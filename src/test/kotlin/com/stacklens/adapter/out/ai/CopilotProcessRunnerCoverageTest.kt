package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CopilotProcessRunnerCoverageTest {
    private val runner = CopilotProcessRunner()

    private fun started(command: List<String>): Process = ProcessBuilder(command).redirectErrorStream(true).start()

    @Test
    fun `the inline prompt limit separates stdin from argument transport`() {
        assertTrue(CopilotProcessRunner.shouldUseStdin(9000))
        assertFalse(CopilotProcessRunner.shouldUseStdin(10))
    }

    @Test
    fun `a large prompt is written to the process in chunks and echoed back`() {
        val process = started(listOf("/bin/cat"))
        val content = (1..900).joinToString("\n") { "line-$it-padding" } + "\n"
        val reader = runner.startStdoutReader(process)

        val cancel = runner.startStdinWriter(process, content)

        val completed = runner.waitForOrCancel(process, reader.thread, 30L, content.length, cancel)

        assertTrue(completed)
        assertEquals(content.trim(), reader.buffer.toString().trim())
        assertEquals(0, runner.safeExitValue(process))
    }

    @Test
    fun `partial output is flushed while the process keeps talking`() {
        val process = started(listOf("/bin/sh", "-c", "printf 'alpha\\n'; sleep 1; printf 'beta\\n'"))
        val seen = mutableListOf<String>()
        val reader =
            runner.startStdoutReader(
                process,
                onPartialOutput = { chunk -> synchronized(seen) { seen += chunk } },
                flushIntervalMs = 0L,
            )

        runner.waitForOrCancel(process, reader.thread, 30L, 10)
        reader.thread.join(5_000)

        assertTrue(reader.buffer.toString().contains("beta"), reader.buffer.toString())
        assertTrue(synchronized(seen) { seen.isNotEmpty() }, seen.toString())
    }

    @Test
    fun `a broken standard input is swallowed by the writer thread`() {
        val process = mock<Process>()
        whenever(process.outputStream).thenThrow(IllegalStateException("stream gone"))

        val cancel = runner.startStdinWriter(process, "x".repeat(20_000))

        assertFalse(cancel.get())
    }

    @Test
    fun `a failing output stream is logged instead of thrown`() {
        val process = mock<Process>()
        whenever(process.inputStream).thenThrow(IllegalStateException("pipe closed"))

        val reader = runner.startStdoutReader(process)
        reader.thread.join(5_000)

        assertEquals("", reader.buffer.toString())
    }

    @Test
    fun `a slow process is killed when the timeout elapses`() {
        val process = started(listOf("/bin/sh", "-c", "sleep 20"))
        val reader = runner.startStdoutReader(process)
        val cancel = AtomicBoolean(false)

        val completed = runner.waitForOrCancel(process, reader.thread, 0L, 5, cancel)

        assertFalse(completed)
        assertTrue(cancel.get())
        process.waitFor(5, TimeUnit.SECONDS)
        assertFalse(process.isAlive())
        assertEquals(137, runner.safeExitValue(process))
    }

    @Test
    fun `an interrupted wait cancels the writer and rethrows`() {
        val process = mock<Process>()
        whenever(process.waitFor(any(), any())).thenThrow(InterruptedException("interrupted"))
        val cancel = AtomicBoolean(false)

        org.junit.jupiter.api.Assertions.assertThrows(InterruptedException::class.java) {
            runner.waitForOrCancel(process, Thread(), 5L, 7, cancel)
        }

        assertTrue(cancel.get())
        Thread.interrupted()
    }

    @Test
    fun `an interrupted wait without a writer flag still terminates the process`() {
        val process = mock<Process>()
        whenever(process.waitFor(any(), any())).thenThrow(InterruptedException("interrupted"))

        org.junit.jupiter.api.Assertions.assertThrows(InterruptedException::class.java) {
            runner.waitForOrCancel(process, Thread(), 5L, 7)
        }

        Thread.interrupted()
    }

    @Test
    fun `the process builder injects the token and redirects errors`(
        @TempDir dir: Path,
    ) {
        val withRepo = runner.buildProcess(listOf("/bin/cat"), "tok", useStdin = true, repoDir = dir.toFile())
        val withoutRepo = runner.buildProcess(listOf("/bin/cat"), "tok", useStdin = false)

        assertEquals("tok", withRepo.environment()["COPILOT_GITHUB_TOKEN"])
        assertEquals(dir.toFile(), withRepo.directory())
        assertEquals(File(System.getProperty("java.io.tmpdir")), withoutRepo.directory())
    }

    @Test
    fun `the command line differs between stdin and inline prompts`(
        @TempDir dir: Path,
    ) {
        val stdinCommand = runner.buildCommand(listOf("copilot"), "gpt-5", "prompt text", useStdin = true, repoDir = dir.toFile())
        val inlineCommand = runner.buildCommand(listOf("copilot"), "gpt-5", "short", useStdin = false)

        assertEquals(listOf("copilot"), stdinCommand.take(1))
        assertTrue(stdinCommand.contains("--model"), stdinCommand.toString())
        assertTrue(inlineCommand.any { it.contains("short") || it == "-" }, inlineCommand.toString())
        assertFalse(File(dir.toFile(), "missing").exists())
    }
}
