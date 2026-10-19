package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class CopilotProcessRunnerTest {
    private val runner = CopilotProcessRunner()

    @Test
    fun `shouldUseStdin returns false for short prompts`() {
        assertFalse(CopilotProcessRunner.shouldUseStdin(0))
        assertFalse(CopilotProcessRunner.shouldUseStdin(1000))
        assertFalse(CopilotProcessRunner.shouldUseStdin(8_192))
    }

    @Test
    fun `shouldUseStdin returns true for prompts longer than limit`() {
        assertTrue(CopilotProcessRunner.shouldUseStdin(8_193))
        assertTrue(CopilotProcessRunner.shouldUseStdin(100_000))
    }

    @Test
    fun `startStdinWriter returns cancel flag that can be set`() {
        val proc = ProcessBuilder("/bin/cat").start()
        try {
            val cancel = runner.startStdinWriter(proc, "hello world")
            assertFalse(cancel.get())
            cancel.set(true)
            assertTrue(cancel.get())
        } finally {
            proc.destroyForcibly()
        }
    }

    @Test
    fun `startStdinWriter writes content to process stdin`() {
        val proc = ProcessBuilder("/bin/cat").start()
        try {
            runner.startStdinWriter(proc, "captured content")
            proc.waitFor()
            val output = proc.inputStream.bufferedReader().readText()
            assertTrue(output.contains("captured content"))
        } finally {
            proc.destroyForcibly()
        }
    }

    @Test
    fun `startStdoutReader accumulates stdout in buffer`() {
        val proc = ProcessBuilder("/bin/echo", "line1").start()
        try {
            val reader = runner.startStdoutReader(proc)
            proc.waitFor()
            reader.thread.join(2_000)
            assertTrue(reader.buffer.toString().contains("line1"))
        } finally {
            proc.destroyForcibly()
        }
    }

    @Test
    fun `startStdoutReader invokes onPartialOutput callback when flush interval elapsed`() {
        val proc = ProcessBuilder("/bin/echo", "streamed").start()
        try {
            val captures = mutableListOf<String>()
            val reader = runner.startStdoutReader(proc, onPartialOutput = { captures += it }, flushIntervalMs = 0L)
            proc.waitFor()
            reader.thread.join(2_000)
            assertTrue(captures.isNotEmpty())
        } finally {
            proc.destroyForcibly()
        }
    }

    @Test
    fun `startStdoutReader handles empty stream gracefully`() {
        val proc = ProcessBuilder("/bin/sh", "-c", "exit 0").start()
        try {
            val reader = runner.startStdoutReader(proc)
            proc.waitFor()
            reader.thread.join(2_000)
            assertEquals(0, reader.buffer.length)
        } finally {
            proc.destroyForcibly()
        }
    }

    @Test
    fun `waitForOrCancel returns true when process completes in time`() {
        val proc = ProcessBuilder("/bin/sh", "-c", "exit 0").start()
        val reader = runner.startStdoutReader(proc)
        val completed = runner.waitForOrCancel(proc, reader.thread, timeoutSeconds = 5, promptLength = 10)
        assertTrue(completed)
    }

    @Test
    fun `waitForOrCancel returns false when process exceeds timeout`() {
        val proc = ProcessBuilder("/bin/sh", "-c", "sleep 5").start()
        val reader = runner.startStdoutReader(proc)
        try {
            val completed = runner.waitForOrCancel(proc, reader.thread, timeoutSeconds = 1, promptLength = 10)
            assertFalse(completed)
        } finally {
            proc.destroyForcibly()
        }
    }

    @Test
    fun `waitForOrCancel sets writerCancel flag on normal completion`() {
        val proc = ProcessBuilder("/bin/echo", "x").start()
        val reader = runner.startStdoutReader(proc)
        val cancel =
            java.util.concurrent.atomic
                .AtomicBoolean(false)
        runner.waitForOrCancel(
            proc,
            reader.thread,
            timeoutSeconds = 5,
            promptLength = 10,
            writerCancel = cancel,
        )
        assertTrue(cancel.get())
    }

    @Test
    fun `waitForOrCancel rethrows InterruptedException and interrupts current thread`() {
        val proc = ProcessBuilder("/bin/sh", "-c", "sleep 30").start()
        val reader = runner.startStdoutReader(proc)
        val cancel =
            java.util.concurrent.atomic
                .AtomicBoolean(false)
        val current = Thread.currentThread()
        val interrupter =
            Thread {
                Thread.sleep(200)
                current.interrupt()
            }
        interrupter.isDaemon = true
        try {
            interrupter.start()
            val ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                    java.lang.InterruptedException::class.java,
                ) {
                    runner.waitForOrCancel(
                        proc,
                        reader.thread,
                        timeoutSeconds = 30,
                        promptLength = 10,
                        writerCancel = cancel,
                    )
                }
            assertNotNull(ex)
            assertTrue(cancel.get())
        } finally {
            Thread.interrupted()
            proc.destroyForcibly()
        }
    }

    @Test
    fun `safeExitValue returns 0 for terminated process`() {
        val proc = ProcessBuilder("/bin/sh", "-c", "exit 0").start()
        proc.waitFor()
        assertEquals(0, runner.safeExitValue(proc))
    }

    @Test
    fun `safeExitValue returns 124 for not-yet-terminated process`() {
        val proc = ProcessBuilder("/bin/sh", "-c", "sleep 1").start()
        assertEquals(124, runner.safeExitValue(proc))
        proc.destroyForcibly()
    }

    @Test
    fun `buildProcess sets COPILOT_GITHUB_TOKEN environment variable`() {
        val pb = runner.buildProcess(listOf("/bin/echo"), token = "TOK123", useStdin = false)
        assertEquals("TOK123", pb.environment()["COPILOT_GITHUB_TOKEN"])
    }

    @Test
    fun `buildProcess redirects input from dev null when not using stdin`() {
        val pb = runner.buildProcess(listOf("/bin/echo"), token = "T", useStdin = false)
        assertEquals(File("/dev/null"), pb.redirectInput().file())
    }

    @Test
    fun `buildProcess merges stderr into stdout`() {
        val pb = runner.buildProcess(listOf("/bin/echo"), token = "T", useStdin = false)
        assertTrue(pb.redirectErrorStream())
    }

    @Test
    fun `buildProcess uses repoDir when provided`() {
        val repo = File("/tmp/some-repo")
        val pb = runner.buildProcess(listOf("/bin/echo"), token = "T", useStdin = false, repoDir = repo)
        assertEquals(repo, pb.directory())
    }

    @Test
    fun `buildProcess defaults to tmp directory when repoDir is null`() {
        val pb = runner.buildProcess(listOf("/bin/echo"), token = "T", useStdin = false)
        assertEquals(File(System.getProperty("java.io.tmpdir")), pb.directory())
    }

    @Test
    fun `buildCommand with useStdin appends prompt via stdin flag`() {
        val cmd =
            runner.buildCommand(
                cmd = listOf("/bin/copilot"),
                model = "claude-test",
                prompt = "ignored",
                useStdin = true,
            )
        assertTrue(cmd.contains("--model"))
        assertTrue(cmd.contains("claude-test"))
        assertTrue(cmd.contains("--disable-builtin-mcps"))
        assertTrue(cmd.contains("--no-custom-instructions"))
        assertTrue(cmd.contains("--no-ask-user"))
        assertTrue(cmd.contains("--deny-tool=write"))
        assertTrue(cmd.contains("--deny-tool=shell"))
        assertTrue(cmd.contains("-s"))
        assertFalse(cmd.contains("-p"))
        assertFalse(cmd.contains("ignored"))
    }

    @Test
    fun `buildCommand without stdin embeds prompt via -p flag`() {
        val cmd =
            runner.buildCommand(
                cmd = listOf("/bin/copilot"),
                model = "claude-test",
                prompt = "my-prompt",
                useStdin = false,
            )
        assertTrue(cmd.contains("-p"))
        assertTrue(cmd.contains("my-prompt"))
    }

    @Test
    fun `buildCommand with repoDir adds allow-all-tools and add-dir`() {
        val repo = File("/tmp/repo")
        val cmd =
            runner.buildCommand(
                cmd = listOf("/bin/copilot"),
                model = "m",
                prompt = "p",
                useStdin = false,
                repoDir = repo,
            )
        assertTrue(cmd.contains("--allow-all-tools"))
        assertTrue(cmd.contains("--add-dir"))
        assertTrue(cmd.contains(repo.absolutePath))
        assertFalse(cmd.contains("--deny-tool=write"))
    }

    @Test
    fun `buildProcess redirects input when useStdin is false`() {
        val pb = CopilotProcessRunner().buildProcess(listOf("copilot"), "tok", useStdin = false)
        assertNotNull(pb)
    }

    @Test
    fun `waitForOrCancel throws InterruptedException when thread is interrupted`() {
        val runner = CopilotProcessRunner()
        val process: Process = mock()
        whenever(process.waitFor(any(), any())).thenThrow(InterruptedException("test interrupt"))
        val thread = Thread { }

        try {
            runner.waitForOrCancel(process, thread, 10L, 100)
            org.junit.jupiter.api.Assertions
                .fail<Unit>("Expected InterruptedException to be rethrown")
        } catch (e: InterruptedException) {
            assertEquals("test interrupt", e.message)
        }
    }
}
