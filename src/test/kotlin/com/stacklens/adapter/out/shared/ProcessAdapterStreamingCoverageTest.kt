package com.stacklens.adapter.out.shared

import com.stacklens.shared.ProcessUtils
import com.stacklens.testsupport.asManagedProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class ProcessAdapterStreamingCoverageTest {
    private val adapter = ProcessAdapter()

    @Test
    fun `run streaming hands every output line to the consumer and returns the exit code`() {
        val lines = mutableListOf<String>()

        val exit = adapter.runStreaming(listOf("/bin/sh", "-c", "printf 'a\\nb\\n'"), null, 20) { lines += it }

        assertEquals(0, exit)
        assertEquals(listOf("a", "b"), lines)
    }

    @Test
    fun `run streaming honours the working directory`() {
        val lines = mutableListOf<String>()

        val exit = adapter.runStreaming(listOf("/bin/pwd"), File(System.getProperty("user.dir")), 20) { lines += it }

        assertEquals(0, exit)
        assertTrue(lines.single().isNotBlank())
    }

    @Test
    fun `a silent process is killed once the timeout expires`() {
        val failure =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreaming(listOf("/bin/sleep", "30"), null, 1) { }
            }

        assertTrue(failure.message!!.contains("Process timed out after 1s"))
    }

    @Test
    fun `a process that closes its output but stays alive is reported as a timeout`() {
        val failure =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreaming(listOf("/bin/sh", "-c", "echo hi; exec 1>&-; sleep 2"), null, 1) { }
            }

        assertTrue(failure.message!!.contains("Process timed out after 1s"))
    }

    @Test
    fun `a builder that cannot be created is wrapped into a process execution exception`() {
        val failure =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreamingInternal(listOf("x"), null, 5, {}) { _, _ -> throw IllegalStateException("no builder") }
            }

        assertTrue(failure.message!!.contains("Failed to start process"))
    }

    @Test
    fun `a process that cannot be started is wrapped into a process execution exception`() {
        val builder = mock<ProcessBuilder>()
        whenever(builder.start()).thenThrow(java.io.IOException("no binary"))

        val failure =
            assertThrows(ProcessExecutionException::class.java) {
                adapter.runStreamingInternal(listOf("x"), null, 5, {}) { _, _ -> builder }
            }

        assertTrue(failure.message!!.contains("Failed to start process"))
    }

    @Test
    fun `the process builder redirects stderr and applies the working directory`() {
        val withDir = adapter.buildProcessBuilder(listOf("/bin/echo"), File(System.getProperty("user.dir")))
        val withoutDir = adapter.buildProcessBuilder(listOf("/bin/echo"), null)

        assertTrue(withDir.redirectErrorStream())
        assertEquals(File(System.getProperty("user.dir")).canonicalFile, withDir.directory())
        assertEquals(null, withoutDir.directory())
    }

    @Test
    fun `delegating helpers expose the process utilities`() {
        assertEquals(ProcessUtils.resolveGitBinary(), adapter.resolveGitBinary())
        assertEquals(ProcessUtils.resolveMakeBinary(), adapter.resolveMakeBinary())
        assertEquals(false, adapter.makefileHasTarget(System.getProperty("java.io.tmpdir"), "no-such-target"))

        val live = ProcessBuilder(listOf("/bin/sleep", "5")).start()
        adapter.killProcessTree(live.asManagedProcess())
        assertEquals(false, live.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && live.isAlive)

        val result = adapter.run(listOf("/bin/echo", "via-port"), timeoutSec = 20, workDir = null)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("via-port"))
    }
}
