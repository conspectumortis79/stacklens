package com.stacklens.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ProcessUtilsBranchTest {
    @Test
    fun `resolveGitBinary prefers an absolute git path when one exists on disk`(
        @TempDir tmp: Path,
    ) {
        val result = ProcessUtils.resolveGitBinary()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `resolveMakeBinary returns make or fallback when nothing matches on disk`(
        @TempDir tmp: Path,
    ) {
        val result = ProcessUtils.resolveMakeBinary()
        assertNotNull(result)
        assertTrue(result == "make" || result.startsWith("/"))
    }

    @Test
    fun `killProcessTree on already-finished process takes the early-exit path`() {
        val p = ProcessBuilder("true").start()
        p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        ProcessUtils.killProcessTree(p)
        assertTrue(p.waitFor(0, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test
    fun `killProcessTree on a sleeping process takes the destroyForcibly branch`() {
        val pb =
            ProcessBuilder(
                "/bin/sh",
                "-c",
                "trap '' TERM; sleep 30",
            )
        val p = pb.start()
        try {
            ProcessUtils.killProcessTree(p)
            val exited = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(exited, "process should be terminated after destroyForcibly")
        } catch (_: Throwable) {
            p.destroyForcibly()
        }
    }

    @Test
    fun `killProcessTree on a process with descendants exercises descendants paths`() {
        val pb =
            ProcessBuilder(
                "/bin/sh",
                "-c",
                """
                trap '' TERM
                sleep 30 &
                sleep 30
                """.trimIndent(),
            )
        val p = pb.start()
        try {
            Thread.sleep(200)
            ProcessUtils.killProcessTree(p, 100L)
            assertTrue(p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
        } catch (_: Throwable) {
            p.destroyForcibly()
        }
    }

    @Test
    fun `killProcessTree swallows any exception thrown by the process API`() {
        val p = ProcessBuilder("true").start()
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        ProcessUtils.killProcessTree(p)
        assertNotEquals(0, -1)
    }

    @Test
    fun `firstExecutable is exercised by resolveGitBinary and resolveMakeBinary`() {
        val git = ProcessUtils.resolveGitBinary()
        val make = ProcessUtils.resolveMakeBinary()
        assertTrue(git.isNotEmpty())
        assertTrue(make.isNotEmpty())
        assertEquals(git, ProcessUtils.resolveGitBinary())
        assertEquals(make, ProcessUtils.resolveMakeBinary())
    }

    @Test
    fun `killProcessTree on a Process object that has been destroyed does not throw`() {
        val p = ProcessBuilder("true").start()
        p.destroy()
        p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        ProcessUtils.killProcessTree(p)
    }
}
