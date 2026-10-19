package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ProcessExecutorFailurePathCoverageTest {
    @Test
    fun `a command without any executable is reported by its exception type`() {
        val result = ProcessExecutor.run(emptyList(), 1L)

        assertEquals(-1, result.exitCode)
        assertEquals("", result.stdout)
        assertTrue(result.stderr.isNotBlank(), result.stderr)
    }

    @Test
    fun `a stream that breaks while it is drained is swallowed by the worker`() {
        val broken =
            object : ByteArrayInputStream("first line".toByteArray()) {
                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    if (off == 0 && len > 0) throw IOException("pipe closed")
                    return super.read(b, off, len)
                }
            }
        val sink = StringBuilder()

        val thread = ProcessExecutor.drain(broken, sink, "broken-stream")
        thread.join(5_000)

        assertTrue(sink.isEmpty(), "the failing read must not contribute output: $sink")
    }

    @Test
    fun `a readable stream is drained line by line`() {
        val sink = StringBuilder()

        ProcessExecutor.drain(ByteArrayInputStream("one\ntwo\n".toByteArray()), sink, "reader").join(5_000)

        assertEquals("one\ntwo\n", sink.toString())
    }

    @Test
    fun `an interrupted worker leaves the drain without output`() {
        val hanging =
            object : InputStream() {
                override fun read(): Int {
                    Thread.sleep(300)
                    throw InterruptedException("cancelled")
                }
            }
        val sink = StringBuilder()

        val thread = ProcessExecutor.drain(hanging, sink, "hanging")
        thread.join(2_000)

        assertEquals("", sink.toString())
    }
}
