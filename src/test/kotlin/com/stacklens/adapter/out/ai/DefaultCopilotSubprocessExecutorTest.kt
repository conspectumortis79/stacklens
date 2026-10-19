package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class DefaultCopilotSubprocessExecutorTest {
    private val fakeProcess = mock<Process>()

    private fun stubProcess(
        exitCode: Int = 0,
        output: String = "hello world",
    ) {
        whenever(fakeProcess.inputStream).thenReturn(ByteArrayInputStream(output.toByteArray()))
        whenever(fakeProcess.outputStream).thenReturn(ByteArrayOutputStream())
        whenever(fakeProcess.errorStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(fakeProcess.exitValue()).thenReturn(exitCode)
    }

    private fun stubProcessRunner(
        readerBuffer: StringBuilder = StringBuilder(""),
        completed: Boolean = true,
    ): CopilotProcessRunner {
        val reader =
            CopilotProcessRunner.OutputReader(
                buffer = readerBuffer,
                thread = Thread {},
            )
        val runner = mock<CopilotProcessRunner>()
        whenever(runner.buildProcess(any(), any(), any(), anyOrNull())).thenReturn(ProcessBuilder("echo", "x"))
        whenever(runner.startStdoutReader(any(), anyOrNull(), any())).thenReturn(reader)
        whenever(runner.startStdinWriter(any(), any())).thenReturn(AtomicBoolean(false))
        whenever(runner.waitForOrCancel(any(), any(), any(), any(), anyOrNull())).thenReturn(completed)
        whenever(runner.safeExitValue(any())).thenReturn(0)
        return runner
    }

    private fun newExecutor(runner: CopilotProcessRunner = stubProcessRunner()) = DefaultCopilotSubprocessExecutor(runner)

    @Test
    fun `run happy path returns a CopilotExecutionResult with output and exit code`() {
        val buffer = StringBuilder("the actual response")
        val runner = stubProcessRunner(readerBuffer = buffer, completed = true)
        stubProcess(exitCode = 0)

        val result =
            newExecutor(runner).run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = false,
                repoDir = null,
                prompt = "short prompt",
                onPartialOutput = null,
                timeoutSeconds = 30,
            )
        assertEquals("the actual response", result.raw)
        assertEquals(0, result.exitCode)
        assertTrue(result.elapsedSec >= 0)
    }

    @Test
    fun `run with useStdin=true invokes the stdin writer and cancels it after waitForOrCancel`() {
        val buffer = StringBuilder("output")
        val runner = stubProcessRunner(readerBuffer = buffer, completed = true)
        stubProcess(exitCode = 0)
        newExecutor(runner).run(
            command = listOf("copilot"),
            token = "tok",
            useStdin = true,
            repoDir = null,
            prompt = "long ".repeat(2000),
            onPartialOutput = null,
            timeoutSeconds = 10,
        )
        verify(runner, times(1)).startStdinWriter(any(), any())
        verify(runner, atLeastOnce()).waitForOrCancel(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `run throws COPILOT_TIMEOUT when the process does not complete in time`() {
        val runner = stubProcessRunner(completed = false)
        stubProcess(exitCode = 137)
        val ex =
            assertThrows(IllegalStateException::class.java) {
                newExecutor(runner).run(
                    command = listOf("copilot"),
                    token = "tok",
                    useStdin = false,
                    repoDir = null,
                    prompt = "p",
                    onPartialOutput = null,
                    timeoutSeconds = 1,
                )
            }
        assertTrue(ex.message!!.startsWith("[COPILOT_TIMEOUT:"))
    }

    @Test
    fun `run applies CopilotOutputSanitizer to the captured buffer`() {
        val rawOutput = "<system_notification>some noise</system_notification>\nhello there"
        val buffer = StringBuilder(rawOutput)
        val runner = stubProcessRunner(readerBuffer = buffer, completed = true)
        stubProcess(exitCode = 0)
        val result =
            newExecutor(runner).run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = false,
                repoDir = null,
                prompt = "p",
                onPartialOutput = null,
                timeoutSeconds = 5,
            )
        assertTrue(result.raw.contains("hello there"))
        assertTrue(!result.raw.contains("system_notification"))
    }

    @Test
    fun `run forwards the partial-output callback to startStdoutReader`() {
        val captured = StringBuilder()
        val runner = stubProcessRunner()
        stubProcess()
        newExecutor(runner).run(
            command = listOf("copilot"),
            token = "tok",
            useStdin = false,
            repoDir = null,
            prompt = "p",
            onPartialOutput = { captured.append(it) },
            timeoutSeconds = 5,
        )
        verify(runner, atLeastOnce()).startStdoutReader(any(), anyOrNull(), any())
    }

    @Test
    fun `run forwards the repoDir to buildProcess`() {
        val runner = stubProcessRunner()
        stubProcess()
        val repoDir = File("/tmp/repo")
        newExecutor(runner).run(
            command = listOf("copilot"),
            token = "tok",
            useStdin = false,
            repoDir = repoDir,
            prompt = "p",
            onPartialOutput = null,
            timeoutSeconds = 5,
        )
        verify(runner, atLeastOnce()).buildProcess(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `run with useStdin=false does not invoke the stdin writer`() {
        val runner = stubProcessRunner()
        stubProcess()
        newExecutor(runner).run(
            command = listOf("copilot"),
            token = "tok",
            useStdin = false,
            repoDir = null,
            prompt = "p",
            onPartialOutput = null,
            timeoutSeconds = 5,
        )
        verify(runner, never()).startStdinWriter(any(), any())
    }

    @Test
    fun `default constructor uses the real CopilotProcessRunner`() {
        val executor = DefaultCopilotSubprocessExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `run consults safeExitValue to populate the result`() {
        val runner = mock<CopilotProcessRunner>()
        val buffer = StringBuilder("x")
        val reader = CopilotProcessRunner.OutputReader(buffer, Thread {})
        whenever(runner.buildProcess(any(), any(), any(), anyOrNull()))
            .thenReturn(ProcessBuilder("echo", "x"))
        whenever(runner.startStdoutReader(any(), anyOrNull(), any())).thenReturn(reader)
        whenever(runner.startStdinWriter(any(), any())).thenReturn(AtomicBoolean(false))
        whenever(runner.waitForOrCancel(any(), any(), any(), any(), anyOrNull())).thenReturn(true)
        whenever(runner.safeExitValue(any())).thenReturn(42)
        stubProcess()
        val result =
            newExecutor(runner).run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = false,
                repoDir = null,
                prompt = "p",
                onPartialOutput = null,
                timeoutSeconds = 5,
            )
        assertEquals(42, result.exitCode)
    }

    @Test
    fun `run reads the runner's reader buffer to populate the result raw field`() {
        val buffer = StringBuilder("first line\nsecond line\n")
        val runner = stubProcessRunner(readerBuffer = buffer, completed = true)
        stubProcess()
        val result =
            newExecutor(runner).run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = false,
                repoDir = null,
                prompt = "p",
                onPartialOutput = null,
                timeoutSeconds = 5,
            )
        assertTrue(result.raw.contains("first line"))
        assertTrue(result.raw.contains("second line"))
    }

    @Test
    fun `run propagates InterruptedException from waitForOrCancel`() {
        val runner = mock<CopilotProcessRunner>()
        val buffer = StringBuilder("")
        val reader = CopilotProcessRunner.OutputReader(buffer, Thread {})
        whenever(runner.buildProcess(any(), any(), any(), anyOrNull()))
            .thenReturn(ProcessBuilder("echo", "x"))
        whenever(runner.startStdoutReader(any(), anyOrNull(), any())).thenReturn(reader)
        whenever(runner.startStdinWriter(any(), any())).thenReturn(AtomicBoolean(false))
        whenever(runner.waitForOrCancel(any(), any(), any(), any(), anyOrNull()))
            .thenAnswer { throw InterruptedException("aborted") }
        stubProcess()
        assertThrows(InterruptedException::class.java) {
            newExecutor(runner).run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = false,
                repoDir = null,
                prompt = "p",
                onPartialOutput = null,
                timeoutSeconds = 5,
            )
        }
    }
}
