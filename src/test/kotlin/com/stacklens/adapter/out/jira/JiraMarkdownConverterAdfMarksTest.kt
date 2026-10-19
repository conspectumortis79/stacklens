package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraMarkdownConverterAdfMarksTest {
    private val converter = JiraMarkdownConverter()

    private fun inlineNodes(markdown: String): List<*> {
        val doc = converter.markdownToAdf(markdown)

        @Suppress("UNCHECKED_CAST")
        val first =
            (doc.getValue("content") as List<Map<String, Any>>)
                .first()
        @Suppress("UNCHECKED_CAST")
        return first.getValue("content") as List<*>
    }

    @Test
    fun `italic segments become emphasised text nodes`() {
        val nodes = inlineNodes("plain _emphasis_ tail")

        @Suppress("UNCHECKED_CAST")
        val emphasised = nodes.map { it as Map<String, Any> }.first { it["text"] == "emphasis" }

        assertEquals("text", emphasised.getValue("type"))
        @Suppress("UNCHECKED_CAST")
        assertEquals("em", (emphasised.getValue("marks") as List<Map<String, Any>>).single().getValue("type"))
    }

    @Test
    fun `underscore delimited text becomes an emphasized run`() {
        val adf = converter.markdownToAdf("normal _betont_ ende")

        assertTrue(adf.toString().contains("type=em"), "unexpected: $adf")
    }

    @Test
    fun `bold and code segments keep their own marks`() {
        val nodes = inlineNodes("**strong** and `code`")

        @Suppress("UNCHECKED_CAST")
        val types =
            nodes
                .map { it as Map<String, Any> }
                .filter { it.containsKey("marks") }
                .associate {
                    it.getValue("text") as String to
                        @Suppress("UNCHECKED_CAST")
                        (it.getValue("marks") as List<Map<String, Any>>).single().getValue("type") as String
                }

        assertEquals(mapOf("strong" to "strong", "code" to "code"), types)
    }

    @Test
    fun `text without inline markers is emitted unchanged`() {
        val nodes = inlineNodes("nothing special")

        assertEquals(1, nodes.size)
        assertTrue(nodes.toString().contains("nothing special"))
    }

    @Test
    fun `ordered list becomes an orderedList node`() {
        val doc = converter.markdownToAdf("1. first\n2. second")

        @Suppress("UNCHECKED_CAST")
        val content = doc.getValue("content") as List<Map<String, Any>>
        assertEquals("orderedList", content.single().getValue("type"))
    }
}
