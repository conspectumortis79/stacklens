package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.shared.ThreadSleepSleeper
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class CopilotBinaryDetector(
    private val configuredBinary: String,
    private val legacyCliBin: String,
    private val fileSystem: FileSystem = DefaultFileSystem,
    private val processRunner: ProcessRunner = DefaultProcessRunner.Default,
    private val installBackoffMs: (attempt: Int) -> Long = { a -> 1000L * (1 shl a) },
    private val sleeper: SleeperPort = ThreadSleepSleeper(),
) {
    private val log = LoggerFactory.getLogger(CopilotBinaryDetector::class.java)

    interface FileSystem {
        fun canExecute(path: String): Boolean

        fun isDirectory(path: String): Boolean

        fun isFile(path: String): Boolean

        fun walkTopDown(root: String): Sequence<String>

        fun userHome(): String
    }

    object DefaultFileSystem : FileSystem {
        override fun canExecute(path: String): Boolean = File(path).canExecute()

        override fun isDirectory(path: String): Boolean = File(path).isDirectory

        override fun isFile(path: String): Boolean = File(path).isFile

        override fun walkTopDown(root: String): Sequence<String> = File(root).walkTopDown().map { it.absolutePath }

        override fun userHome(): String = System.getProperty("user.home")
    }

    fun interface ProcessRunner {
        fun run(
            command: List<String>,
            env: Map<String, String>,
        ): ProcessResult
    }

    data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
    )

    open class DefaultProcessRunner(
        private val timeoutSeconds: Long = TIMEOUT_SECONDS,
        private val destroyGraceSeconds: Long = DESTROY_GRACE_SECONDS,
        private val processStarter: (ProcessBuilder) -> Process = { it.start() },
    ) : ProcessRunner {
        override fun run(
            command: List<String>,
            env: Map<String, String>,
        ): ProcessResult {
            val pb = ProcessBuilder(command).redirectErrorStream(true)
            env.forEach { (k, v) -> pb.environment()[k] = v }
            val process = processStarter(pb)
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            return if (completed) {
                val stdout =
                    process.inputStream
                        .utf8Reader()
                        .use { it.readText() }
                        .trim()
                ProcessResult(exitCode = safeExitValue(process), stdout = stdout)
            } else {
                process.destroyForcibly()
                process.waitFor(destroyGraceSeconds, TimeUnit.SECONDS)
                ProcessResult(exitCode = TIMEOUT_EXIT_CODE, stdout = "")
            }
        }

        private fun safeExitValue(process: Process): Int = runCatching { process.exitValue() }.getOrElse { TIMEOUT_EXIT_CODE }

        companion object {
            const val TIMEOUT_SECONDS: Long = 5L
            const val TIMEOUT_EXIT_CODE: Int = 124
            const val DESTROY_GRACE_SECONDS: Long = 2L

            @JvmField
            val Default: DefaultProcessRunner = DefaultProcessRunner()
        }
    }

    fun detect(): List<String>? {
        val explicit =
            configuredBinary.takeIf { it.isNotBlank() }
                ?: legacyCliBin.takeIf { it.isNotBlank() }
        if (explicit != null) return listOf(explicit.trim())

        knownPaths().firstOrNull { fileSystem.canExecute(it) }?.let { return listOf(it) }

        which("copilot")?.let { return listOf(it) }

        searchGhDirs()?.let { return listOf(it) }

        return null
    }

    fun install(token: String): List<String> {
        val gh =
            which("gh")
                ?: findGhBinary()
                ?: throw StackLensFailureException(StackLensFailure.GhCliMissing)

        log.info("Installiere Copilot CLI via gh copilot (GH_TOKEN=PAT)...")
        var lastOut = ""
        var lastExit = -1
        repeat(3) { attempt ->
            try {
                val res =
                    processRunner.run(
                        listOf("sh", "-c", "printf 'y\\n' | gh copilot -- --version 2>&1"),
                        mapOf("GH_TOKEN" to token),
                    )
                lastOut = res.stdout.trim()
                lastExit = res.exitCode
                log.info(
                    "gh copilot install attempt {} (exit={}): {}",
                    attempt + 1,
                    lastExit,
                    CopilotOutputSanitizer.scrubSensitiveData(lastOut.take(200)),
                )
                if (lastExit == 0 && detect() != null) return@repeat
            } catch (e: Exception) {
                log.warn("Copilot CLI installation attempt {} failed: {}", attempt + 1, e.message)
            }
            if (attempt < 2) {
                sleeper.sleep(installBackoffMs(attempt))
            }
        }

        val found =
            detect()
                ?: throw StackLensFailureException(StackLensFailure.CopilotInstallFailed)

        log.info("Copilot CLI after installation: {}", found)
        return found
    }

    private fun knownPaths(): List<String> {
        val home = fileSystem.userHome()
        return listOf(
            "/opt/homebrew/bin/copilot",
            "/usr/local/bin/copilot",
            "/home/linuxbrew/.linuxbrew/bin/copilot",
            "$home/.local/bin/copilot",
            "$home/.local/share/gh/copilot/copilot",
            "$home/Library/Application Support/gh/copilot/copilot",
            "$home/Library/Application Support/Code/User/globalStorage/github.copilot-chat/copilotCli/copilot",
            "$home/Library/Application Support/Code - Insiders/User/globalStorage/github.copilot-chat/copilotCli/copilot",
            "$home/.config/Code/User/globalStorage/github.copilot-chat/copilotCli/copilot",
            "$home/.config/Code - Insiders/User/globalStorage/github.copilot-chat/copilotCli/copilot",
            "$home/.var/app/com.visualstudio.code/config/Code/User/globalStorage/github.copilot-chat/copilotCli/copilot",
            "$home/.var/app/com.visualstudio.code-insiders/config/Code - Insiders/User/globalStorage/github.copilot-chat/copilotCli/copilot",
        )
    }

    private fun isCopilotExecutable(path: String): Boolean {
        val file = File(path)
        return file.name == "copilot" && fileSystem.canExecute(path) && fileSystem.isFile(path)
    }

    private fun searchGhDirs(): String? {
        val home = fileSystem.userHome()
        return listOf(
            "$home/.local/share/gh",
            "$home/Library/Application Support/gh",
        ).filter { fileSystem.isDirectory(it) }
            .flatMap { dir -> fileSystem.walkTopDown(dir) }
            .firstOrNull { isCopilotExecutable(it) }
    }

    internal fun findGhBinary(): String? =
        listOf(
            "/opt/homebrew/bin/gh",
            "/usr/local/bin/gh",
            "/usr/bin/gh",
            "/home/linuxbrew/.linuxbrew/bin/gh",
        ).firstOrNull { fileSystem.canExecute(it) }

    internal fun which(cmd: String): String? {
        val result =
            try {
                processRunner.run(
                    listOf("/bin/sh", "-lc", "command -v ${cmd.replace("'", "'\\''")}"),
                    emptyMap(),
                )
            } catch (_: Exception) {
                return null
            }
        val out = result.stdout.trim()
        return out.takeIf { it.isNotBlank() && result.exitCode == 0 }
    }
}
