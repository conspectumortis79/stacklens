package com.stacklens.domain.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentTextUtilsExtraTest {
    @Test
    fun `normaliseBadgeToFirstLine returns original when no badge present`() {
        val output = "Some text without any badge emoji"
        assertEquals(output, AgentTextUtils.normaliseBadgeToFirstLine(output))
    }

    @Test
    fun `normaliseBadgeToFirstLine returns original when output is blank`() {
        assertEquals("", AgentTextUtils.normaliseBadgeToFirstLine(""))
    }

    @Test
    fun `normaliseBadgeToFirstLine returns original when only whitespace`() {
        assertEquals("   \n  ", AgentTextUtils.normaliseBadgeToFirstLine("   \n  "))
    }

    @Test
    fun `normaliseBadgeToFirstLine returns original when badge is on first non-blank line`() {
        val output = "🧪 some text here\nmore text"
        assertEquals(output, AgentTextUtils.normaliseBadgeToFirstLine(output))
    }

    @Test
    fun `normaliseBadgeToFirstLine moves badge to front when on later line`() {
        val output = "Some preamble\nExplanation line\n🚀 badge line"
        val normalized = AgentTextUtils.normaliseBadgeToFirstLine(output)
        assertTrue(normalized.startsWith("🚀"))
    }

    @Test
    fun `normaliseBadgeToFirstLine handles each badge emoji`() {
        listOf("🧪", "🚀", "🔀", "🗑️").forEach { badge ->
            val output = "preamble\n$badge badge"
            val normalized = AgentTextUtils.normaliseBadgeToFirstLine(output)
            assertTrue(normalized.startsWith(badge), "Expected $badge to be moved to front")
        }
    }

    @Test
    fun `joinWordsWithLimit returns empty when words empty`() {
        assertEquals("", AgentTextUtils.joinWordsWithLimit(emptyList(), 10))
    }

    @Test
    fun `joinWordsWithLimit returns single word unchanged when fits`() {
        assertEquals("hello", AgentTextUtils.joinWordsWithLimit(listOf("hello"), 10))
    }

    @Test
    fun `joinWordsWithLimit truncates first word when it exceeds maxLen`() {
        val result = AgentTextUtils.joinWordsWithLimit(listOf("verylongword"), 5)
        assertEquals("veryl", result)
    }

    @Test
    fun `joinWordsWithLimit joins multiple words with dashes`() {
        assertEquals("foo-bar-baz", AgentTextUtils.joinWordsWithLimit(listOf("foo", "bar", "baz"), 20))
    }

    @Test
    fun `joinWordsWithLimit stops adding words when exceeding maxLen`() {
        val result = AgentTextUtils.joinWordsWithLimit(listOf("aaa", "bbb", "cccc"), 7)
        assertEquals("aaa-bbb", result)
    }

    @Test
    fun `joinWordsWithLimit strips trailing dash from final result`() {
        val result = AgentTextUtils.joinWordsWithLimit(listOf("ab", "cd", "ef"), 5)
        assertEquals("ab-cd", result)
    }

    @Test
    fun `parseAgentOutput filters paths starting with slash`() {
        val output = "### /etc/passwd\n```\ncontent\n```"
        val parsed = AgentTextUtils.parseAgentOutput(output)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parseAgentOutput filters paths with traversal sequences`() {
        val output = "### ../etc/passwd\n```\ncontent\n```"
        val parsed = AgentTextUtils.parseAgentOutput(output)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parseAgentOutput returns valid parsed files`() {
        val output = "### src/Foo.kt\n```kotlin\nclass Foo\n```"
        val parsed = AgentTextUtils.parseAgentOutput(output)
        assertEquals(1, parsed.size)
        assertEquals("src/Foo.kt", parsed[0].path)
        assertTrue(parsed[0].content.contains("class Foo"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for blank input`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey(""))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for whitespace only`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("   \n   "))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for fence-only input`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("```\n```"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for NONE`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("NONE"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for overly long string`() {
        val longKey = "A".repeat(100)
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey(longKey))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for paths with slashes`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("path/to/branch"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for traversal sequence`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("a../b"))
    }

    @Test
    fun `sanitiseDetectedTicketKey strips Output prefix`() {
        assertEquals(
            "JIRA-123",
            AgentTextUtils.sanitiseDetectedTicketKey("Output: JIRA-123"),
        )
    }

    @Test
    fun `sanitiseDetectedTicketKey strips surrounding punctuation`() {
        assertEquals(
            "JIRA-123",
            AgentTextUtils.sanitiseDetectedTicketKey("\"JIRA-123\"."),
        )
    }

    @Test
    fun `sanitiseDetectedTicketKey returns valid ticket key`() {
        assertEquals("PROJ-456", AgentTextUtils.sanitiseDetectedTicketKey("PROJ-456"))
    }

    @Test
    fun `generateFeatureSlug returns feature for empty input`() {
        assertEquals("feature", AgentTextUtils.generateFeatureSlug(""))
    }

    @Test
    fun `generateFeatureSlug returns feature for whitespace-only input`() {
        assertEquals("feature", AgentTextUtils.generateFeatureSlug("   \n   "))
    }

    @Test
    fun `generateFeatureSlug takes first 6 words`() {
        val result = AgentTextUtils.generateFeatureSlug("one two three four five six seven eight")
        assertEquals("one-two-three-four-five-six", result)
    }

    @Test
    fun `generateFeatureSlug strips non-alphanumeric chars`() {
        val result = AgentTextUtils.generateFeatureSlug("Hello, World!")
        assertEquals("hello-world", result)
    }

    @Test
    fun `parseCoveragePercent returns null for missing pattern`() {
        assertNull(AgentTextUtils.parseCoveragePercent("no coverage info"))
    }

    @Test
    fun `parseCoveragePercent parses valid percentage`() {
        assertEquals(85.5, AgentTextUtils.parseCoveragePercent("Lines: 85.5%"))
    }

    @Test
    fun `parseCoveragePercent handles zero percent`() {
        assertEquals(0.0, AgentTextUtils.parseCoveragePercent("Lines: 0%"))
    }

    @Test
    fun `parseCoveragePercent handles 100 percent`() {
        assertEquals(100.0, AgentTextUtils.parseCoveragePercent("Lines: 100%"))
    }

    @Test
    fun `extractSlugFromNoraSummary returns empty for input without summary`() {
        assertEquals("", AgentTextUtils.extractSlugFromNoraSummary("just some text"))
    }

    @Test
    fun `extractSlugFromNoraSummary handles bold summary`() {
        val output = "**Summary**: Add user authentication feature"
        val slug = AgentTextUtils.extractSlugFromNoraSummary(output)
        assertEquals("add-user-authentication-feature", slug)
    }

    @Test
    fun `extractSlugFromNoraSummary handles header summary`() {
        val output = "## Summary\nFix broken login flow"
        val slug = AgentTextUtils.extractSlugFromNoraSummary(output)
        assertEquals("fix-broken-login-flow", slug)
    }

    @Test
    fun `cleanPath strips backticks`() {
        assertEquals("src/Foo.kt", AgentTextUtils.cleanPath("`src/Foo.kt`"))
    }

    @Test
    fun `cleanPath strips trailing punctuation`() {
        assertEquals("src/Foo.kt", AgentTextUtils.cleanPath("src/Foo.kt:"))
    }

    @Test
    fun `cleanPath strips quotes`() {
        assertEquals("src/Foo.kt", AgentTextUtils.cleanPath("\"src/Foo.kt\""))
    }

    @Test
    fun `cleanPath strips single quotes`() {
        assertEquals("src/Foo.kt", AgentTextUtils.cleanPath("'src/Foo.kt'"))
    }

    @Test
    fun `cleanPath returns empty for whitespace-only input`() {
        assertEquals("", AgentTextUtils.cleanPath("   "))
    }

    @Test
    fun `ParsedFile stores path and content`() {
        val pf = AgentTextUtils.ParsedFile("foo.kt", "class Foo")
        assertEquals("foo.kt", pf.path)
        assertEquals("class Foo", pf.content)
    }

    @Test
    fun `ParsedFile equality holds by value`() {
        val a = AgentTextUtils.ParsedFile("foo.kt", "x")
        val b = AgentTextUtils.ParsedFile("foo.kt", "x")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `agentOutputBlockRe matches basic pattern`() {
        val match = AgentTextUtils.agentOutputBlockRe.find("### foo.kt\n```kotlin\ncontent\n```")
        assertNotNull(match)
        assertEquals("foo.kt", match!!.groupValues[1])
        assertEquals("content\n", match.groupValues[2])
    }

    @Test
    fun `agentOutputBlockRe matches multiple blocks`() {
        val output =
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
        val matches = AgentTextUtils.agentOutputBlockRe.findAll(output).toList()
        assertEquals(2, matches.size)
    }
}
