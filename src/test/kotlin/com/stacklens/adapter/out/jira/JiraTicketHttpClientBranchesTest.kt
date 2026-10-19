package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraTicketHttpClientBranchesTest {
    private val mapper = ObjectMapper()
    private val client = JiraTicketHttpClient()

    @Test
    fun `flattenDescriptionNode returns empty for null`() {
        assertEquals("", client.flattenDescriptionNode(null))
    }

    @Test
    fun `flattenDescriptionNode returns empty for null node`() {
        val node = mapper.readTree("null")
        assertEquals("", client.flattenDescriptionNode(node))
    }

    @Test
    fun `flattenDescriptionNode returns text for textual node`() {
        val node = mapper.readTree("\"hello world\"")
        assertEquals("hello world", client.flattenDescriptionNode(node))
    }

    @Test
    fun `flattenDescriptionNode flattens complex ADF document`() {
        val adf =
            """
            {
              "type": "doc",
              "version": 1,
              "content": [
                { "type": "paragraph", "content": [
                  { "type": "text", "text": "Hello " },
                  { "type": "text", "text": "world" }
                ]}
              ]
            }
            """.trimIndent()
        val node = mapper.readTree(adf)
        val text = client.flattenDescriptionNode(node)
        assertEquals("Hello world", text)
    }

    @Test
    fun `flattenDescriptionNode flattens ADF with nested content`() {
        val adf =
            """
            {
              "type": "doc",
              "content": [
                { "type": "bulletList", "content": [
                  { "type": "listItem", "content": [
                    { "type": "paragraph", "content": [
                      { "type": "text", "text": "Item 1" }
                    ]}
                  ]}
                ]}
              ]
            }
            """.trimIndent()
        val node = mapper.readTree(adf)
        val text = client.flattenDescriptionNode(node)
        assertTrue(text.contains("Item 1"))
    }

    @Test
    fun `flattenAdf returns text for nodes with text`() {
        val result = invokeFlattenAdf(listOf(JiraAdfNode(text = "hello", content = emptyList())))
        assertEquals("hello", result)
    }

    @Test
    fun `flattenAdf recurses into content when text is null`() {
        val child = JiraAdfNode(text = "child", content = emptyList())
        val parent = JiraAdfNode(text = null, content = listOf(child))
        val result = invokeFlattenAdf(listOf(parent))
        assertEquals("child", result)
    }

    @Test
    fun `flattenAdf returns empty string for nodes without text and empty content`() {
        val node = JiraAdfNode(text = null, content = emptyList())
        val result = invokeFlattenAdf(listOf(node))
        assertEquals("", result)
    }

    @Test
    fun `flattenAdf joins multiple nodes with spaces`() {
        val n1 = JiraAdfNode(text = "a", content = emptyList())
        val n2 = JiraAdfNode(text = "b", content = emptyList())
        val result = invokeFlattenAdf(listOf(n1, n2))
        assertEquals("a b", result)
    }

    @Test
    fun `flattenAdf collapses whitespace`() {
        val n1 = JiraAdfNode(text = "hello\n\nworld", content = emptyList())
        val result = invokeFlattenAdf(listOf(n1))
        assertEquals("hello world", result)
    }

    private fun invokeFlattenAdf(nodes: List<JiraAdfNode>): String {
        val method = JiraTicketHttpClient::class.java.getDeclaredMethod("flattenAdf", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(client, nodes) as String
    }
}
