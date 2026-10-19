package com.stacklens.adapter.out.ai

import com.stacklens.testsupport.DelayedInputStream
import com.stacklens.testsupport.FlakyInputStream
import com.stacklens.testsupport.FlakyOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class CopilotProcessRunnerStreamUseCoverageTest {
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

    private fun settle() {
        Thread.sleep(250)
    }

    @Test
    fun `a stdin flush that breaks inside the block is swallowed`() {
        val sink = FlakyOutputStream(failOnFlush = true)

        runner.startStdinWriter(processWith(ByteArrayInputStream(ByteArray(0)), sink), "short prompt")

        settle()
        assertTrue(sink.closeCalls >= 1)
    }

    @Test
    fun `a stdin close that breaks after a complete write is swallowed`() {
        val sink = FlakyOutputStream(failOnClose = true)

        runner.startStdinWriter(processWith(ByteArrayInputStream(ByteArray(0)), sink), "tail")

        settle()
        assertTrue(sink.closeCalls >= 1)
    }

    @Test
    fun `a stdin stream that breaks on write and on close is swallowed too`() {
        val sink = FlakyOutputStream(failOnWrite = true, failOnClose = true)

        runner.startStdinWriter(processWith(ByteArrayInputStream(ByteArray(0)), sink), "x".repeat(20_000))

        settle()
        assertTrue(sink.closeCalls >= 1)
    }

    @Test
    fun `a stdout stream that breaks on read and on close only logs`() {
        val source = FlakyInputStream(failOnRead = true, failOnClose = true)
        val reader = runner.startStdoutReader(processWith(source, FlakyOutputStream()), null, 1_000L)

        settle()
        assertTrue(source.closeCalls >= 1)
        assertEquals("", reader.buffer.toString())
    }

    @Test
    fun `a stdout close that breaks after the last line only logs`() {
        val source =
            FlakyInputStream(
                payload = "one\ntwo\n".toByteArray(),
                failOnClose = true,
            )
        val seen = AtomicInteger()
        val reader = runner.startStdoutReader(processWith(source, FlakyOutputStream()), { seen.incrementAndGet() }, 0L)

        settle()
        assertTrue(source.closeCalls >= 1)
        assertTrue(reader.buffer.toString().contains("two"))
    }

    @Test
    fun `a slow second line crosses the flush interval and pushes again`() {
        val source =
            DelayedInputStream(
                chunks = listOf("data: first\n".toByteArray(), "data: second\n".toByteArray()),
                pauseFrom = 1,
                pauseMillis = 120L,
            )
        val partials = mutableListOf<String>()

        val reader = runner.startStdoutReader(processWith(source, FlakyOutputStream()), { partials += it }, 50L)
        reader.thread.join(5_000)

        assertTrue(
            partials.any { it.contains("second") },
            "the throttled callback must have fired once the interval elapsed: $partials",
        )
        assertTrue(reader.buffer.toString().contains("second"))
    }
}
