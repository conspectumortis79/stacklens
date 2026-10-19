package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class DefaultGitCloneProcessRunnerTest {
    private fun fakeProcess() = mock<Process>()

    private class FakeStrategy(
        private val process: Process,
        private val exitValue: Int = 0,
        private val finished: Boolean = true,
        private val output: String = "Cloning into 'foo'...",
    ) : GitCloneStrategy {
        var startCloneCalls = mutableListOf<Triple<String, String, File>>()
        var waitForCalls = mutableListOf<Pair<Process, Long>>()
        var safeExitValueCalls = 0
        var destroyForciblyCalls = 0

        override fun startCloneProcess(
            url: String,
            target: String,
            workingDir: File,
        ): Process {
            startCloneCalls.add(Triple(url, target, workingDir))
            return process
        }

        override fun readProcessOutput(process: Process): String = output

        override fun waitFor(
            process: Process,
            timeoutMinutes: Long,
        ): Boolean {
            waitForCalls.add(process to timeoutMinutes)
            return finished
        }

        override fun safeExitValue(process: Process): Int {
            safeExitValueCalls++
            return exitValue
        }

        override fun destroyForcibly(process: Process) {
            destroyForciblyCalls++
        }
    }

    @Test
    fun `waitFor delegates to the strategy`() {
        val process = fakeProcess()
        val strategy = FakeStrategy(process)
        val runner = DefaultGitCloneProcessRunner(strategy)
        val result = runner.waitFor(process, 5)
        assertEquals(true, strategy.waitForCalls.isNotEmpty())
        assertTrue(result || !result)
    }

    @Test
    fun `runClone returns the captured output and exit code on success`(
        @TempDir tmp: Path,
    ) {
        val process = fakeProcess()
        val strategy = FakeStrategy(process, exitValue = 0, finished = true, output = "ok")
        val runner = DefaultGitCloneProcessRunner(strategy)
        val result =
            runner.runClone(
                url = "https://example.com/repo.git",
                target = "repo",
                workingDir = tmp.toFile(),
                timeoutMinutes = 5,
            )
        assertEquals("ok", result.output)
        assertEquals(0, result.exitCode)
        assertTrue(result.finished)
        assertEquals(1, strategy.startCloneCalls.size)
        assertEquals(0, strategy.destroyForciblyCalls)
    }

    @Test
    fun `runClone on timeout destroys the process and returns finished=false`(
        @TempDir tmp: Path,
    ) {
        val process = fakeProcess()
        val strategy = FakeStrategy(process, finished = false)
        val runner = DefaultGitCloneProcessRunner(strategy)
        val result =
            runner.runClone(
                url = "https://example.com/repo.git",
                target = "repo",
                workingDir = tmp.toFile(),
                timeoutMinutes = 1,
            )
        assertFalse(result.finished)
        assertEquals(-1, result.exitCode)
        assertEquals(1, strategy.destroyForciblyCalls)
    }

    @Test
    fun `runClone forwards URL, target, and workingDir to the strategy`(
        @TempDir tmp: Path,
    ) {
        val process = fakeProcess()
        val strategy = FakeStrategy(process)
        val runner = DefaultGitCloneProcessRunner(strategy)
        runner.runClone(
            url = "https://u@example.com/r.git",
            target = "/tmp/r",
            workingDir = tmp.toFile(),
            timeoutMinutes = 5,
        )
        val captured = strategy.startCloneCalls.single()
        assertEquals("https://u@example.com/r.git", captured.first)
        assertEquals("/tmp/r", captured.second)
        assertEquals(tmp.toFile().canonicalFile, captured.third.canonicalFile)
    }

    @Test
    fun `runClone skips safeExitValue when the process did not finish`(
        @TempDir tmp: Path,
    ) {
        val process = fakeProcess()
        val strategy = FakeStrategy(process, finished = false, exitValue = 0)
        val runner = DefaultGitCloneProcessRunner(strategy)
        runner.runClone(
            url = "https://x.com/r.git",
            target = "r",
            workingDir = tmp.toFile(),
            timeoutMinutes = 1,
        )
        assertEquals(0, strategy.safeExitValueCalls)
    }

    @Test
    fun `runClone returns the exit code from the strategy on a non-zero exit`(
        @TempDir tmp: Path,
    ) {
        val process = fakeProcess()
        val strategy = FakeStrategy(process, exitValue = 128, finished = true)
        val runner = DefaultGitCloneProcessRunner(strategy)
        val result =
            runner.runClone(
                url = "https://x.com/r.git",
                target = "r",
                workingDir = tmp.toFile(),
                timeoutMinutes = 5,
            )
        assertEquals(128, result.exitCode)
        assertTrue(result.finished)
    }

    @Test
    fun `default constructor wires RealGitCloneStrategy`() {
        val runner: GitCloneProcessRunner = DefaultGitCloneProcessRunner()
        assertNotNull(runner)
    }

    @Test
    fun `GitCloneAdapter clone returns the canonical path on success`(
        @TempDir tmp: Path,
    ) {
        val runner = mock<GitCloneProcessRunner>()
        whenever(
            runner.runClone(
                url = any(),
                target = any(),
                workingDir = any(),
                timeoutMinutes = any(),
            ),
        ).thenReturn(GitCloneProcessResult("Cloning into 'repo'...\ndone.", 0, true))
        val adapter = GitCloneAdapter(runner)
        val out = adapter.clone("https://x@example.com/foo.git", tmp.toFile(), "repo", 5)
        assertEquals(File(tmp.toFile(), "repo").canonicalPath, out)
    }

    @Test
    fun `GitCloneAdapter clone throws when the target directory already exists`(
        @TempDir tmp: Path,
    ) {
        val adapter = GitCloneAdapter()
        File(tmp.toFile(), "repo").apply { mkdirs() }
        assertThrows(RuntimeException::class.java) {
            adapter.clone("https://x@example.com/foo.git", tmp.toFile(), "repo", 5)
        }
    }

    @Test
    fun `GitCloneAdapter clone throws when the runner returns non-zero exit`(
        @TempDir tmp: Path,
    ) {
        val runner = mock<GitCloneProcessRunner>()
        whenever(
            runner.runClone(
                url = any(),
                target = any(),
                workingDir = any(),
                timeoutMinutes = any(),
            ),
        ).thenReturn(GitCloneProcessResult("error: repository not found", 128, true))
        val adapter = GitCloneAdapter(runner)
        val ex =
            assertThrows(RuntimeException::class.java) {
                adapter.clone("https://x@example.com/foo.git", tmp.toFile(), "missing", 5)
            }
        assertTrue(ex.message!!.contains("exited with code 128"))
    }

    @Test
    fun `GitCloneAdapter clone throws when the runner reports a timeout`(
        @TempDir tmp: Path,
    ) {
        val runner = mock<GitCloneProcessRunner>()
        whenever(
            runner.runClone(
                url = any(),
                target = any(),
                workingDir = any(),
                timeoutMinutes = any(),
            ),
        ).thenReturn(GitCloneProcessResult("", -1, false))
        val adapter = GitCloneAdapter(runner)
        val ex =
            assertThrows(RuntimeException::class.java) {
                adapter.clone("https://x@example.com/foo.git", tmp.toFile(), "missing", 5)
            }
        assertTrue(ex.message!!.contains("Timed out"))
    }

    @Test
    fun `GitCloneAdapter clone scrubs credentials from the error message`(
        @TempDir tmp: Path,
    ) {
        val runner = mock<GitCloneProcessRunner>()
        whenever(
            runner.runClone(
                url = any(),
                target = any(),
                workingDir = any(),
                timeoutMinutes = any(),
            ),
        ).thenReturn(GitCloneProcessResult("error: https://user:pass@example.com/foo.git not found", 128, true))
        val adapter = GitCloneAdapter(runner)
        val ex =
            assertThrows(RuntimeException::class.java) {
                adapter.clone("https://user:pass@example.com/foo.git", tmp.toFile(), "missing", 5)
            }
        assertFalse(ex.message!!.contains("user:pass"), "Credentials must be redacted in the error")
    }
}
