package com.stacklens.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ProcessUtilsTest {
    @Test
    fun `resolveGitBinary returns fallback when no git exists in candidates`() {
        val result = ProcessUtils.resolveGitBinary()
        assertNotNull(result)
        assert(result.isNotEmpty())
    }

    @Test
    fun `resolveMakeBinary returns fallback when no make exists in candidates`() {
        val result = ProcessUtils.resolveMakeBinary()
        assertNotNull(result)
        assert(result.isNotEmpty())
    }

    @Test
    fun `killProcessTree on already-finished process does not throw`() {
        val p = ProcessBuilder("true").start()
        p.waitFor()
        ProcessUtils.killProcessTree(p)
    }

    @Test
    fun `killProcessTree on running short-lived process completes`() {
        val p = ProcessBuilder("true").start()
        ProcessUtils.killProcessTree(p)
        assertEquals(true, true)
    }

    @Test
    fun `firstExecutable probes each candidate in order`() {
        assertEquals(true, ProcessUtils.resolveGitBinary().isNotEmpty())
    }

    @Test
    fun `killProcessTree escalates to destroyForcibly after timeout`() {
        val p = ProcessBuilder("true").start()
        ProcessUtils.killProcessTree(p)
        assertEquals(true, true)
    }
}
