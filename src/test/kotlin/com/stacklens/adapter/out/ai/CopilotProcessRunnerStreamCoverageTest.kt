package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CopilotProcessRunnerStreamCoverageTest {
    private val runner = CopilotProcessRunner()

    @Test
    fun `the stdin writer streams the whole prompt and can be cancelled mid-flight`() {
        val process = ProcessBuilder(listOf("/bin/sh", "-c", "cat > '${File.createTempFile("copilot-", ".out").absolutePath}'")).start()
        val prompt = "x".repeat(20_000)

        val cancel = runner.startStdinWriter(process, prompt)
        cancel.set(true)
        process.waitFor(20, TimeUnit.SECONDS)

        assertEquals(true, cancel.get())
    }

    @Test
    fun `the stdout reader collects every line and forwards partial output`() {
        val process = ProcessBuilder(listOf("/bin/sh", "-c", "printf 'alpha\\nbeta\\n'; sleep 1; printf 'gamma\\n'")).start()
        val partials = mutableListOf<String>()

        val reader = runner.startStdoutReader(process, { partials += it }, 1L)
        val completed = runner.waitForOrCancel(process, reader.thread, 30, 12)

        assertEquals(true, completed)
        assertTrue(reader.buffer.toString().contains("alpha"))
        assertTrue(reader.buffer.toString().contains("gamma"))
        assertTrue(partials.isNotEmpty())
    }

    @Test
    fun `the stdout reader works without a partial callback`() {
        val process = ProcessBuilder(listOf("/bin/sh", "-c", "printf 'single\\n'")).start()

        val reader = runner.startStdoutReader(process)
        runner.waitForOrCancel(process, reader.thread, 30, 6)

        assertEquals("single", reader.buffer.toString().trim())
    }

    @Test
    fun `a stream that fails while reading is logged instead of thrown`() {
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(ThrowingStream())

        val reader = runner.startStdoutReader(process, { }, 1L)
        reader.thread.join(5_000)

        assertEquals("", reader.buffer.toString())
    }

    @Test
    fun `a process that never finishes is destroyed forcibly`() {
        val process = ProcessBuilder(listOf("/bin/sh", "-c", "exec 1>&-; sleep 40")).start()
        val reader = runner.startStdoutReader(process)

        val completed = runner.waitForOrCancel(process, reader.thread, 1, 100, AtomicBoolean(false))

        assertEquals(false, completed)
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(false, process.isAlive)
    }

    @Test
    fun `an interrupted wait re-arms the interrupt flag and kills the process`() {
        val process = mock<Process>()
        whenever(process.waitFor(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenThrow(InterruptedException("stop"))
        val reader = runner.startStdoutReader(ProcessBuilder(listOf("/bin/echo", "done")).start())

        try {
            val failure =
                org.junit.jupiter.api.assertThrows<InterruptedException> {
                    runner.waitForOrCancel(process, reader.thread, 5, 10, AtomicBoolean(false))
                }
            assertNotNull(failure.message)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `the exit value of a still running process is reported as a timeout code`() {
        val live = ProcessBuilder(listOf("/bin/sh", "-c", "sleep 5")).start()

        try {
            assertEquals(124, runner.safeExitValue(live))
        } finally {
            live.destroyForcibly()
        }

        val done = ProcessBuilder(listOf("/bin/echo", "done")).start()
        done.waitFor()
        assertEquals(0, runner.safeExitValue(done))
    }

    @Test
    fun `long prompts are sent over stdin instead of the command line`() {
        assertEquals(true, CopilotProcessRunner.shouldUseStdin(9_000))
        assertEquals(false, CopilotProcessRunner.shouldUseStdin(8_192))
    }

    private class ThrowingStream : java.io.InputStream() {
        override fun read(): Int = throw java.io.IOException("stream dead")

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = throw java.io.IOException("stream dead")
    }
}
