package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.AiConfig
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import java.io.File

data class CopilotExecutionResult(
    val raw: String,
    val exitCode: Int,
    val elapsedSec: Long,
)

interface CopilotSubprocessExecutor {
    fun run(
        command: List<String>,
        token: String,
        useStdin: Boolean,
        repoDir: File?,
        prompt: String,
        onPartialOutput: ((String) -> Unit)?,
        timeoutSeconds: Long,
        isCancelled: (() -> Boolean)? = null,
    ): CopilotExecutionResult
}

class DefaultCopilotSubprocessExecutor(
    private val processRunner: CopilotProcessRunner = CopilotProcessRunner(),
) : CopilotSubprocessExecutor {
    override fun run(
        command: List<String>,
        token: String,
        useStdin: Boolean,
        repoDir: File?,
        prompt: String,
        onPartialOutput: ((String) -> Unit)?,
        timeoutSeconds: Long,
        isCancelled: (() -> Boolean)?,
    ): CopilotExecutionResult {
        val process = processRunner.buildProcess(command, token, useStdin, repoDir).start()
        val writerCancel = if (useStdin) processRunner.startStdinWriter(process, prompt) else null
        val reader = processRunner.startStdoutReader(process, onPartialOutput, 1_000L)
        val startMs = System.currentTimeMillis()
        val completed =
            processRunner.waitForOrCancel(
                process,
                reader.thread,
                timeoutSeconds,
                prompt.length,
                writerCancel,
            )
        if (!completed) {
            process.destroyForcibly()
            throw StackLensFailureException(
                StackLensFailure.CopilotTimeout(
                    timeoutSeconds = timeoutSeconds,
                    promptChars = prompt.length,
                ),
            )
        }
        if (isCancelled?.invoke() == true) {
            process.destroyForcibly()
            throw InterruptedException("copilot cli subprocess cancelled")
        }
        val raw =
            reader.buffer
                .toString()
                .trim()
                .let { CopilotOutputSanitizer.stripSystemNotifications(it) }
        val exitCode = processRunner.safeExitValue(process)
        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000
        return CopilotExecutionResult(raw, exitCode, elapsedSec)
    }
}

