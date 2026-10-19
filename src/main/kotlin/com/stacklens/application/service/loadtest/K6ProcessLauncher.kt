package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.file.TempFileWritePort
import com.stacklens.application.port.out.process.ManagedProcess
import com.stacklens.application.port.out.process.ProcessRunPort
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class K6ProcessLauncher(
    private val processRunPort: ProcessRunPort,
    private val fileSystem: TempFileWritePort,
    private val runRegistry: K6RunRegistry,
) {
    private val log = LoggerFactory.getLogger(K6ProcessLauncher::class.java)

    private companion object {
        const val MAX_STDOUT = 500_000
        const val MAX_STDERR = 100_000
        const val K6_HARD_TIMEOUT_MINUTES = 15L
    }

    fun launch(
        k6Bin: String,
        script: String,
        bearerToken: String?,
        analysisId: Long,
    ): K6Run {
        val tmpFile = fileSystem.createTempFile("stacklens-k6-", ".js")
        fileSystem.writeFile(tmpFile, script)
        val summaryFile = fileSystem.createTempFile("stacklens-k6-summary-", ".json")

        val k6Args =
            mutableListOf(
                k6Bin,
                "run",
                "--no-color",
                "--summary-export",
                summaryFile.absolutePath,
            )
        if (!bearerToken.isNullOrBlank()) {
            k6Args += listOf("--env", "JWT_TOKEN=$bearerToken")
        }
        k6Args += tmpFile.absolutePath

        val process = processRunPort.launch(k6Args)
        val stdoutBuf = StringBuilder()
        val stderrBuf = StringBuilder()
        val scriptHeader = buildScriptHeader(script, tmpFile)
        val run = K6Run(process, stdoutBuf, stderrBuf, summaryFile, scriptHeader)
        runRegistry.register(analysisId, run)

        startStdoutDrainer(process, stdoutBuf)
        startStderrDrainer(process, stderrBuf)
        enforceHardTimeoutAndCleanupTemp(process, tmpFile, analysisId)
        return run
    }

    private fun buildScriptHeader(
        script: String,
        tmpFile: File,
    ): String =
        buildString {
            appendLine("[StackLens] k6 script metadata")
            appendLine("[StackLens]   tmp file: ${tmpFile.absolutePath}")
            val baseUrlMatch =
                Regex("""(?m)(?:const|let|var)\s+BASE_URL\s*=\s*(?:__ENV\.BASE_URL\s*\|\|\s*)?['"]([^'"]*)['"]""").find(script)
            if (baseUrlMatch != null) {
                appendLine("[StackLens]   effective BASE_URL after patch: ${baseUrlMatch.groupValues[1]}")
            } else {
                appendLine("[StackLens]   no BASE_URL literal found in patched script")
            }
            appendLine("[StackLens]   first 20 lines of patched script:")
            script.lineSequence().take(20).forEach { appendLine("[StackLens]     $it") }
            appendLine("[StackLens] end of script header")
        }

    private fun startStdoutDrainer(
        process: ManagedProcess,
        stdoutBuf: StringBuilder,
    ) {
        CompletableFuture
            .runAsync {
                process.stdout().use { s ->
                    val buf = ByteArray(4096)
                    var n: Int
                    while (s.read(buf).also { n = it } != -1) {
                        val chunk = String(buf, 0, n)
                        synchronized(stdoutBuf) {
                            stdoutBuf.append(chunk)
                            if (stdoutBuf.length > MAX_STDOUT) {
                                stdoutBuf.delete(0, stdoutBuf.length - MAX_STDOUT)
                            }
                        }
                    }
                }
            }.exceptionally { e ->
                log.warn("[k6] stdout drain failed: {}", e.message)
                null
            }
    }

    private fun startStderrDrainer(
        process: ManagedProcess,
        stderrBuf: StringBuilder,
    ) {
        CompletableFuture
            .runAsync {
                process.stderr().use { s ->
                    val buf = ByteArray(4096)
                    var n: Int
                    while (s.read(buf).also { n = it } != -1) {
                        val chunk = String(buf, 0, n)
                        synchronized(stderrBuf) {
                            stderrBuf.append(chunk)
                            if (stderrBuf.length > MAX_STDERR) {
                                stderrBuf.delete(0, stderrBuf.length - MAX_STDERR)
                            }
                        }
                    }
                }
            }.exceptionally { e ->
                log.warn("[k6] stderr drain failed: {}", e.message)
                null
            }
    }

    private fun enforceHardTimeoutAndCleanupTemp(
        process: ManagedProcess,
        tmpFile: File,
        analysisId: Long,
    ) {
        CompletableFuture
            .runAsync {
                val finished = process.waitFor(K6_HARD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                if (!finished && process.isAlive) {
                    log.warn(
                        "[k6] Process for analysisId={} exceeded the {} min hard timeout — destroying the process tree.",
                        analysisId,
                        K6_HARD_TIMEOUT_MINUTES,
                    )
                    runCatching { processRunPort.killProcessTree(process) }
                }
                fileSystem.deleteFile(tmpFile)
            }.exceptionally { e ->
                log.warn("[k6] temp cleanup failed for analysisId={}: {}", analysisId, e.message)
                null
            }
    }
}
