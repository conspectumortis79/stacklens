package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import org.slf4j.LoggerFactory
import java.io.File

class DockerTestRunner(
    private val processPort: ProcessRunPort,
) {
    private val log = LoggerFactory.getLogger(DockerTestRunner::class.java)

    data class UpResult(
        val success: Boolean,
        val composeDir: File,
    )

    data class TestOutput(
        val command: String,
        val result: ProcessResult,
        val passed: Boolean,
    )

    fun startServices(
        composeDir: File,
        services: List<String>,
        onLine: (String) -> Unit = {},
        timeoutSec: Long = 360L,
    ): UpResult {
        val cmd = listOf("docker-compose", "up", "-d", "--build") + services
        return try {
            val exitCode =
                processPort.runStreaming(
                    cmd = cmd,
                    workDir = composeDir,
                    timeoutSec = timeoutSec,
                    onLine = onLine,
                )
            UpResult(exitCode == 0, composeDir)
        } catch (e: Exception) {
            log.warn("docker-compose up failed: {}", e.message)
            UpResult(false, composeDir)
        }
    }

    fun runTests(
        composeDir: File,
        commands: List<String>,
        onLine: (String) -> Unit = {},
        onTestStart: (index: Int, command: String) -> Unit = { _, _ -> },
        onTestEnd: (index: Int, command: String, exitCode: Int) -> Unit = { _, _, _ -> },
    ): List<TestOutput> {
        val outputs = mutableListOf<TestOutput>()
        for ((idx, cmd) in commands.withIndex()) {
            val trimmed = cmd.trim()
            if (trimmed.isEmpty()) continue
            onTestStart(idx, cmd)
            val stdout = StringBuilder()
            val exitCode =
                try {
                    processPort.runStreaming(
                        cmd = listOf("sh", "-c", trimmed),
                        workDir = composeDir,
                        timeoutSec = 120L,
                        onLine = { line ->
                            stdout.appendLine(line)
                            onLine(line)
                        },
                    )
                } catch (e: Exception) {
                    log.warn("Test command '{}' failed: {}", trimmed.take(120), e.message)
                    -1
                }
            onTestEnd(idx, cmd, exitCode)
            outputs.add(TestOutput(cmd, ProcessResult(exitCode, stdout.toString(), ""), exitCode == 0))
        }
        return outputs
    }

    fun stopServices(composeDir: File): Boolean =
        try {
            val r =
                processPort.run(
                    listOf("docker-compose", "down", "--volumes", "--remove-orphans"),
                    timeoutSec = 90L,
                    workDir = composeDir,
                )
            r.success
        } catch (e: Exception) {
            log.warn("docker-compose down failed: {}", e.message)
            false
        }
}
