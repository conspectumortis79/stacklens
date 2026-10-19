package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GitCloneAdapterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `DefaultGitCloneProcessRunner returns the result of the strategy`() {
        val fakeProcess = ProcessBuilder("echo", "hi").start()
        val strategy =
            object : GitCloneStrategy {
                override fun startCloneProcess(
                    url: String,
                    target: String,
                    workingDir: File,
                ): Process = fakeProcess

                override fun readProcessOutput(process: Process): String = "out"

                override fun waitFor(
                    process: Process,
                    timeoutMinutes: Long,
                ): Boolean = true

                override fun safeExitValue(process: Process): Int = 0

                override fun destroyForcibly(process: Process) {
                    process.destroy()
                }
            }
        val runner = DefaultGitCloneProcessRunner(strategy)
        val result =
            runner.runClone(
                url = "u",
                target = "t",
                workingDir = tempDir.toFile(),
                timeoutMinutes = 1,
            )
        assertEquals("out", result.output)
        assertEquals(0, result.exitCode)
        assertTrue(result.finished)
        fakeProcess.inputStream.bufferedReader().readText()
        fakeProcess.waitFor()
    }

    @Test
    fun `DefaultGitCloneProcessRunner marks the process as not finished on timeout`() {
        val fakeProcess = ProcessBuilder("echo", "hi").start()
        val strategy =
            object : GitCloneStrategy {
                override fun startCloneProcess(
                    url: String,
                    target: String,
                    workingDir: File,
                ): Process = fakeProcess

                override fun readProcessOutput(process: Process): String = ""

                override fun waitFor(
                    process: Process,
                    timeoutMinutes: Long,
                ): Boolean = false

                override fun safeExitValue(process: Process): Int = -1

                override fun destroyForcibly(process: Process) {
                }
            }
        val runner = DefaultGitCloneProcessRunner(strategy)
        val result =
            runner.runClone(
                url = "u",
                target = "t",
                workingDir = tempDir.toFile(),
                timeoutMinutes = 1,
            )
        assertEquals(-1, result.exitCode)
        assertFalse(result.finished)
        runCatching { fakeProcess.inputStream.bufferedReader().readText() }
        runCatching { fakeProcess.waitFor() }
    }

    @Test
    fun `DefaultGitCloneProcessRunner delegates waitFor to the strategy`() {
        val fakeProcess = ProcessBuilder("echo", "hi").start()
        var called = false
        val strategy =
            object : GitCloneStrategy {
                override fun startCloneProcess(
                    url: String,
                    target: String,
                    workingDir: File,
                ): Process = fakeProcess

                override fun readProcessOutput(process: Process): String = ""

                override fun waitFor(
                    process: Process,
                    timeoutMinutes: Long,
                ): Boolean {
                    called = true
                    return true
                }

                override fun safeExitValue(process: Process): Int = 0

                override fun destroyForcibly(process: Process) {
                    process.destroy()
                }
            }
        DefaultGitCloneProcessRunner(strategy).waitFor(fakeProcess, 1)
        assertTrue(called)
        fakeProcess.inputStream.bufferedReader().readText()
        fakeProcess.waitFor()
    }

    @Test
    fun `clone throws when the target directory already exists`() {
        val parent = tempDir.toFile()
        val existing = File(parent, "name")
        existing.mkdirs()
        val runner =
            object : GitCloneProcessRunner {
                override fun runClone(
                    url: String,
                    target: String,
                    workingDir: File,
                    timeoutMinutes: Long,
                ): GitCloneProcessResult = error("should not be called")

                override fun waitFor(
                    process: Process,
                    timeoutMinutes: Long,
                ): Boolean = false
            }
        val adapter = GitCloneAdapter(runner)
        assertThrows<RuntimeException> {
            adapter.clone("u", parent, "name", 1)
        }
    }

    @Test
    fun `clone throws when the process exits with non-zero code and cleans up`() {
        val parent = tempDir.toFile()
        val target = File(parent, "name")
        val runner =
            object : GitCloneProcessRunner {
                override fun runClone(
                    url: String,
                    target: String,
                    workingDir: File,
                    timeoutMinutes: Long,
                ): GitCloneProcessResult =
                    GitCloneProcessResult(
                        output = "https://user:secret@host/repo: error: not found",
                        exitCode = 128,
                        finished = true,
                    )

                override fun waitFor(
                    process: Process,
                    timeoutMinutes: Long,
                ): Boolean = false
            }
        val adapter = GitCloneAdapter(runner)
        val ex =
            assertThrows<RuntimeException> {
                adapter.clone("u", parent, "name", 1)
            }
        assertTrue(ex.message!!.contains("128"))
        assertFalse(ex.message!!.contains("secret"))
        assertFalse(target.exists())
    }

    @Test
    fun `clone throws a timeout-specific message when the process is not finished`() {
        val parent = tempDir.toFile()
        val runner =
            object : GitCloneProcessRunner {
                override fun runClone(
                    url: String,
                    target: String,
                    workingDir: File,
                    timeoutMinutes: Long,
                ): GitCloneProcessResult =
                    GitCloneProcessResult(
                        output = "still cloning...",
                        exitCode = -1,
                        finished = false,
                    )

                override fun waitFor(
                    process: Process,
                    timeoutMinutes: Long,
                ): Boolean = false
            }
        val adapter = GitCloneAdapter(runner)
        val ex =
            assertThrows<RuntimeException> {
                adapter.clone("u", parent, "name", 2)
            }
        assertTrue(ex.message!!.contains("Timed out after 2 minutes"))
    }

    @Test
    fun `clone returns the canonical target path on success`() {
        val parent = tempDir.toFile()
        val runner =
            object : GitCloneProcessRunner {
                override fun runClone(
                    url: String,
                    target: String,
                    workingDir: File,
                    timeoutMinutes: Long,
                ): GitCloneProcessResult =
                    GitCloneProcessResult(
                        output = "Cloning into '$target'...\nDone.",
                        exitCode = 0,
                        finished = true,
                    )

                override fun waitFor(
                    process: Process,
                    timeoutMinutes: Long,
                ): Boolean = true
            }
        val adapter = GitCloneAdapter(runner)
        val path = adapter.clone("u", parent, "name", 1)
        assertEquals(File(parent, "name").canonicalPath, path)
    }
}
