package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class ProcessAdapterStreamingTest {
    private class FakeProcess(
        private val stdout: InputStream,
        private val exitCode: Int = 0,
        private val exitsWithinBudget: Boolean = true,
    ) : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

        override fun getInputStream(): InputStream = stdout

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = exitCode

        override fun waitFor(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = exitsWithinBudget

        override fun exitValue(): Int = exitCode

        override fun destroy() {}

        override fun isAlive(): Boolean = false

        override fun destroyForcibly(): Process = this
    }

    private fun builderOf(process: Process): ProcessBuilder {
        val builder = mock<ProcessBuilder>()
        whenever(builder.start()).thenReturn(process)
        return builder
    }

    private val blockingIn: InputStream =
        object : InputStream() {
            override fun read(): Int {
                Thread.sleep(2_500)
                return -1
            }
        }

    private val brokenIn: InputStream =
        object : InputStream() {
            override fun read(): Int = throw IOException("stream closed by peer")
        }

    private val lineIn: InputStream = ByteArrayInputStream("erste zeile\n".toByteArray())

    private fun run(
        process: Process,
        onLine: (String) -> Unit,
        timeoutSec: Long = 6L,
    ): Int =
        ProcessAdapter().runStreamingInternal(
            cmd = listOf("fake", "cmd"),
            workDir = null,
            timeoutSec = timeoutSec,
            onLine = onLine,
            processBuilderFactory = { _, _ -> builderOf(process) },
        )

    @Test
    fun `a broken output stream still yields the exit code of the process`() {
        val lines = mutableListOf<String>()

        val exit = run(FakeProcess(brokenIn), { lines += it })

        assertEquals(0, exit)
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `a process that keeps the stream open past its budget times out`() {
        val failure =
            assertThrows(ProcessExecutionException::class.java) {
                run(FakeProcess(blockingIn, exitsWithinBudget = false), { }, timeoutSec = 1L)
            }

        assertTrue(failure.message!!.contains("Process timed out after 1s: fake cmd"), failure.message)
    }

    @Test
    fun `a closed stream with a process still running times out as well`() {
        val failure =
            assertThrows(ProcessExecutionException::class.java) {
                run(FakeProcess(lineIn, exitsWithinBudget = false), { }, timeoutSec = 4L)
            }

        assertTrue(failure.message!!.contains("Process timed out after 4s"), failure.message)
    }

    @Test
    fun `a consumer that breaks the stream is wrapped into a process failure`() {
        val failure =
            assertThrows(ProcessExecutionException::class.java) {
                run(FakeProcess(lineIn), { throw IllegalStateException("verbraucher weg") })
            }

        assertTrue(failure.message!!.contains("Process failed"), failure.message)
        assertEquals("verbraucher weg", failure.cause?.message)
    }

    @Test
    fun `lines are handed to the consumer in order`() {
        val lines = mutableListOf<String>()

        val exit = run(FakeProcess(lineIn), { lines += it })

        assertEquals(0, exit)
        assertEquals(listOf("erste zeile"), lines)
    }
}
