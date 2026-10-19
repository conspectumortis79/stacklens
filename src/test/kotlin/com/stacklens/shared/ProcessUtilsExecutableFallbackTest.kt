package com.stacklens.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProcessUtilsExecutableFallbackTest {
    @Test
    fun `falls back to the literal name when no candidate is executable`() {
        assertEquals("git", ProcessUtils.firstExecutable(listOf("/no/such/git-one", "/no/such/git-two"), "git"))
    }

    @Test
    fun `returns the first executable candidate`(
        @TempDir dir: File,
    ) {
        val missing = File(dir, "missing")
        val notExecutable = script(dir, "not-executable", executable = false)
        val first = script(dir, "first", executable = true)
        val second = script(dir, "second", executable = true)
        val candidates = listOf(missing.absolutePath, notExecutable.absolutePath, first.absolutePath, second.absolutePath)

        assertEquals(first.absolutePath, ProcessUtils.firstExecutable(candidates, "git"))
    }

    @Test
    fun `public resolvers delegate to the same candidate lists they declare`(
        @TempDir dir: File,
    ) {
        val git = script(dir, "git", executable = true)
        val make = script(dir, "make", executable = true)

        assertEquals(git.absolutePath, ProcessUtils.firstExecutable(listOf(git.absolutePath), "git"))
        assertEquals(make.absolutePath, ProcessUtils.firstExecutable(listOf(make.absolutePath), "make"))
        assertTrue(ProcessUtils.GIT_CANDIDATES.isNotEmpty())
        assertTrue(ProcessUtils.MAKE_CANDIDATES.isNotEmpty())
        assertTrue(ProcessUtils.resolveGitBinary().isNotBlank())
        assertTrue(ProcessUtils.resolveMakeBinary().isNotBlank())
    }

    private fun script(
        dir: File,
        name: String,
        executable: Boolean,
    ): File {
        val file = File(dir, name)
        file.writeText("#!/bin/sh\n")
        file.setExecutable(executable)
        return file
    }
}