class
CopilotCliAdapter(
    private val configuredBinary: String,
    private val legacyCliBin: String,
    private val timeoutSeconds: Long,
    private val defaultModel: String,
    private val aiConfigRepo: AiConfigPort,
    private val modelAliases: Map<String, String> = DEFAULT_MODEL_ALIASES,
    private val detector: CopilotBinaryDetector =
        CopilotBinaryDetector(configuredBinary, legacyCliBin),
    private val subprocessExecutor: CopilotSubprocessExecutor = DefaultCopilotSubprocessExecutor(),
    internal var envReader: (String) -> String? = { name -> System.getenv(name) },
) : CopilotStreamingPort {
    private val log = LoggerFactory.getLogger(CopilotCliAdapter::class.java)

    @Volatile
    internal var copilotCmd: List<String>? = null

    override val providerLabel: String = "Copilot"

    override val supportsAttachments: Boolean = false

    override val writesFilesNatively: Boolean = true

    private fun currentAiConfig(): AiConfig? = aiConfigRepo.findAiConfig()

    @PostConstruct
    fun initialize() {
        Thread {
            val found = detector.detect()
            copilotCmd = found
            if (found != null) {
                log.info("Copilot CLI bereit: {}", found)
            } else {
                log.info("Copilot CLI binary not found – will be installed on first use.")
            }
        }.also {
            it.isDaemon = true
            it.name = "copilot-init"
        }.start()
    }

    override fun resolveToken(): String? {
        currentAiConfig()
            ?.githubToken
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return listOf("COPILOT_GITHUB_TOKEN", "GH_TOKEN", "GITHUB_TOKEN")
            .mapNotNull { name -> envReader(name)?.takeIf { it.isNotBlank() } }
            .firstOrNull()
    }

    internal fun requireToken(): String = resolveToken() ?: throw StackLensFailureException(StackLensFailure.CopilotTokenMissing)

    override fun resolveModel(): String {
        currentAiConfig()
            ?.model
            ?.takeIf { it.isNotBlank() }
            ?.let { return modelAliases.getOrDefault(it, it) }
        return defaultModel.takeIf { it.isNotBlank() } ?: "gpt-5.4-mini"
    }

    override fun resolveLanguage(): String =
        currentAiConfig()
            ?.language
            ?.takeIf { it.isNotBlank() } ?: "de"

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
    ): String {
        if (attachments.isNotEmpty()) {
            log.warn(
                "Copilot CLI adapter received {} attachment(s); the Copilot CLI subprocess does not accept images via stdin. Falling back to text-only request.",
                attachments.size,
            )
        }
        val token = requireToken()
        return executeInternal(token, model, prompt, timeoutOverride ?: timeoutSeconds, null, null)
    }

    internal fun executeInternal(
        token: String,
        model: String,
        prompt: String,
        timeoutOverride: Long,
        repoDir: File?,
        onPartialOutput: ((String) -> Unit)?,
        isCancelled: (() -> Boolean)? = null,
    ): String {
        val cmd = copilotCmd ?: detector.install(token).also { copilotCmd = it }
        val useStdin = CopilotProcessRunner.shouldUseStdin(prompt.length)
        val command = processRunner.buildCommand(cmd, model, prompt, useStdin, repoDir)

        log.info(
            "Starting Copilot CLI {} | Model: {} | Prompt: {} chars | Timeout: {}s | Stdin: {}",
            if (repoDir != null) {
                "(repo-write: ${repoDir.name})"
            } else if (onPartialOutput != null) {
                "(streaming)"
            } else {
                ""
            },
            model,
            prompt.length,
            timeoutOverride,
            useStdin,
        )

        val result = subprocessExecutor.run(command, token, useStdin, repoDir, prompt, onPartialOutput, timeoutOverride, isCancelled)
        checkOutput(result.raw, result.exitCode, result.elapsedSec)
        return result.raw
    }

    internal fun checkOutput(
        raw: String,
        exitCode: Int,
        elapsedSec: Long,
    ) {
        if (CopilotOutputSanitizer.isNotInstalledError(raw)) {
            log.error("Copilot CLI shim reports: binary not installed ({} seconds)", elapsedSec)
            throw StackLensFailureException(StackLensFailure.CopilotNotInstalled)
        }
        if (exitCode != 0) {
            log.warn("Copilot CLI exit code {} ({} seconds) | output: {} chars", exitCode, elapsedSec, raw.length)
            if (CopilotOutputSanitizer.isQuotaError(raw)) throw StackLensFailureException(StackLensFailure.CopilotQuota(raw))
            if (raw.isBlank()) throw StackLensFailureException(StackLensFailure.CopilotNoOutput(exitCode))
            if (exitCode > 1) {
                throw StackLensFailureException(
                    StackLensFailure.CopilotExitError(
                        exitCode = exitCode,
                        detail = CopilotOutputSanitizer.scrubSensitiveData(raw.take(400)),
                    ),
                )
            }
            log.warn("Copilot CLI exit 1 with output – content will be used ({} seconds)", elapsedSec)
        }
        if (raw.isBlank()) {
            log.error("Copilot CLI returned empty response (exit 0, {} seconds)", elapsedSec)
            throw StackLensFailureException(StackLensFailure.CopilotEmptyResponse)
        }
        log.info("Copilot CLI completed ({} seconds) | Response: {} chars", elapsedSec, raw.length)
    }

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        if (attachments.isNotEmpty()) {
            log.warn(
                "Copilot CLI adapter received {} attachment(s); the Copilot CLI subprocess does not accept images via stdin. Falling back to text-only request.",
                attachments.size,
            )
        }
        val token = requireToken()
        return executeInternal(token, model, prompt, timeoutOverride ?: timeoutSeconds, null, onPartialOutput, isCancelled)
    }

    override fun executeWithStreamingPerLine(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        repoDir: File?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        if (attachments.isNotEmpty()) {
            log.warn(
                "Copilot CLI adapter received {} attachment(s); the Copilot CLI subprocess does not accept images via stdin. Falling back to text-only request.",
                attachments.size,
            )
        }
        val token = requireToken()
        return executeInternal(token, model, prompt, timeoutOverride ?: timeoutSeconds, repoDir, onPartialOutput, isCancelled)
    }

    companion object {
        val DEFAULT_MODEL_ALIASES: Map<String, String> =
            mapOf(
                "gpt-4o-mini" to "gpt-5.4-mini",
                "gpt-4o" to "gpt-5.4-mini",
            )

        internal val processRunner: CopilotProcessRunner = CopilotProcessRunner()
    }
}
