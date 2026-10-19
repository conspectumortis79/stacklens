package com.stacklens.adapter.out.shared

import com.stacklens.domain.util.closingAuto
import com.stacklens.domain.util.exceptionDiagnostic
import com.stacklens.domain.util.quietly
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

object ProcessExecutor {
    private val log = LoggerFactory.getLogger(ProcessExecutor::class.java)

    private const val EXTRA_PATH = "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val success: Boolean get() = exitCode == 0
    }

    fun run(
        cmd: List<String>,
        timeoutSec: Long,
        augmentPath: Boolean = true,
        workDir: java.io.File? = null,
    ): Result {
        return try {
            val pb = ProcessBuilder(cmd).redirectErrorStream(false)
            if (workDir != null) pb.directory(workDir)
            if (augmentPath) {
                val env = pb.environment()
                env["PATH"] = buildPath(env["PATH"])
            }
            val proc = pb.start()
            quietly { proc.outputStream.close() }

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outT = drain(proc.inputStream, stdout, "proc-stdout")
            val errT = drain(proc.errorStream, stderr, "proc-stderr")

            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                quietly { outT.join(1_000) }
                quietly { errT.join(1_000) }
                return Result(124, stdout.toString(), "Timeout nach ${timeoutSec}s\n$stderr")
            }
            outT.join(5_000)
            errT.join(5_000)
            Result(proc.exitValue(), stdout.toString(), stderr.toString())
        } catch (e: Exception) {
            log.warn("Process failed: {} — {}: {}", cmd, e.javaClass.simpleName, e.message)
            Result(-1, "", exceptionDiagnostic(e))
        }
    }

    internal fun drain(
        stream: java.io.InputStream,
        sink: StringBuilder,
        name: String,
    ): Thread {
        val t =
            Thread {
                stream.utf8Reader().closingAuto { r ->
                    try {
                        var line = r.readLine()
                        while (line != null) {
                            synchronized(sink) { sink.appendLine(line) }
                            line = r.readLine()
                        }
                    } catch (e: Exception) {
                        log.debug("{} drain ended with {}: {}", name, e.javaClass.simpleName, e.message)
                    }
                }
            }
        t.isDaemon = true
        t.name = name
        t.start()
        return t
    }

    internal fun buildPath(existingPath: String?): String = if (existingPath != null) "$existingPath:$EXTRA_PATH" else EXTRA_PATH
}
