package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ProcessAdapterTest {
    private val adapter = ProcessAdapter()
    private lateinit var tmp: File

    @BeforeEach
    fun setUp() {
        tmp = Files.createTempDirectory("process-adapter").toFile()
    }

    @AfterEach
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun `resolveGitBinary returns a non-blank path`() {
        assertTrue(adapter.resolveGitBinary().isNotBlank())
    }

    @Test
    fun `resolveMakeBinary returns a non-blank path`() {
        assertTrue(adapter.resolveMakeBinary().isNotBlank())
    }

    @Test
    fun `run returns successful ProcessResult for working command`() {
        val r = adapter.run(listOf("/bin/echo", "hi"), timeoutSec = 5)
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("hi"))
        assertTrue(r.success)
    }

    @Test
    fun `run propagates non-zero exit code`() {
        val r = adapter.run(listOf("/bin/sh", "-c", "exit 7"), timeoutSec = 5)
        assertEquals(7, r.exitCode)
        assertEquals(false, r.success)
    }

    @Test
    fun `run executes in the provided workDir`() {
        File(tmp, "marker.txt").writeText("xx")
        val r = adapter.run(listOf("/bin/sh", "-c", "cat marker.txt"), timeoutSec = 5, workDir = tmp)
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("xx"))
    }

    @Test
    fun `launch starts a process and returns it`() {
        val p = adapter.launch(listOf("/bin/sh", "-c", "exit 0"))
        assertTrue(p.isAlive)
        p.waitFor()
        assertEquals(0, p.exitValue())
    }

    @Test
    fun `runStreaming returns exit code 0 and invokes onLine for each line`() {
        val lines = mutableListOf<String>()
        val code =
            adapter.runStreaming(
                cmd = listOf("/bin/sh", "-c", "echo a; echo b; echo c"),
                workDir = null,
                timeoutSec = 10,
                onLine = { lines += it },
            )
        assertEquals(0, code)
        assertTrue(lines.contains("a"))
        assertTrue(lines.contains("b"))
        assertTrue(lines.contains("c"))
    }

    @Test
    fun `runStreaming with process that exceeds timeout throws timeout exception`() {
        val lines = mutableListOf<String>()
        val ex =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreaming(
                    cmd = listOf("/bin/sh", "-c", "sleep 30"),
                    workDir = null,
                    timeoutSec = 1,
                    onLine = { lines += it },
                )
            }
        assertTrue(ex.message!!.contains("timed out after 1s"), "was: ${ex.message}")
        assertTrue(lines.isEmpty(), "no output expected before timeout, got: $lines")
    }

    @Test
    fun `runStreaming times out when the process outlives its stdout`() {
        val lines = mutableListOf<String>()
        val ex =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreaming(
                    cmd = listOf("/bin/sh", "-c", "exec 1>&- 2>&-; sleep 30"),
                    workDir = null,
                    timeoutSec = 1,
                    onLine = { lines += it },
                )
            }
        assertTrue(ex.message!!.contains("timed out after 1s"), "was: ${ex.message}")
        assertTrue(lines.isEmpty(), "no output expected, got: $lines")
    }

    @Test
    fun `runStreaming runs in the provided workDir`() {
        File(tmp, "marker.txt").writeText("hi-from-dir")
        val lines = mutableListOf<String>()
        val code =
            adapter.runStreaming(
                cmd = listOf("/bin/sh", "-c", "cat marker.txt"),
                workDir = tmp,
                timeoutSec = 5,
                onLine = { lines += it },
            )
        assertEquals(0, code)
        assertTrue(lines.any { it.contains("hi-from-dir") })
    }

    @Test
    fun `makefileHasTarget delegates to MakefileRunner`() {
        File(tmp, "Makefile").writeText("build:\n\t@echo x\n")
        assertTrue(adapter.makefileHasTarget(tmp.absolutePath, "build"))
    }

    @Test
    fun `findMakeTarget delegates to MakefileRunner`() {
        File(tmp, "Makefile").writeText("foo:\n\t@echo x\nbar:\n\t@echo y\n")
        assertEquals("foo", adapter.findMakeTarget(tmp.absolutePath, "foo", "bar"))
    }

    @Test
    fun `runMakeTarget delegates to MakefileRunner`() {
        File(tmp, "Makefile").writeText("hello:\n\t@echo hi\n")
        val (code, out) = adapter.runMakeTarget(tmp.absolutePath, "hello", timeoutSecs = 10)
        assertEquals(0, code)
        assertTrue(out.contains("hi"))
    }

    @Test
    fun `killProcessTree terminates a running process`() {
        val p = adapter.launch(listOf("/bin/sh", "-c", "sleep 30"))
        assertTrue(p.isAlive)
        adapter.killProcessTree(p)
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(!p.isAlive, "process should be killed")
    }

    @Test
    fun `ProcessExecutionException stores message and cause`() {
        val cause = RuntimeException("inner")
        val ex = ProcessExecutionException("outer", cause)
        assertEquals("outer", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun `runStreaming wraps start failure in ProcessExecutionException`() {
        val ex =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreaming(
                    cmd = listOf("/this/binary/definitely/does/not/exist/xyz-12345"),
                    workDir = null,
                    timeoutSec = 5,
                    onLine = { },
                )
            }
        assertTrue(ex.message!!.contains("Failed to start process"))
        assertTrue(ex.cause != null)
    }

    @Test
    fun `runStreaming wraps start failure in ProcessExecutionException when workDir is set`() {
        val ex =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreaming(
                    cmd = listOf("/nonexistent-binary-1234"),
                    workDir = tmp,
                    timeoutSec = 5,
                    onLine = { },
                )
            }
        assertTrue(ex.message!!.contains("Failed to start process"))
    }

    @Test
    fun `runStreamingInternal wraps processBuilderFactory exception in ProcessExecutionException`() {
        val ex =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreamingInternal(
                    cmd = listOf("/bin/echo"),
                    workDir = null,
                    timeoutSec = 5,
                    onLine = { },
                    processBuilderFactory = { _, _ -> throw java.io.IOException("boom") },
                )
            }
        assertTrue(ex.message!!.contains("Failed to start process"))
        assertTrue(ex.cause is java.io.IOException)
    }

    @Test
    fun `runStreamingInternal rethrows ProcessExecutionException from pb start`() {
        val stubPb = ProcessBuilder("/nonexistent-binary-1234-xyz")
        val ex =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreamingInternal(
                    cmd = listOf("/nonexistent-binary-1234-xyz"),
                    workDir = null,
                    timeoutSec = 5,
                    onLine = { },
                    processBuilderFactory = { _, _ -> stubPb },
                )
            }
        assertTrue(ex.message!!.contains("Failed to start process"))
    }

    @Test
    fun `runStreamingInternal applies the workDir to the supplied processBuilder`() {
        val capturedDir = arrayOfNulls<File>(1)
        val code =
            adapter.runStreamingInternal(
                cmd = listOf("/bin/echo", "hi"),
                workDir = tmp,
                timeoutSec = 5,
                onLine = { },
                processBuilderFactory = { _, wd ->
                    val pb = ProcessBuilder("/bin/echo", "hi")
                    if (wd != null) {
                        pb.directory(wd)
                        capturedDir[0] = wd
                    }
                    pb
                },
            )
        assertEquals(0, code)
        assertSame(tmp, capturedDir[0])
    }

    @Test
    fun `runStreamingInternal handles a process with no workDir`() {
        var capturedDir: File? = null
        val code =
            adapter.runStreamingInternal(
                cmd = listOf("/bin/echo", "hi"),
                workDir = null,
                timeoutSec = 5,
                onLine = { },
                processBuilderFactory = { _, wd ->
                    capturedDir = wd
                    ProcessBuilder("/bin/echo", "hi")
                },
            )
        assertEquals(0, code)
        assertEquals(null, capturedDir)
    }

    @Test
    fun `buildProcessBuilder configures redirectErrorStream and directory`() {
        val pb = adapter.buildProcessBuilder(listOf("/bin/echo", "hi"), tmp)
        assertEquals(tmp, pb.directory())
        assertTrue(pb.redirectErrorStream())
    }

    @Test
    fun `buildProcessBuilder leaves directory null when workDir is null`() {
        val pb = adapter.buildProcessBuilder(listOf("/bin/echo", "hi"), null)
        assertEquals(null, pb.directory())
    }

    @Test
    fun `runStreaming reader thread catches exceptions during line read`() {
        val p = adapter.launch(listOf("/bin/sh", "-c", "echo hi; exit 0"))
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        val lines = mutableListOf<String>()
        val code =
            adapter.runStreaming(
                cmd = listOf("/bin/echo", "ok"),
                workDir = null,
                timeoutSec = 5,
                onLine = { lines += it },
            )
        assertEquals(0, code)
    }

    @Test
    fun `runStreaming uses blocking wait instead of polling sleep`() {
        val lines = mutableListOf<String>()
        val before = System.currentTimeMillis()
        val ex =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreaming(
                    cmd = listOf("/bin/sh", "-c", "sleep 30"),
                    workDir = null,
                    timeoutSec = 1,
                    onLine = { lines += it },
                )
            }
        val elapsed = System.currentTimeMillis() - before
        assertTrue(
            elapsed < 3_000,
            "timeout should fire promptly with blocking-wait, was ${elapsed}ms",
        )
        assertTrue(ex.message!!.contains("timed out after 1s"))
    }

    @Test
    fun `runStreaming forwards every line even when producer outpaces consumer`() {
        val lines = mutableListOf<String>()
        val cmd =
            listOf(
                "/bin/sh",
                "-c",
                (1..200).joinToString("\n") { "echo line-$it" },
            )
        val code =
            adapter.runStreaming(
                cmd = cmd,
                workDir = null,
                timeoutSec = 10,
                onLine = { lines += it },
            )
        assertEquals(0, code)
        assertEquals(200, lines.size)
        assertEquals("line-1", lines.first())
        assertEquals("line-200", lines.last())
    }
}
