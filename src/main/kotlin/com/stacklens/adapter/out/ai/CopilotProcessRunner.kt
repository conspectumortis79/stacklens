package com.stacklens.adapter.out.ai

import com.stacklens.domain.util.quietly
import com.stacklens.shared.utf8Reader
import com.stacklens.shared.utf8Writer
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CopilotProcessRunner {
    private val log = LoggerFactory.getLogger(CopilotProcessRunner::class.java)

    class OutputReader(
        val buffer: StringBuilder,
        val thread: Thread,
    )

    companion object {
        private const val INLINE_PROMPT_LIMIT_BYTES = 8_192

        fun shouldUseStdin(promptLength: Int) = promptLength > INLINE_PROMPT_LIMIT_BYTES
    }

    fun startStdinWriter(
        process: Process,
        content: String,
    ): AtomicBoolean {
        val cancel = AtomicBoolean(false)
        Thread {
            try {
                process.outputStream.utf8Writer().use { writer ->
                    val chunk = 8 * 1024
                    var i = 0
                    while (i < content.length && !cancel.get()) {
                        val end = (i + chunk).coerceAtMost(content.length)
                        writer.write(content, i, end - i)
                        i = end
                    }
                    if (!cancel.get()) writer.flush()
                }
            } catch (_: Exception) {
            }
        }.also { t ->
            t.isDaemon = true
            t.name = "copilot-stdin"
        }.start()
        return cancel
    }

    fun startStdoutReader(
        process: Process,
        onPartialOutput: ((String) -> Unit)? = null,
        flushIntervalMs: Long = 1_000L,
    ): OutputReader {
        val sb = StringBuilder()
        val lastFlush =
            java.util.concurrent.atomic
                .AtomicLong(System.currentTimeMillis())
        val pushPartial: (() -> Unit)? =
            onPartialOutput?.let { callback ->
                {
                    val now = System.currentTimeMillis()
                    if (now - lastFlush.get() >= flushIntervalMs) {
                        runCatching { callback(CopilotOutputSanitizer.stripSystemNotifications(sb.toString())) }
                        lastFlush.set(now)
                    }
                }
            }
        val thread =
            Thread {
                try {
                    process.inputStream.utf8Reader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            sb.appendLine(line)
                            pushPartial?.invoke()
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Copilot CLI stream read failed: {}: {}", e.javaClass.simpleName, e.message)
                }
            }
        thread.isDaemon = true
        thread.name = "copilot-stdout"
        thread.start()
        return OutputReader(sb, thread)
    }

    fun waitForOrCancel(
        process: Process,
        readThread: Thread,
        timeoutSeconds: Long,
        promptLength: Int,
        writerCancel: AtomicBoolean? = null,
    ): Boolean =
        try {
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                log.info("Copilot CLI did not finish within {} s — destroyForcibly | Prompt: {} chars", timeoutSeconds, promptLength)
                runCatching { process.destroyForcibly() }
                quietly { readThread.join(1_000) }
            } else {
                readThread.join(5_000)
            }
            writerCancel?.set(true)
            completed
        } catch (e: InterruptedException) {
            log.info("Copilot CLI aborted (Thread interrupted) — destroyForcibly | Prompt: {} chars", promptLength)
            writerCancel?.set(true)
            runCatching { process.destroyForcibly() }
            quietly { readThread.join(1_000) }
            Thread.currentThread().interrupt()
            throw e
        }

    fun safeExitValue(process: Process): Int =
        runCatching { process.exitValue() }.getOrElse {
            log.warn("Copilot CLI process not yet fully terminated — exitValue() threw {}: {}", it.javaClass.simpleName, it.message)
            124
        }

    fun buildProcess(
        cmd: List<String>,
        token: String,
        useStdin: Boolean,
        repoDir: File? = null,
    ): ProcessBuilder =
        ProcessBuilder(cmd)
            .apply {
                environment()["COPILOT_GITHUB_TOKEN"] = token
                directory(repoDir ?: File(System.getProperty("java.io.tmpdir")))
            }.redirectErrorStream(true)
            .apply { if (!useStdin) redirectInput(File("/dev/null")) }

    fun buildCommand(
        cmd: List<String>,
        model: String,
        prompt: String,
        useStdin: Boolean,
        repoDir: File? = null,
    ): List<String> {
        val extraArgs = mutableListOf<String>()
        extraArgs.addAll(listOf("--model", model, "--disable-builtin-mcps", "--no-custom-instructions", "--no-ask-user"))
        if (repoDir != null) {
            extraArgs.addAll(listOf("--allow-all-tools", "--add-dir", repoDir.absolutePath))
        } else {
            extraArgs.addAll(listOf("--deny-tool=write", "--deny-tool=shell"))
        }
        extraArgs.add("-s")
        return if (useStdin) cmd + extraArgs else cmd + listOf("-p", prompt) + extraArgs
    }
}
