package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class CopilotProcessRunnerStreamThreadCoverageTest {
    private val process = mock<Process>()

    init {
        whenever(process.inputStream).thenReturn(ByteArrayInputStream("first\nsecond\n".toByteArray()))
        whenever(process.errorStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
    }

    private fun awaitQuietly(thread: Thread) {
        val deadline = System.currentTimeMillis() + 4_000L
        while (thread.isAlive && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 4_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertTrue(condition(), "the worker thread never reached the expected state")
    }

    @Test
    fun `the stdin writer flushes a short prompt in full`() {
        val sink = ByteArrayOutputStream()
        whenever(process.outputStream).thenReturn(sink)

        val cancel = CopilotProcessRunner().startStdinWriter(process, "short prompt")
        awaitUntil { sink.size() > 0 }

        assertEquals("short prompt", String(sink.toByteArray()))
        assertEquals(false, cancel.get())
    }

    @Test
    fun `the stdin writer stops early once the run is cancelled`() {
        val endless = mock<Process>()
        whenever(endless.outputStream).thenReturn(
            object : ByteArrayOutputStream() {
                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {
                }
            },
        )

        val runner = CopilotProcessRunner()
        val cancel = runner.startStdinWriter(endless, "x".repeat(200_000))
        cancel.set(true)
        awaitUntil { cancel.get() }
    }

    @Test
    fun `the stdout reader reports partial output on every chunk when the interval is zero`() {
        val partials = AtomicInteger()

        val reader = CopilotProcessRunner().startStdoutReader(process, { partials.incrementAndGet() }, 0L)
        awaitQuietly(reader.thread)

        assertTrue(partials.get() >= 1, "expected at least one partial callback")
        assertTrue(reader.buffer.toString().contains("second"))
    }

    @Test
    fun `the stdout reader stays quiet without a callback`() {
        val runner = CopilotProcessRunner()

        val reader = runner.startStdoutReader(process, null, 1_000L)
        awaitQuietly(reader.thread)

        assertTrue(reader.buffer.toString().contains("first"))
    }

    @Test
    fun `a timeout during the wait destroys the process and joins the reader`() {
        val runner = CopilotProcessRunner()
        val stalled = mock<Process>()
        whenever(stalled.waitFor(any(), any())).thenReturn(false)
        whenever(stalled.destroyForcibly()).thenReturn(stalled)
        whenever(stalled.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        val reader = runner.startStdoutReader(stalled, null, 1_000L)

        assertEquals(false, runner.waitForOrCancel(stalled, reader.thread, 1L, 10))
    }

    @Test
    fun `an interrupted wait destroys the process and restores the interrupt flag`() {
        val runner = CopilotProcessRunner()
        val interrupted = mock<Process>()
        whenever(interrupted.waitFor(any(), any())).thenThrow(InterruptedException("cancelled"))
        whenever(interrupted.destroyForcibly()).thenReturn(interrupted)
        whenever(interrupted.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        val reader = runner.startStdoutReader(interrupted, null, 1_000L)

        var caught = false
        try {
            runner.waitForOrCancel(interrupted, reader.thread, 1L, 10)
        } catch (expected: InterruptedException) {
            caught = true
            assertTrue(expected.message!!.contains("cancelled"))
        }
        assertTrue(caught)
        assertTrue(Thread.interrupted())
    }
}
