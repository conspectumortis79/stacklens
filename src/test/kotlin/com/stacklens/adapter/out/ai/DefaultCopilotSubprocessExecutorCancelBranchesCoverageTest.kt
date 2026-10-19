package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class DefaultCopilotSubprocessExecutorCancelBranchesCoverageTest {
    private val fakeProcess = mock<Process>()

    init {
        whenever(fakeProcess.inputStream).thenReturn(ByteArrayInputStream("done".toByteArray()))
        whenever(fakeProcess.outputStream).thenReturn(ByteArrayOutputStream())
        whenever(fakeProcess.errorStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(fakeProcess.exitValue()).thenReturn(0)
    }

    private fun runner(
        buffer: String,
        completed: Boolean,
    ): CopilotProcessRunner {
        val reader = CopilotProcessRunner.OutputReader(StringBuilder(buffer), Thread {})
        val builder = mock<ProcessBuilder>()
        whenever(builder.start()).thenReturn(fakeProcess)
        val runner = mock<CopilotProcessRunner>()
        whenever(runner.buildProcess(any(), any(), any(), anyOrNull())).thenReturn(builder)
        whenever(runner.startStdoutReader(any(), anyOrNull(), any())).thenReturn(reader)
        whenever(runner.startStdinWriter(any(), any())).thenReturn(AtomicBoolean(false))
        whenever(runner.waitForOrCancel(any(), any(), any(), any(), anyOrNull())).thenReturn(completed)
        whenever(runner.safeExitValue(any())).thenReturn(0)
        return runner
    }

    @Test
    fun `a polling canceller that never fires lets the run finish`() {
        val executor = DefaultCopilotSubprocessExecutor(runner("answer", completed = true))

        val result =
            executor.run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = true,
                repoDir = null,
                prompt = "a".repeat(9000),
                onPartialOutput = { },
                timeoutSeconds = 5,
                isCancelled = { false },
            )

        assertEquals("answer", result.raw)
        verify(fakeProcess, never()).destroyForcibly()
    }

    @Test
    fun `a cancellation observed after the process finished destroys the process`() {
        val executor = DefaultCopilotSubprocessExecutor(runner("answer", completed = true))

        assertThrows(InterruptedException::class.java) {
            executor.run(
                command = listOf("copilot"),
                token = "tok",
                useStdin = false,
                repoDir = null,
                prompt = "short",
                onPartialOutput = null,
                timeoutSeconds = 5,
                isCancelled = { true },
            )
        }
        verify(fakeProcess).destroyForcibly()
    }

    @Test
    fun `a polling canceller is consulted while the process is still running`() {
        var polls = 0
        val executor = DefaultCopilotSubprocessExecutor(runner("answer", completed = false))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                executor.run(
                    command = listOf("copilot"),
                    token = "tok",
                    useStdin = false,
                    repoDir = null,
                    prompt = "short",
                    onPartialOutput = null,
                    timeoutSeconds = 7,
                    isCancelled = {
                        polls++
                        false
                    },
                )
            }

        assertNotNull(failure.message)
        assertEquals(0, polls, "the timeout path reports before the canceller is consulted")
        verify(fakeProcess).destroyForcibly()
    }
}
