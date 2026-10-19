package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class CopilotProcessRunnerStreamFailureCoverageTest {
    private val runner = CopilotProcessRunner()

    private fun processWith(
        input: InputStream,
        output: OutputStream,
    ): Process {
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(input)
        whenever(process.outputStream).thenReturn(output)
        return process
    }

    private val explodingInput: InputStream =
        object : InputStream() {
            override fun read(): Int = throw IOException("pipe closed")

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int = throw IOException("pipe closed")
        }

    private val explodingOutput: OutputStream =
        object : OutputStream() {
            override fun write(b: Int) = throw IOException("stdin closed")
        }

    private fun await(thread: Thread) {
        val deadline = System.currentTimeMillis() + 4_000L
        while (thread.isAlive && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
    }

    private fun awaitWorkerNamed(name: String) {
        val deadline = System.currentTimeMillis() + 4_000L
        while (System.currentTimeMillis() < deadline) {
            if (Thread.getAllStackTraces().keys.none { it.name == name && it.isAlive }) return
            Thread.sleep(5)
        }
    }

    @Test
    fun `a stdin writer survives a stream that breaks while writing`() {
        val process = processWith(ByteArrayInputStream(ByteArray(0)), explodingOutput)

        val cancel = runner.startStdinWriter(process, "x".repeat(20_000))
        awaitWorkerNamed("copilot-stdin")

        assertEquals(false, cancel.get())
    }

    @Test
    fun `a stdout reader reports partial output and swallows a callback failure`() {
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(ByteArrayInputStream("line one\nline two\n".toByteArray()))
        val calls = AtomicInteger()

        val reader =
            runner.startStdoutReader(
                process,
                {
                    calls.incrementAndGet()
                    throw IllegalStateException("the ui is gone")
                },
                0L,
            )
        await(reader.thread)

        assertTrue(calls.get() > 0, "the partial callback must have been used")
        assertTrue(reader.buffer.toString().contains("line two"))
    }

    @Test
    fun `a stdout reader logs a stream that breaks while reading`() {
        val process = processWith(explodingInput, ByteArrayOutput)

        val reader = runner.startStdoutReader(process, { }, 0L)
        await(reader.thread)

        assertEquals("", reader.buffer.toString())
    }

    @Test
    fun `a timed out process is destroyed even when the destroy itself fails`() {
        val process = mock<Process>()
        whenever(process.waitFor(any(), any())).thenReturn(false)
        whenever(process.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(process.destroyForcibly()).thenThrow(IllegalStateException("already reaped"))

        assertEquals(false, runner.waitForOrCancel(process, Thread { }, 1L, 12, null))
    }

    @Test
    fun `an interrupted wait reports the interruption after cleaning up`() {
        val process = mock<Process>()
        whenever(process.waitFor(any(), any())).thenThrow(InterruptedException("stopped"))
        whenever(process.destroyForcibly()).thenThrow(IllegalStateException("already reaped"))

        assertThrows(InterruptedException::class.java) {
            runner.waitForOrCancel(process, Thread { }, 1L, 12, null)
        }
        assertTrue(Thread.interrupted())
    }

    private object ByteArrayOutput : java.io.ByteArrayOutputStream()
}
