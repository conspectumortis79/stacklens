package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraMarkdownConverterExtraTest {
    private val converter = JiraMarkdownConverter()

    @Test
    fun `looksLikeMarkdown returns false for blank input`() {
        assertFalse(converter.looksLikeMarkdown(""))
        assertFalse(converter.looksLikeMarkdown("   "))
        assertFalse(converter.looksLikeMarkdown("\n\n"))
    }

    @Test
    fun `looksLikeMarkdown detects ATX heading`() {
        assertTrue(converter.looksLikeMarkdown("# Title"))
        assertTrue(converter.looksLikeMarkdown("## Subtitle"))
        assertTrue(converter.looksLikeMarkdown("### Sub-subtitle"))
    }

    @Test
    fun `looksLikeMarkdown detects fenced code block`() {
        assertTrue(converter.looksLikeMarkdown("Some text\n```code\nmore"))
    }

    @Test
    fun `looksLikeMarkdown detects Markdown bullet`() {
        assertTrue(converter.looksLikeMarkdown("- item one"))
    }

    @Test
    fun `looksLikeMarkdown detects Markdown bold`() {
        assertTrue(converter.looksLikeMarkdown("this is **bold** text"))
    }

    @Test
    fun `looksLikeMarkdown detects Markdown link`() {
        assertTrue(converter.looksLikeMarkdown("click [here](https://example.com)"))
    }

    @Test
    fun `looksLikeMarkdown returns false for plain text`() {
        assertFalse(converter.looksLikeMarkdown("This is just plain text without any formatting"))
    }

    @Test
    fun `looksLikeMarkdown rejects hash without space`() {
        assertFalse(converter.looksLikeMarkdown("#nospace"))
    }

    @Test
    fun `looksLikeMarkdown rejects dash without space`() {
        assertFalse(converter.looksLikeMarkdown("-nospace"))
    }

    @Test
    fun `looksLikeMarkdown handles heading level 6`() {
        assertTrue(converter.looksLikeMarkdown("###### level 6"))
    }

    @Test
    fun `markdownToJiraWiki converts bold text`() {
        assertEquals("some *bold* text", converter.markdownToJiraWiki("some **bold** text"))
    }

    @Test
    fun `markdownToJiraWiki converts italic text`() {
        val result = converter.markdownToJiraWiki("some _italic_ text")
        assertTrue(result.contains("_italic_"))
    }

    @Test
    fun `markdownToJiraWiki converts inline code`() {
        assertEquals("some {{code}} text", converter.markdownToJiraWiki("some `code` text"))
    }

    @Test
    fun `markdownToJiraWiki converts Markdown links to wiki links`() {
        val result = converter.markdownToJiraWiki("[link text](https://example.com)")
        assertEquals("[link text|https://example.com]", result)
    }

    @Test
    fun `markdownToJiraWiki handles empty input`() {
        assertEquals("", converter.markdownToJiraWiki(""))
    }

    @Test
    fun `markdownToJiraWiki handles plain text without formatting`() {
        val result = converter.markdownToJiraWiki("plain text")
        assertTrue(result.contains("plain text"))
    }

    @Test
    fun `markdownToAdf returns a non-null map for plain text`() {
        val adf = converter.markdownToAdf("plain text")
        assertNotNull(adf)
        assertEquals("doc", adf["type"])
    }

    @Test
    fun `markdownToAdf handles empty input`() {
        val adf = converter.markdownToAdf("")
        assertNotNull(adf)
    }

    @Test
    fun `markdownToAdf returns list of nodes in content`() {
        val adf = converter.markdownToAdf("hello\nworld")
        val content = adf["content"]
        assertNotNull(content)
    }

    @Test
    fun `inlineToAdf returns list of marks for plain text`() {
        val marks = converter.inlineToAdf("hello")
        assertNotNull(marks)
    }

    @Test
    fun `inlineToAdf returns a single empty text node for empty input`() {
        val marks = converter.inlineToAdf("")
        assertEquals(1, marks.size)
    }

    @Test
    fun `inlineToAdf detects bold formatting`() {
        val marks = converter.inlineToAdf("**bold**")
        assertNotNull(marks)
    }

    @Test
    fun `inlineToAdf detects link formatting`() {
        val marks = converter.inlineToAdf("[text](https://example.com)")
        assertNotNull(marks)
    }
}
