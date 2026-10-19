package com.stacklens.domain.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentTextUtilsBranchesTest {
    @Test
    fun `generateFeatureSlug falls back to feature when every token has no alphanumerics`() {
        assertEquals("feature", AgentTextUtils.generateFeatureSlug("--- !!! --- ???"))
    }

    @Test
    fun `generateFeatureSlug falls back to feature for empty input`() {
        assertEquals("feature", AgentTextUtils.generateFeatureSlug(""))
    }

    @Test
    fun `joinWordsWithLimit hard-truncates a single word that exceeds maxLen`() {
        val out = AgentTextUtils.joinWordsWithLimit(listOf("verylongword"), maxLen = 4)
        assertEquals("very", out)
    }

    @Test
    fun `joinWordsWithLimit hard-truncates when all words are too long`() {
        val out = AgentTextUtils.joinWordsWithLimit(listOf("alpha", "beta"), maxLen = 2)
        assertEquals("al", out)
    }
}
