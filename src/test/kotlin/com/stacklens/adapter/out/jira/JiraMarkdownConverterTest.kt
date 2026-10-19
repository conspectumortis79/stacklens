package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraMarkdownConverterTest {
    private val converter = JiraMarkdownConverter()

    @Test
    fun `looksLikeMarkdown false for blank`() {
        assertFalse(converter.looksLikeMarkdown(""))
        assertFalse(converter.looksLikeMarkdown("   \n  "))
    }

    @Test
    fun `looksLikeMarkdown true for ATX heading`() {
        assertTrue(converter.looksLikeMarkdown("# Title"))
        assertTrue(converter.looksLikeMarkdown("## Heading 2"))
        assertTrue(converter.looksLikeMarkdown("###### h6"))
    }

    @Test
    fun `looksLikeMarkdown true for fenced code`() {
        assertTrue(converter.looksLikeMarkdown("``` kotlin\nfun main() {}"))
    }

    @Test
    fun `looksLikeMarkdown true for bullet list`() {
        assertTrue(converter.looksLikeMarkdown("- item"))
    }

    @Test
    fun `looksLikeMarkdown true for bold`() {
        assertTrue(converter.looksLikeMarkdown("this is **bold** text"))
    }

    @Test
    fun `looksLikeMarkdown true for link`() {
        assertTrue(converter.looksLikeMarkdown("see [docs](https://example.com)"))
    }

    @Test
    fun `looksLikeMarkdown false for plain wiki`() {
        assertFalse(converter.looksLikeMarkdown("h2. Just a normal line"))
    }

    @Test
    fun `markdownToJiraWiki h3 heading`() {
        val result = converter.markdownToJiraWiki("### Ziel\n\nText")
        assertTrue(result.contains("h3. Ziel"))
    }

    @Test
    fun `markdownToJiraWiki h2 heading`() {
        val result = converter.markdownToJiraWiki("## Ziel\nText")
        assertTrue(result.contains("h2. Ziel"))
    }

    @Test
    fun `markdownToJiraWiki h1 heading treated as h2`() {
        val result = converter.markdownToJiraWiki("# Ziel")
        assertTrue(result.contains("h2. Ziel"))
    }

    @Test
    fun `markdownToJiraWiki horizontal rule`() {
        val result = converter.markdownToJiraWiki("---")
        assertTrue(result.contains("----"))
    }

    @Test
    fun `markdownToJiraWiki horizontal rule stars`() {
        val result = converter.markdownToJiraWiki("***")
        assertTrue(result.contains("----"))
    }

    @Test
    fun `markdownToJiraWiki blockquote`() {
        val result = converter.markdownToJiraWiki("> quoted text")
        assertTrue(result.contains("{quote}quoted text{quote}"))
    }

    @Test
    fun `markdownToJiraWiki bullet list`() {
        val result = converter.markdownToJiraWiki("- one\n- two")
        assertTrue(result.contains("* one"))
        assertTrue(result.contains("* two"))
    }

    @Test
    fun `markdownToJiraWiki bullet list with star marker`() {
        val result = converter.markdownToJiraWiki("* one\n* two")
        assertTrue(result.contains("* one"))
        assertTrue(result.contains("* two"))
    }

    @Test
    fun `markdownToJiraWiki ordered list`() {
        val result = converter.markdownToJiraWiki("1. first\n2. second")
        assertTrue(result.contains("# first"))
        assertTrue(result.contains("# second"))
    }

    @Test
    fun `markdownToJiraWiki blank line flushes`() {
        val result = converter.markdownToJiraWiki("- a\n\npara")
        assertTrue(result.contains("* a"))
        assertTrue(result.contains("para"))
    }

    @Test
    fun `markdownToJiraWiki inline formatting`() {
        val result = converter.markdownToJiraWiki("text with **bold** and `code`")
        assertTrue(result.contains("*bold*"))
        assertTrue(result.contains("{{code}}"))
    }

    @Test
    fun `markdownToJiraWiki inline link`() {
        val result = converter.markdownToJiraWiki("see [text](https://ex.com)")
        assertTrue(result.contains("[text|https://ex.com]"))
    }

    @Test
    fun `markdownToAdf h3 heading`() {
        val doc = converter.markdownToAdf("### Ziel")
        assertEquals("doc", doc["type"])
        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals(1, content.size)
        assertEquals("heading", content[0]["type"])
        @Suppress("UNCHECKED_CAST")
        val attrs = content[0]["attrs"] as Map<String, Int>
        assertEquals(3, attrs["level"])
    }

    @Test
    fun `markdownToAdf h2 heading`() {
        val doc = converter.markdownToAdf("## Ziel")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals("heading", content[0]["type"])
        @Suppress("UNCHECKED_CAST")
        val attrs = content[0]["attrs"] as Map<String, Int>
        assertEquals(2, attrs["level"])
    }

    @Test
    fun `markdownToAdf rule`() {
        val doc = converter.markdownToAdf("---")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals("rule", content[0]["type"])
    }

    @Test
    fun `markdownToAdf bullet list`() {
        val doc = converter.markdownToAdf("- one\n- two")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals(1, content.size)
        assertEquals("bulletList", content[0]["type"])
    }

    @Test
    fun `markdownToAdf blank line flushes bullets`() {
        val doc = converter.markdownToAdf("- a\n\npara")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals(2, content.size)
        assertEquals("bulletList", content[0]["type"])
        assertEquals("paragraph", content[1]["type"])
    }

    @Test
    fun `markdownToAdf paragraph`() {
        val doc = converter.markdownToAdf("hello world")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals("paragraph", content[0]["type"])
    }

    @Test
    fun `markdownToAdf empty input produces empty paragraph`() {
        val doc = converter.markdownToAdf("")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals(1, content.size)
        assertEquals("paragraph", content[0]["type"])
    }

    @Test
    fun `markdownToAdf mixed content`() {
        val doc = converter.markdownToAdf("## H2\n\n- item\n\npara")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertTrue(content.size >= 3)
    }

    @Test
    fun `markdownToAdf star bullet also works`() {
        val doc = converter.markdownToAdf("* item")

        @Suppress("UNCHECKED_CAST")
        val content = doc["content"] as List<Map<String, Any>>
        assertEquals("bulletList", content[0]["type"])
    }

    @Test
    fun `inlineToAdf plain text`() {
        val result = converter.inlineToAdf("hello")
        assertEquals(1, result.size)
        assertEquals("text", result[0]["type"])
        assertEquals("hello", result[0]["text"])
    }

    @Test
    fun `inlineToAdf bold text`() {
        val result = converter.inlineToAdf("**bold**")
        assertEquals(1, result.size)
        @Suppress("UNCHECKED_CAST")
        val marks = result[0]["marks"] as List<Map<String, String>>
        assertEquals("strong", marks[0]["type"])
    }

    @Test
    fun `inlineToAdf mixed text`() {
        val result = converter.inlineToAdf("before **bold** after")
        assertEquals(3, result.size)
    }

    @Test
    fun `inlineToAdf empty returns a single empty text node`() {
        val result = converter.inlineToAdf("")
        assertEquals(1, result.size)
        assertEquals("text", result[0]["type"])
        assertEquals("", result[0]["text"])
    }

    @Test
    fun `inlineToAdf only non-bold returns single plain node`() {
        val result = converter.inlineToAdf("just text")
        assertEquals(1, result.size)
        assertEquals("just text", result[0]["text"])
    }
}
