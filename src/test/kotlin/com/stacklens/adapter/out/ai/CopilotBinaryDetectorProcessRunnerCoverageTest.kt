package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotBinaryDetectorProcessRunnerCoverageTest {
    private val runner = CopilotBinaryDetector.DefaultProcessRunner(timeoutSeconds = 5L, destroyGraceSeconds = 1L)

    @Test
    fun `a finished process reports its trimmed output and exit code`() {
        val result = runner.run(listOf("/bin/echo", "  copilot 1.2  "), mapOf("LC_ALL" to "C"))

        assertEquals(0, result.exitCode)
        assertEquals("copilot 1.2", result.stdout)
    }

    @Test
    fun `a process that outlives the timeout is destroyed and reported as timed out`() {
        val result = CopilotBinaryDetector.DefaultProcessRunner(timeoutSeconds = 1L, destroyGraceSeconds = 1L).run(listOf("/bin/sleep", "5"), emptyMap())

        assertEquals(124, result.exitCode)
        assertEquals("", result.stdout)
    }

    @Test
    fun `a process writing to both streams is captured through the merged reader`() {
        val script = listOf("/bin/sh", "-c", "printf 'a\\nb\\n'; printf 'c\\n' 2>&1")

        val result = runner.run(script, emptyMap())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("a") && result.stdout.contains("c"), result.stdout)
    }

    @Test
    fun `a command that fails keeps its error output`() {
        val result = runner.run(listOf("/bin/sh", "-c", "echo boom; exit 3"), emptyMap())

        assertEquals(3, result.exitCode)
        assertEquals("boom", result.stdout)
    }
}
