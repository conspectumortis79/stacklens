package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.process.ManagedProcess
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.domain.util.quietly
import com.stacklens.shared.ProcessUtils
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Component
class ProcessAdapter : ProcessPort {
    override fun resolveGitBinary(): String = ProcessUtils.resolveGitBinary()

    override fun resolveMakeBinary(): String = ProcessUtils.resolveMakeBinary()

    override fun killProcessTree(process: ManagedProcess) = process.killTree()

    override fun run(
        cmd: List<String>,
        timeoutSec: Long,
        workDir: File?,
    ): ProcessResult {
        val raw = ProcessExecutor.run(cmd, timeoutSec = timeoutSec, workDir = workDir)
        return ProcessResult(raw.exitCode, raw.stdout, raw.stderr)
    }

    override fun launch(cmd: List<String>): ManagedProcess = JvmManagedProcess(ProcessBuilder(cmd).start())

    override fun runStreaming(
        cmd: List<String>,
        workDir: File?,
        timeoutSec: Long,
        onLine: (String) -> Unit,
    ): Int =
        runStreamingInternal(
            cmd = cmd,
            workDir = workDir,
            timeoutSec = timeoutSec,
            onLine = onLine,
            processBuilderFactory = { command, wd -> buildProcessBuilder(command, wd) },
        )

    internal fun buildProcessBuilder(
        cmd: List<String>,
        workDir: File?,
    ): ProcessBuilder {
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        if (workDir != null) pb.directory(workDir)
        return pb
    }

    internal fun runStreamingInternal(
        cmd: List<String>,
        workDir: File?,
        timeoutSec: Long,
        onLine: (String) -> Unit,
        processBuilderFactory: (List<String>, File?) -> ProcessBuilder,
    ): Int {
        val pb =
            try {
                processBuilderFactory(cmd, workDir)
            } catch (e: Exception) {
                throw ProcessExecutionException("Failed to start process: ${e.message}", e)
            }
        val proc =
            try {
                pb.start()
            } catch (e: Exception) {
                throw ProcessExecutionException("Failed to start process: ${e.message}", e)
            }
        try {
            val lineQueue = LinkedBlockingQueue<Any>()
            val endOfStream = Any()
            val readerThread =
                Thread({
                    try {
                        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                            var line = reader.readLine()
                            while (line != null) {
                                lineQueue.put(line)
                                line = reader.readLine()
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        lineQueue.put(endOfStream)
                    }
                }, "runStreaming-reader")
            readerThread.isDaemon = true
            readerThread.start()

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
            try {
                while (true) {
                    val item = lineQueue.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
                    if (item == null) {
                        throw timeoutAfter(proc, timeoutSec, cmd)
                    }
                    if (item === endOfStream) {
                        if (!proc.waitFor(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
                            throw timeoutAfter(proc, timeoutSec, cmd)
                        }
                        return proc.exitValue()
                    }
                    onLine(item as String)
                }
            } finally {
                quietly { proc.inputStream.close() }
                readerThread.join(500)
            }
        } catch (e: InterruptedException) {
            ProcessUtils.killProcessTree(proc)
            Thread.currentThread().interrupt()
            throw e
        } catch (e: ProcessExecutionException) {
            throw e
        } catch (e: Exception) {
            ProcessUtils.killProcessTree(proc)
            throw ProcessExecutionException("Process failed: ${e.message}", e)
        }
    }

    private fun timeoutAfter(
        proc: Process,
        timeoutSec: Long,
        cmd: List<String>,
    ): ProcessExecutionException {
        ProcessUtils.killProcessTree(proc)
        return ProcessExecutionException("Process timed out after ${timeoutSec}s: ${cmd.joinToString(" ")}")
    }

    override fun makefileHasTarget(
        repoPath: String,
        target: String,
    ): Boolean = MakefileRunner.makefileHasTarget(repoPath, target)

    override fun findMakeTarget(
        repoPath: String,
        vararg candidates: String,
    ): String? = MakefileRunner.findMakeTarget(repoPath, *candidates)

    override fun runMakeTarget(
        repoPath: String,
        target: String,
        timeoutSecs: Long,
    ): Pair<Int, String> = MakefileRunner.runMakeTarget(repoPath, target, timeoutSecs)
}

class ProcessExecutionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
