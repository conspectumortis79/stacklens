package com.stacklens.domain.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentTextUtilsTest {
    @Test
    fun `cleanPath removes wrapping backticks and quotes`() {
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("`src/Foo.php`"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("'src/Foo.php'"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("\"src/Foo.php\""))
    }

    @Test
    fun `cleanPath strips trailing punctuation`() {
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("src/Foo.php:"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("src/Foo.php,"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("src/Foo.php;"))
    }

    @Test
    fun `cleanPath trims whitespace`() {
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("  src/Foo.php  "))
    }

    @Test
    fun `parseAgentOutput returns an empty list for blank input`() {
        assertTrue(AgentTextUtils.parseAgentOutput("").isEmpty())
    }

    @Test
    fun `parseAgentOutput returns an empty list for non-matching text`() {
        assertTrue(AgentTextUtils.parseAgentOutput("no file blocks here").isEmpty())
    }

    @Test
    fun `parseAgentOutput parses a single file block`() {
        val out = "### src/Foo.kt\n```kotlin\nfun foo() = 1\n```\n"
        val files = AgentTextUtils.parseAgentOutput(out)
        assertEquals(1, files.size)
        assertEquals("src/Foo.kt", files[0].path)
        assertEquals("fun foo() = 1\n", files[0].content)
    }

    @Test
    fun `parseAgentOutput parses multiple file blocks`() {
        val out =
            """
            ### a.kt
            ```
            A
            ```
            ### b.kt
            ```
            B
            ```
            """.trimIndent()
        val files = AgentTextUtils.parseAgentOutput(out)
        assertEquals(2, files.size)
        assertEquals("a.kt", files[0].path)
        assertEquals("b.kt", files[1].path)
    }

    @Test
    fun `parseAgentOutput filters out unsafe paths`() {
        val out =
            """
            ### a.kt
            ```
            A
            ```
            ### /abs.kt
            ```
            A
            ```
            ### ../up.kt
            ```
            A
            ```
            """.trimIndent()
        val files = AgentTextUtils.parseAgentOutput(out)
        assertEquals(1, files.size)
        assertEquals("a.kt", files[0].path)
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for blank input`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey(""))
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("  \n  "))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for fence-only input`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("```\n```"))
    }

    @Test
    fun `sanitiseDetectedTicketKey extracts the first plausible line`() {
        assertEquals("PROJ-1", AgentTextUtils.sanitiseDetectedTicketKey("PROJ-1"))
    }

    @Test
    fun `sanitiseDetectedTicketKey strips Output prefix`() {
        assertEquals("PROJ-1", AgentTextUtils.sanitiseDetectedTicketKey("Output: PROJ-1"))
        assertEquals("PROJ-1", AgentTextUtils.sanitiseDetectedTicketKey("output: PROJ-1"))
    }

    @Test
    fun `sanitiseDetectedTicketKey strips surrounding punctuation`() {
        assertEquals("PROJ-1", AgentTextUtils.sanitiseDetectedTicketKey("\"PROJ-1\""))
        assertEquals("PROJ-1", AgentTextUtils.sanitiseDetectedTicketKey("'PROJ-1'"))
        assertEquals("PROJ-1", AgentTextUtils.sanitiseDetectedTicketKey("(PROJ-1)"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for NONE`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("NONE"))
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("none"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for too-long content`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("x".repeat(81)))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for whitespace`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("hello world"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for path-traversal sequences`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("../etc"))
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("a/b"))
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("a\\b"))
    }

    @Test
    fun `generateFeatureSlug returns feature for blank input`() {
        assertEquals("feature", AgentTextUtils.generateFeatureSlug(""))
        assertEquals("feature", AgentTextUtils.generateFeatureSlug("   \n  "))
    }

    @Test
    fun `generateFeatureSlug takes up to 6 words`() {
        val slug = AgentTextUtils.generateFeatureSlug("one two three four five six seven")
        assertEquals(listOf("one", "two", "three", "four", "five", "six").joinToString("-"), slug)
    }

    @Test
    fun `generateFeatureSlug lowercases and strips non-alphanumerics`() {
        assertEquals("add-jwt-authentication", AgentTextUtils.generateFeatureSlug("Add JWT authentication!"))
    }

    @Test
    fun `generateFeatureSlug falls back to feature when no significant words remain`() {
        assertEquals("feature", AgentTextUtils.generateFeatureSlug("!@#\$"))
    }

    @Test
    fun `joinWordsWithLimit joins all words when under the limit`() {
        assertEquals("a-b-c", AgentTextUtils.joinWordsWithLimit(listOf("a", "b", "c"), 10))
    }

    @Test
    fun `joinWordsWithLimit stops at the word that would exceed the limit`() {
        assertEquals("aaa-bb", AgentTextUtils.joinWordsWithLimit(listOf("aaa", "bb", "cccc"), 7))
    }

    @Test
    fun `joinWordsWithLimit hard-truncates a single word longer than maxLen`() {
        assertEquals("abcdef", AgentTextUtils.joinWordsWithLimit(listOf("abcdefghij"), 6))
    }

    @Test
    fun `joinWordsWithLimit returns empty for empty input`() {
        assertEquals("", AgentTextUtils.joinWordsWithLimit(emptyList(), 5))
    }

    @Test
    fun `joinWordsWithLimit strips a trailing dash`() {
        assertEquals("abcd", AgentTextUtils.joinWordsWithLimit(listOf("abcd", "efgh"), 6))
    }

    @Test
    fun `extractSlugFromNoraSummary returns empty when no summary is present`() {
        assertEquals("", AgentTextUtils.extractSlugFromNoraSummary("no summary here"))
    }

    @Test
    fun `extractSlugFromNoraSummary extracts from bold Summary`() {
        val md = "**Summary**: Add user login\nMore text"
        assertEquals("add-user-login", AgentTextUtils.extractSlugFromNoraSummary(md))
    }

    @Test
    fun `extractSlugFromNoraSummary extracts from heading Summary`() {
        val md = "## Summary\nAdd user login\nMore text"
        assertEquals("add-user-login", AgentTextUtils.extractSlugFromNoraSummary(md))
    }

    @Test
    fun `parseCoveragePercent parses a valid coverage line`() {
        assertEquals(85.5, AgentTextUtils.parseCoveragePercent("Lines: 85.5%"))
    }

    @Test
    fun `parseCoveragePercent returns null for missing coverage line`() {
        assertNull(AgentTextUtils.parseCoveragePercent("no coverage here"))
    }

    @Test
    fun `parseCoveragePercent returns null for non-numeric value`() {
        assertNull(AgentTextUtils.parseCoveragePercent("Lines: not-a-number%"))
    }

    @Test
    fun `normaliseBadgeToFirstLine returns the input when already at the top`() {
        val input = "🧪\n\nrest"
        assertEquals(input, AgentTextUtils.normaliseBadgeToFirstLine(input))
    }

    @Test
    fun `normaliseBadgeToFirstLine moves a later badge line to position 0`() {
        val input = "Some preamble\n🚀\nrest"
        val result = AgentTextUtils.normaliseBadgeToFirstLine(input)
        assertTrue(result.startsWith("🚀"))
    }

    @Test
    fun `normaliseBadgeToFirstLine is a no-op when no badge is present`() {
        val input = "no badges here"
        assertEquals(input, AgentTextUtils.normaliseBadgeToFirstLine(input))
    }

    @Test
    fun `normaliseBadgeToFirstLine returns the input when it is all blank`() {
        val input = "   \n  \n"
        assertEquals(input, AgentTextUtils.normaliseBadgeToFirstLine(input))
    }
}
