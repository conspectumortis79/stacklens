package com.stacklens.adapter.out.ai

import com.stacklens.testsupport.FakeSleeper
import com.stacklens.testsupport.FlakyInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class CopilotBinaryDetectorProcessShapeCoverageTest {
    private fun finishedProcess(stdout: ByteArray): Process {
        val process = mock<Process>()
        whenever(process.waitFor(any<Long>(), any<TimeUnit>())).thenReturn(true)
        whenever(process.inputStream).thenReturn(ByteArrayInputStream(stdout))
        whenever(process.exitValue()).thenReturn(0)
        return process
    }

    @Test
    fun `a helper whose output stream breaks is reported as a failed run`() {
        val broken = mock<Process>()
        whenever(broken.waitFor(any<Long>(), any<TimeUnit>())).thenReturn(true)
        whenever(broken.inputStream).thenReturn(FlakyInputStream(failOnRead = true))

        val runner = CopilotBinaryDetector.DefaultProcessRunner(processStarter = { broken })

        assertThrows(IOException::class.java) { runner.run(listOf("copilot", "--version"), emptyMap()) }
    }

    @Test
    fun `a helper that cannot report an exit code counts as timed out`() {
        val unresponsive = finishedProcess("copilot 1.0".toByteArray())
        whenever(unresponsive.exitValue()).thenThrow(IllegalMonitorStateException("not terminated"))

        val runner = CopilotBinaryDetector.DefaultProcessRunner(processStarter = { unresponsive })

        val result = runner.run(listOf("copilot", "--version"), mapOf("PATH" to "/usr/bin"))

        assertEquals(124, result.exitCode)
        assertEquals("copilot 1.0", result.stdout)
    }

    @Test
    fun `a successful install attempt without a visible binary keeps searching`() {
        val sleeper = FakeSleeper()
        var detectProbes = 0
        val detector =
            CopilotBinaryDetector(
                configuredBinary = "",
                legacyCliBin = "",
                fileSystem = EmptyFileSystem,
                processRunner =
                    CopilotBinaryDetector.ProcessRunner { command, _ ->
                        when {
                            command.last().contains("command -v gh") -> CopilotBinaryDetector.ProcessResult(0, "/usr/bin/gh")
                            command.last().contains("gh copilot") -> CopilotBinaryDetector.ProcessResult(0, "copilot 1.2 installed")
                            else -> {
                                detectProbes++
                                CopilotBinaryDetector.ProcessResult(1, "")
                            }
                        }
                    },
                installBackoffMs = { 0L },
                sleeper = sleeper,
            )

        val failure =
            assertThrows(IllegalStateException::class.java) { detector.install("token") }

        assertEquals("[COPILOT_INSTALL_FAILED]", failure.message)
        assertEquals(4, detectProbes)
        assertEquals(2, sleeper.requests.size)
    }

    private object EmptyFileSystem : CopilotBinaryDetector.FileSystem {
        override fun canExecute(path: String): Boolean = false

        override fun isDirectory(path: String): Boolean = false

        override fun isFile(path: String): Boolean = false

        override fun walkTopDown(root: String): Sequence<String> = emptySequence()

        override fun userHome(): String = "/home/test"
    }
}
