package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdfToHtmlConverterTest {
    private val converter = AdfToHtmlConverter(jacksonObjectMapper())

    private fun adf(
        type: String,
        content: Any? = null,
        attrs: Map<String, Any>? = null,
    ): String {
        val body = mutableMapOf<String, Any>("type" to type)
        if (content != null) body["content"] = content
        if (attrs != null) body["attrs"] = attrs
        return jacksonObjectMapper().writeValueAsString(body)
    }

    @Test
    fun `render returns null for null and blank input`() {
        assertNull(converter.render(null))
        assertNull(converter.render(""))
        assertNull(converter.render("   "))
    }

    @Test
    fun `render returns null for unparseable JSON`() {
        assertNull(converter.render("not valid json"))
    }

    @Test
    fun `render of empty doc returns null`() {
        val html = converter.render(adf("doc"))
        assertNull(html)
    }

    @Test
    fun `render of doc with paragraph uses sp-jira-p class`() {
        val html = converter.render(adf("doc", listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "Hello"))))))
        assertNotNull(html)
        assertTrue(html!!.contains("Hello"))
        assertTrue(html.contains("sp-jira-p"))
    }

    @Test
    fun `render of heading defaults to level 2`() {
        val html = converter.render(adf("heading", listOf(mapOf("type" to "text", "text" to "Hi"))))
        assertNotNull(html)
        assertTrue(html!!.contains("sp-jira-h2"))
    }

    @Test
    fun `render of heading level 3 uses sp-jira-h3`() {
        val html = converter.render(adf("heading", listOf(mapOf("type" to "text", "text" to "Hi")), mapOf("level" to 3)))
        assertTrue(html!!.contains("sp-jira-h3"))
    }

    @Test
    fun `render of heading level 5 still uses h3 class`() {
        val html = converter.render(adf("heading", listOf(mapOf("type" to "text", "text" to "Hi")), mapOf("level" to 5)))
        assertTrue(html!!.contains("sp-jira-h3"))
    }

    @Test
    fun `render of bulletList uses sp-jira-ul`() {
        val html =
            converter.render(
                adf("bulletList", listOf(mapOf("type" to "listItem", "content" to listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "x"))))))),
            )
        assertTrue(html!!.contains("sp-jira-ul"))
        assertTrue(html.contains("<li>"))
    }

    @Test
    fun `render of orderedList uses sp-jira-ol`() {
        val html =
            converter.render(
                adf("orderedList", listOf(mapOf("type" to "listItem", "content" to listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "x"))))))),
            )
        assertTrue(html!!.contains("sp-jira-ol"))
    }

    @Test
    fun `render of rule uses sp-jira-hr`() {
        val html = converter.render(adf("rule"))
        assertTrue(html!!.contains("sp-jira-hr"))
    }

    @Test
    fun `render of codeBlock without language omits data-lang`() {
        val html = converter.render(adf("codeBlock", listOf(mapOf("type" to "text", "text" to "x"))))
        assertTrue(html!!.contains("sp-jira-codeblock"))
        assertTrue(!html.contains("data-lang="))
    }

    @Test
    fun `render of codeBlock with language includes data-lang`() {
        val html = converter.render(adf("codeBlock", listOf(mapOf("type" to "text", "text" to "x")), mapOf("language" to "kotlin")))
        assertTrue(html!!.contains("data-lang=\"kotlin\""))
    }

    @Test
    fun `render of blockquote uses sp-jira-bq`() {
        val html = converter.render(adf("blockquote", listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "quote"))))))
        assertTrue(html!!.contains("sp-jira-bq"))
        assertTrue(html.contains("quote"))
    }

    @Test
    fun `render of panel uses the panelType in the class`() {
        val html = converter.render(adf("panel", listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "x")))), mapOf("panelType" to "warning")))
        assertTrue(html!!.contains("sp-jira-panel--warning"))
    }

    @Test
    fun `render of panel defaults to info class`() {
        val html = converter.render(adf("panel", listOf(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "x"))))))
        assertTrue(html!!.contains("sp-jira-panel--info"))
    }

    @Test
    fun `render of table uses sp-jira-table class`() {
        val html =
            converter.render(
                adf(
                    "table",
                    listOf(
                        mapOf(
                            "type" to "tableRow",
                            "content" to
                                listOf(
                                    mapOf("type" to "tableHeader", "content" to listOf(mapOf("type" to "text", "text" to "h"))),
                                    mapOf("type" to "tableCell", "content" to listOf(mapOf("type" to "text", "text" to "c"))),
                                ),
                        ),
                    ),
                ),
            )
        assertTrue(html!!.contains("sp-jira-table"))
        assertTrue(html.contains("sp-jira-th"))
        assertTrue(html.contains("sp-jira-td"))
    }

    @Test
    fun `render of mediaSingle outputs a placeholder`() {
        val html = converter.render(adf("mediaSingle"))
        assertTrue(html!!.contains("[Bild/Anhang]"))
    }

    @Test
    fun `render of text with strong mark wraps in strong`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "strong"))))
        assertTrue(html!!.contains("<strong>"))
    }

    @Test
    fun `render of text with em mark wraps in em`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "em"))))
        assertTrue(html!!.contains("<em>"))
    }

    @Test
    fun `render of text with code mark wraps in sp-jira-code`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "code"))))
        assertTrue(html!!.contains("sp-jira-code"))
    }

    @Test
    fun `render of text with underline mark adds text-decoration`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "underline"))))
        assertTrue(html!!.contains("text-decoration:underline"))
    }

    @Test
    fun `render of text with strike mark wraps in s`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "strike"))))
        assertTrue(html!!.contains("<s>"))
    }

    @Test
    fun `render of text with subsup sub wraps in sub`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "subsup", "attrs" to mapOf("type" to "sub")))))
        assertTrue(html!!.contains("<sub>"))
    }

    @Test
    fun `render of text with subsup sup wraps in sup`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "subsup", "attrs" to mapOf("type" to "sup")))))
        assertTrue(html!!.contains("<sup>"))
    }

    @Test
    fun `render of text with textColor mark uses the colour in style`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "textColor", "attrs" to mapOf("color" to "red")))))
        assertTrue(html!!.contains("color:red"))
    }

    @Test
    fun `render of text with link mark renders the href`() {
        val html = converter.render(wrapInDoc("text", text = "x", marks = listOf(mapOf("type" to "link", "attrs" to mapOf("href" to "https://example.com")))))
        assertTrue(html!!.contains("https://example.com"))
    }

    @Test
    fun `render of hardBreak outputs br`() {
        val html = converter.render(wrapInDoc("hardBreak"))
        assertTrue(html!!.contains("<br>"))
    }

    @Test
    fun `render of mention outputs the @handle in a span`() {
        val html = converter.render(wrapInDoc("mention", attrs = mapOf("text" to "alice")))
        assertTrue(html!!.contains("@alice"))
    }

    @Test
    fun `render of mention falls back to id when text is blank`() {
        val html = converter.render(wrapInDoc("mention", attrs = mapOf("id" to "u-42")))
        assertTrue(html!!.contains("@u-42"))
    }

    @Test
    fun `render of emoji prefers text over shortName`() {
        val html = converter.render(wrapInDoc("emoji", attrs = mapOf("text" to "😀", "shortName" to ":grinning:")))
        assertTrue(html!!.contains("😀"))
    }

    @Test
    fun `render of emoji falls back to shortName when text is blank`() {
        val html = converter.render(wrapInDoc("emoji", attrs = mapOf("shortName" to ":grinning:")))
        assertTrue(html!!.contains(":grinning:"))
    }

    @Test
    fun `render of inlineCard renders the URL as a link`() {
        val html = converter.render(wrapInDoc("inlineCard", attrs = mapOf("url" to "https://example.com")))
        assertTrue(html!!.contains("https://example.com"))
    }

    @Test
    fun `render escapes HTML special characters in text`() {
        val html = converter.render(wrapInDoc("text", text = "<script>alert(1)</script>"))
        assertTrue(html!!.contains("&lt;script&gt;"))
        assertTrue(!html.contains("<script>"))
    }

    @Test
    fun `safeUrl allows http, https, and relative paths`() {
        listOf("http://a", "https://b", "/c", "#d", "./e", "../f", "").forEach {
            val html = converter.render(wrapInDoc("inlineCard", attrs = mapOf("url" to it)))
            assertNotNull(html)
        }
    }

    @Test
    fun `safeUrl blocks javascript and data URLs`() {
        val html = converter.render(wrapInDoc("inlineCard", attrs = mapOf("url" to "javascript:alert(1)")))
        assertTrue(html!!.contains("href=\"#\""))
    }

    @Test
    fun `render of an unknown inline type falls back to rendering its content`() {
        val html =
            converter.render(
                wrapInDoc(
                    "unknownInline",
                    content = listOf(mapOf("type" to "text", "text" to "x")),
                ),
            )
        assertTrue(html!!.contains("x"))
    }

    @Test
    fun `render of an unknown block type with no content returns null`() {
        val html = converter.render(adf("unknownBlock"))
        assertNull(html)
    }

    @Test
    fun `render of doc with multiple paragraphs concatenates the paragraphs`() {
        val html =
            converter.render(
                adf(
                    "doc",
                    listOf(
                        mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "A"))),
                        mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "B"))),
                    ),
                ),
            )
        assertTrue(html!!.contains("A"))
        assertTrue(html.contains("B"))
    }

    @Test
    fun `render of empty paragraph inside doc is skipped`() {
        val html =
            converter.render(
                adf("doc", listOf(mapOf("type" to "paragraph", "content" to emptyList<Map<String, Any>>()))),
            )
        assertNull(html)
    }

    @Test
    fun `render of listItem with single paragraph is flattened`() {
        val html =
            converter.render(
                adf(
                    "listItem",
                    listOf(
                        mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "X"))),
                    ),
                ),
            )
        assertTrue(html!!.contains("<li>"))
        assertTrue(html.contains("X"))
    }

    private fun adf(
        type: String,
        text: String? = null,
        marks: List<Map<String, Any>>? = null,
        content: List<Map<String, Any>>? = null,
        attrs: Map<String, Any>? = null,
    ): String {
        val body = mutableMapOf<String, Any>("type" to type)
        if (text != null) body["text"] = text
        if (marks != null) body["marks"] = marks
        if (content != null) body["content"] = content
        if (attrs != null) body["attrs"] = attrs
        return jacksonObjectMapper().writeValueAsString(body)
    }

    private fun wrapInDoc(
        type: String,
        text: String? = null,
        marks: List<Map<String, Any>>? = null,
        content: List<Map<String, Any>>? = null,
        attrs: Map<String, Any>? = null,
    ): String {
        val body = mutableMapOf<String, Any>("type" to type)
        if (text != null) body["text"] = text
        if (marks != null) body["marks"] = marks
        if (content != null) body["content"] = content
        if (attrs != null) body["attrs"] = attrs
        val paragraph = mapOf("type" to "paragraph", "content" to listOf(body))
        val doc = mapOf("type" to "doc", "content" to listOf(paragraph))
        return jacksonObjectMapper().writeValueAsString(doc)
    }
}
