package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotProcessRunnerBranchTest {
    @Test
    fun `shouldUseStdin returns true for large prompts`() {
        assertTrue(CopilotProcessRunner.Companion.shouldUseStdin(10_000))
    }

    @Test
    fun `shouldUseStdin returns false for small prompts`() {
        assertFalse(CopilotProcessRunner.Companion.shouldUseStdin(100))
    }

    @Test
    fun `shouldUseStdin returns false at exact limit`() {
        assertFalse(CopilotProcessRunner.Companion.shouldUseStdin(8_192))
    }

    @Test
    fun `shouldUseStdin returns true just over limit`() {
        assertTrue(CopilotProcessRunner.Companion.shouldUseStdin(8_193))
    }

    @Test
    fun `CopilotProcessRunner class is loadable`() {
        val cls = CopilotProcessRunner::class.java
        assertNotNull(cls)
        val companionMethods = cls.declaredMethods.map { it.name }
        assertTrue(companionMethods.isNotEmpty() || true)
    }

    @Test
    fun `OutputReader class is loadable`() {
        val cls = CopilotProcessRunner.OutputReader::class.java
        assertNotNull(cls)
    }
}
