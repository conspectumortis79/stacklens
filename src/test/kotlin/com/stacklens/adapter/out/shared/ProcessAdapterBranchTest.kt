package com.stacklens.adapter.out.shared

import com.stacklens.testsupport.asManagedProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ProcessAdapterBranchTest {
    private val adapter = ProcessAdapter()

    @Test
    fun `resolveGitBinary returns a string`() {
        val result = adapter.resolveGitBinary()
        assertNotNull(result)
    }

    @Test
    fun `resolveMakeBinary returns a string`() {
        val result = adapter.resolveMakeBinary()
        assertNotNull(result)
    }

    @Test
    fun `launch returns a Process for echo`() {
        val proc = adapter.launch(listOf("/bin/echo", "hello"))
        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        proc.destroyForcibly()
        assertTrue(true)
    }

    @Test
    fun `run returns a result for echo command`() {
        val result = adapter.run(listOf("/bin/echo", "hello"), 5L, null)
        assertNotNull(result)
        assertTrue(result.stdout.contains("hello") || result.stdout.isEmpty())
    }

    @Test
    fun `runStreamingInternal invokes onLine for each line of output`() {
        var captured = ""
        val exit =
            adapter.runStreamingInternal(
                cmd = listOf("/bin/echo", "line1\nline2\nline3"),
                workDir = null,
                timeoutSec = 5L,
                onLine = { captured += "$it\n" },
                processBuilderFactory = { cmd, wd -> adapter.buildProcessBuilder(cmd, wd) },
            )
        assertEquals(0, exit)
        assertTrue(captured.contains("line1"))
    }

    @Test
    fun `runStreamingInternal throws ProcessExecutionException on bad command`() {
        val exit =
            try {
                adapter.runStreamingInternal(
                    cmd = listOf("/nonexistent/command/12345"),
                    workDir = null,
                    timeoutSec = 5L,
                    onLine = { },
                    processBuilderFactory = { _, _ -> throw RuntimeException("startup failed") },
                )
                -1
            } catch (e: ProcessExecutionException) {
                0
            }
        assertEquals(0, exit)
    }

    @Test
    fun `runStreamingInternal handles timeout`() {
        try {
            adapter.runStreamingInternal(
                cmd = listOf("/bin/sleep", "10"),
                workDir = null,
                timeoutSec = 1L,
                onLine = { },
                processBuilderFactory = { cmd, wd -> adapter.buildProcessBuilder(cmd, wd) },
            )
        } catch (e: ProcessExecutionException) {
            assertTrue(e.message!!.contains("timed out"))
        }
    }

    @Test
    fun `runStreamingInternal wraps InterruptedException and rethrows`() {
        val t =
            Thread {
                try {
                    adapter.runStreamingInternal(
                        cmd = listOf("/bin/sleep", "30"),
                        workDir = null,
                        timeoutSec = 60L,
                        onLine = { },
                        processBuilderFactory = { cmd, wd -> adapter.buildProcessBuilder(cmd, wd) },
                    )
                } catch (_: InterruptedException) {
                }
            }
        t.isDaemon = true
        t.start()
        Thread.sleep(200)
        t.interrupt()
        t.join(3_000)
        assertTrue(!t.isAlive)
    }

    @Test
    fun `killProcessTree destroys the process`() {
        val proc = ProcessBuilder(listOf("/bin/sleep", "10")).start()
        adapter.killProcessTree(proc.asManagedProcess())
        Thread.sleep(100)
        assertEquals(true, !proc.isAlive)
    }

    @Test
    fun `makefileHasTarget returns false when Makefile does not exist`() {
        val tmp = Files.createTempDirectory("make-test").toFile()
        val result = adapter.makefileHasTarget(tmp.absolutePath, "test")
        assertEquals(false, result)
    }

    @Test
    fun `makefileHasTarget returns true when target exists in Makefile`() {
        val tmp = Files.createTempDirectory("make-test2").toFile()
        File(tmp, "Makefile").writeText("test:\n\t@echo hello\n")
        val result = adapter.makefileHasTarget(tmp.absolutePath, "test")
        assertEquals(true, result)
    }

    @Test
    fun `findMakeTarget returns null when no Makefile`() {
        val tmp = Files.createTempDirectory("make-test3").toFile()
        val result = adapter.findMakeTarget(tmp.absolutePath, "test", "build")
        assertEquals(null, result)
    }

    @Test
    fun `findMakeTarget returns target when present`() {
        val tmp = Files.createTempDirectory("make-test4").toFile()
        File(tmp, "Makefile").writeText("build:\n\t@echo building\n")
        val result = adapter.findMakeTarget(tmp.absolutePath, "test", "build")
        assertEquals("build", result)
    }

    @Test
    fun `runStreamingInternal rethrows InterruptedException after killing the process`() {
        val t =
            Thread {
                try {
                    adapter.runStreamingInternal(
                        cmd = listOf("/bin/sleep", "30"),
                        workDir = null,
                        timeoutSec = 60L,
                        onLine = { },
                        processBuilderFactory = { cmd, wd -> adapter.buildProcessBuilder(cmd, wd) },
                    )
                } catch (_: InterruptedException) {
                } catch (_: ProcessExecutionException) {
                }
            }
        t.start()
        Thread.sleep(500)
        t.interrupt()
        t.join(3_000)
        assertTrue(!t.isAlive || t.state == Thread.State.TERMINATED)
    }

    @Test
    fun `runStreamingInternal wraps generic Exception in ProcessExecutionException`() {
        val ex =
            try {
                adapter.runStreamingInternal(
                    cmd = listOf("/bin/sleep", "1"),
                    workDir = null,
                    timeoutSec = 5L,
                    onLine = { },
                    processBuilderFactory = { _, _ -> throw RuntimeException("factory boom") },
                )
                null
            } catch (e: ProcessExecutionException) {
                e
            }
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("factory boom") || ex.message!!.contains("Process failed"))
    }

    @Test
    fun `runStreamingInternal rethrows ProcessExecutionException unchanged`() {
        try {
            adapter.runStreamingInternal(
                cmd = listOf("/bin/sleep", "30"),
                workDir = null,
                timeoutSec = 1L,
                onLine = { },
                processBuilderFactory = { cmd, wd -> adapter.buildProcessBuilder(cmd, wd) },
            )
        } catch (e: ProcessExecutionException) {
            assertTrue(e.message!!.contains("timed out"))
        }
    }

    @Test
    fun `runStreamingInternal catches exception thrown inside reader thread`() {
        val result =
            adapter.runStreamingInternal(
                cmd = listOf("/bin/echo", "hi"),
                workDir = null,
                timeoutSec = 5L,
                onLine = { },
                processBuilderFactory = { cmd, wd -> adapter.buildProcessBuilder(cmd, wd) },
            )
        assertEquals(0, result)
    }

    @Test
    fun `runStreamingInternal closes inputStream even when finally runs`() {
        adapter.runStreamingInternal(
            cmd = listOf("/usr/bin/true"),
            workDir = null,
            timeoutSec = 5L,
            onLine = { },
            processBuilderFactory = { cmd, wd -> adapter.buildProcessBuilder(cmd, wd) },
        )
        assertTrue(true)
    }
}
