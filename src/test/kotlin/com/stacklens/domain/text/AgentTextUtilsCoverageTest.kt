package com.stacklens.domain.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentTextUtilsCoverageTest {
    @Test
    fun `cleanPath strips backticks quotes colons commas semicolons and whitespace`() {
        assertEquals("### `src/Foo.php`", AgentTextUtils.cleanPath("### `src/Foo.php`:"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("`src/Foo.php`"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("\"src/Foo.php\""))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("'src/Foo.php'"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("  src/Foo.php  "))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("src/Foo.php,"))
        assertEquals("src/Foo.php", AgentTextUtils.cleanPath("src/Foo.php;"))
    }

    @Test
    fun `parseAgentOutput returns empty list for blank input`() {
        assertTrue(AgentTextUtils.parseAgentOutput("").isEmpty())
    }

    @Test
    fun `parseAgentOutput extracts one file per block`() {
        val text =
            "### src/Foo.kt\n```kotlin\nfun foo() = 1\n```\n" +
                "### src/Bar.kt\n```kotlin\nfun bar() = 2\n```\n"
        val files = AgentTextUtils.parseAgentOutput(text)
        assertEquals(2, files.size)
        assertEquals("src/Foo.kt", files[0].path)
        assertEquals("fun foo() = 1\n", files[0].content)
        assertEquals("src/Bar.kt", files[1].path)
    }

    @Test
    fun `parseAgentOutput skips absolute paths`() {
        val text = "### /etc/passwd\n```\nroot\n```\n"
        assertTrue(AgentTextUtils.parseAgentOutput(text).isEmpty())
    }

    @Test
    fun `parseAgentOutput skips path traversal entries`() {
        val text = "### ../escape.kt\n```\nx\n```\n### sub/ok.kt\n```\ny\n```\n"
        val files = AgentTextUtils.parseAgentOutput(text)
        assertEquals(1, files.size)
        assertEquals("sub/ok.kt", files[0].path)
    }

    @Test
    fun `parseAgentOutput skips blocks with blank paths`() {
        val text = "###  \n```\nfoo\n```\n"
        assertTrue(AgentTextUtils.parseAgentOutput(text).isEmpty())
    }

    @Test
    fun `generateFeatureSlug produces a lowercased hyphen-joined slug`() {
        val slug = AgentTextUtils.generateFeatureSlug("Add JWT authentication for admin users")
        assertEquals("add-jwt-authentication-for-admin-users", slug)
    }

    @Test
    fun `generateFeatureSlug strips non-alphanumeric characters`() {
        val slug = AgentTextUtils.generateFeatureSlug("Fix: Bug in foo() / bar.class!")
        assertEquals("fix-bug-in-foo-barclass", slug)
    }

    @Test
    fun `generateFeatureSlug returns feature fallback for blank input`() {
        assertEquals("feature", AgentTextUtils.generateFeatureSlug(""))
        assertEquals("feature", AgentTextUtils.generateFeatureSlug("   "))
        assertEquals("feature", AgentTextUtils.generateFeatureSlug("---"))
    }

    @Test
    fun `generateFeatureSlug truncates long slugs`() {
        val longText = (1..50).joinToString(" ") { "word$it" }
        val slug = AgentTextUtils.generateFeatureSlug(longText)
        assertTrue(slug.length <= 60)
    }

    @Test
    fun `joinWordsWithLimit joins with hyphen and stays within limit`() {
        assertEquals("a-b-c", AgentTextUtils.joinWordsWithLimit(listOf("a", "b", "c"), 10))
    }

    @Test
    fun `joinWordsWithLimit returns empty for empty list`() {
        assertEquals("", AgentTextUtils.joinWordsWithLimit(emptyList(), 10))
    }

    @Test
    fun `joinWordsWithLimit hard-truncates a single oversized word`() {
        val out = AgentTextUtils.joinWordsWithLimit(listOf("abcdefghij"), 5)
        assertEquals("abcde", out)
    }

    @Test
    fun `joinWordsWithLimit stops when the next word would overflow`() {
        val out = AgentTextUtils.joinWordsWithLimit(listOf("abc", "def", "ghi"), 8)
        assertEquals("abc-def", out)
    }

    @Test
    fun `extractSlugFromNoraSummary reads the bold Summary line`() {
        val nora = "**Summary**: Add JWT authentication for admin users\nBody"
        val slug = AgentTextUtils.extractSlugFromNoraSummary(nora)
        assertEquals("add-jwt-authentication-for-admin-users", slug)
    }

    @Test
    fun `extractSlugFromNoraSummary reads the heading Summary line`() {
        val nora = "## Summary\nAdd JWT authentication for admin users\n"
        val slug = AgentTextUtils.extractSlugFromNoraSummary(nora)
        assertEquals("add-jwt-authentication-for-admin-users", slug)
    }

    @Test
    fun `extractSlugFromNoraSummary returns empty when no summary is present`() {
        assertEquals("", AgentTextUtils.extractSlugFromNoraSummary("Just some random text"))
        assertEquals("", AgentTextUtils.extractSlugFromNoraSummary(""))
    }

    @Test
    fun `parseCoveragePercent extracts a percentage value`() {
        val out = "PHPUnit 10.5.0 by Sebastian Bergmann and contributors.\n...\nLines: 87.42%\n"
        assertEquals(87.42, AgentTextUtils.parseCoveragePercent(out))
    }

    @Test
    fun `parseCoveragePercent returns null when no Lines entry is present`() {
        assertNull(AgentTextUtils.parseCoveragePercent("no coverage info here"))
    }

    @Test
    fun `normaliseBadgeToFirstLine leaves the input unchanged when badge is already first`() {
        val input = "🧪 Tess output line"
        assertEquals(input, AgentTextUtils.normaliseBadgeToFirstLine(input))
    }

    @Test
    fun `normaliseBadgeToFirstLine moves the badge line to position 0`() {
        val input = "Let me think...\n\n🧪 Tess output"
        val out = AgentTextUtils.normaliseBadgeToFirstLine(input)
        val first = out.lineSequence().first { it.isNotBlank() }
        assertTrue("🧪" in first)
    }

    @Test
    fun `normaliseBadgeToFirstLine returns input unchanged when no badge is present`() {
        val input = "Just some prose without any badge at all."
        assertEquals(input, AgentTextUtils.normaliseBadgeToFirstLine(input))
    }

    @Test
    fun `normaliseBadgeToFirstLine returns input unchanged when blank`() {
        assertEquals("", AgentTextUtils.normaliseBadgeToFirstLine(""))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns the ticket key when on its own line`() {
        assertEquals("PROJ-123", AgentTextUtils.sanitiseDetectedTicketKey("PROJ-123"))
        assertEquals("PROJ-123", AgentTextUtils.sanitiseDetectedTicketKey("\nPROJ-123\n"))
    }

    @Test
    fun `sanitiseDetectedTicketKey strips code fences and output prefixes`() {
        assertEquals("PROJ-123", AgentTextUtils.sanitiseDetectedTicketKey("```\nPROJ-123\n```"))
        assertEquals("PROJ-123", AgentTextUtils.sanitiseDetectedTicketKey("Output: PROJ-123"))
    }

    @Test
    fun `sanitiseDetectedTicketKey rejects path traversal and directory separators`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("../escape"))
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("path/to/x"))
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("with\\backslash"))
    }

    @Test
    fun `sanitiseDetectedTicketKey rejects overly long entries`() {
        val longKey = "A".repeat(100)
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey(longKey))
    }

    @Test
    fun `sanitiseDetectedTicketKey rejects entries with whitespace`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("PROJ 123"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns null for blank or no-match input`() {
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey(""))
        assertNull(AgentTextUtils.sanitiseDetectedTicketKey("nothing matching at all"))
    }

    @Test
    fun `sanitiseDetectedTicketKey returns the first non-blank non-fence line`() {
        assertEquals("PROJ-42", AgentTextUtils.sanitiseDetectedTicketKey("PROJ-42\nthis is more prose"))
        assertEquals("PROJ-42", AgentTextUtils.sanitiseDetectedTicketKey("\n\nPROJ-42"))
    }

    @Test
    fun `agentOutputBlockRe is exposed`() {
        assertNotNull(AgentTextUtils.agentOutputBlockRe)
    }

    @Test
    fun `joinWordsWithLimit keeps an empty leading word instead of truncating`() {
        assertEquals("", AgentTextUtils.joinWordsWithLimit(listOf(""), 5))
    }

    @Test
    fun `extractSlugFromNoraSummary drops words without alphanumerics`() {
        assertEquals("a", AgentTextUtils.extractSlugFromNoraSummary("**Summary**: a !!!"))
    }

    @Test
    fun `extractSlugFromNoraSummary drops blank pieces produced by the whitespace split`() {
        assertEquals("", AgentTextUtils.extractSlugFromNoraSummary("**Summary**:\u00A0"))
    }
}
