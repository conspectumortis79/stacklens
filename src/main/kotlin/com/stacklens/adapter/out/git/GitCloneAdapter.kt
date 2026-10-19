package com.stacklens.adapter.out.git

import com.stacklens.application.port.out.repo.GitClonePort
import com.stacklens.domain.util.closingAuto
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

interface GitCloneProcessRunner {
    fun runClone(
        url: String,
        target: String,
        workingDir: File,
        timeoutMinutes: Long,
    ): GitCloneProcessResult

    fun waitFor(
        process: Process,
        timeoutMinutes: Long,
    ): Boolean
}

data class GitCloneProcessResult(
    val output: String,
    val exitCode: Int,
    val finished: Boolean,
)

interface GitCloneStrategy {
    fun startCloneProcess(
        url: String,
        target: String,
        workingDir: File,
    ): Process

    fun readProcessOutput(process: Process): String

    fun waitFor(
        process: Process,
        timeoutMinutes: Long,
    ): Boolean

    fun safeExitValue(process: Process): Int

    fun destroyForcibly(process: Process)
}

internal class RealGitCloneStrategy : GitCloneStrategy {
    override fun startCloneProcess(
        url: String,
        target: String,
        workingDir: File,
    ): Process =
        ProcessBuilder("git", "clone", url, target)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

    override fun readProcessOutput(process: Process): String = process.inputStream.utf8Reader().closingAuto { it.readText() }

    override fun waitFor(
        process: Process,
        timeoutMinutes: Long,
    ): Boolean = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

    override fun safeExitValue(process: Process): Int = runCatching { process.exitValue() }.getOrElse { -1 }

    override fun destroyForcibly(process: Process) {
        process.destroyForcibly()
    }
}

class DefaultGitCloneProcessRunner(
    private val strategy: GitCloneStrategy = RealGitCloneStrategy(),
) : GitCloneProcessRunner {
    override fun runClone(
        url: String,
        target: String,
        workingDir: File,
        timeoutMinutes: Long,
    ): GitCloneProcessResult {
        val process = strategy.startCloneProcess(url, target, workingDir)
        val output = strategy.readProcessOutput(process)
        val finished = strategy.waitFor(process, timeoutMinutes)
        val exitCode = if (finished) strategy.safeExitValue(process) else -1
        if (!finished) strategy.destroyForcibly(process)
        return GitCloneProcessResult(output, exitCode, finished)
    }

    override fun waitFor(
        process: Process,
        timeoutMinutes: Long,
    ): Boolean = strategy.waitFor(process, timeoutMinutes)
}

class GitCloneAdapter(
    private val processRunner: GitCloneProcessRunner = DefaultGitCloneProcessRunner(),
) : GitClonePort {
    private val log = LoggerFactory.getLogger(GitCloneAdapter::class.java)

    override fun clone(
        authenticatedUrl: String,
        parentDir: File,
        safeName: String,
        timeoutMinutes: Long,
    ): String {
        val cloneTarget = File(parentDir.canonicalFile, safeName)
        if (cloneTarget.exists()) {
            throw RuntimeException("Directory already exists: ${cloneTarget.canonicalPath}")
        }
        log.info("[GitClone] Cloning into {}", cloneTarget.absolutePath)
        val result =
            processRunner.runClone(
                url = authenticatedUrl,
                target = cloneTarget.absolutePath,
                workingDir = parentDir.canonicalFile,
                timeoutMinutes = timeoutMinutes,
            )

        if (result.exitCode != 0) {
            cloneTarget.deleteRecursively()
            val safeOutput = result.output.replace(Regex("https://[^@]+@"), "https://***@").take(500)
            val reason =
                if (!result.finished) {
                    "Timed out after $timeoutMinutes minutes"
                } else {
                    "git exited with code ${result.exitCode}: $safeOutput"
                }
            throw RuntimeException(reason)
        }

        return cloneTarget.canonicalPath
    }
}
