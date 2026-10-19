package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlAiServiceExtractorsTest {
    private fun service(): SqlAiService =
        SqlAiService(
            org.mockito.kotlin.mock(),
            org.mockito.kotlin.mock(),
            org.mockito.kotlin.mock(),
            com.stacklens.adapter.out.textfilter
                .DefaultAiResponseJsonExtractor(),
        )

    @Test
    fun `nodeText returns the string when input is a plain string`() {
        assertEquals("hello", service().nodeText("hello"))
    }

    @Test
    fun `nodeText returns empty string when node is null`() {
        assertEquals("", service().nodeText(null))
    }

    @Test
    fun `nodeText extracts the 'text' key from a map when present`() {
        assertEquals("from-text", service().nodeText(mapOf("text" to "from-text")))
    }

    @Test
    fun `nodeText extracts message key when text is not present`() {
        assertEquals("from-message", service().nodeText(mapOf("message" to "from-message")))
    }

    @Test
    fun `nodeText iterates the known key list to find a non-blank string value`() {
        assertEquals("from-issue", service().nodeText(mapOf("issue" to "from-issue")))
        assertEquals("from-description", service().nodeText(mapOf("description" to "from-description")))
        assertEquals("from-statement", service().nodeText(mapOf("statement" to "from-statement")))
        assertEquals("from-suggestion", service().nodeText(mapOf("suggestion" to "from-suggestion")))
        assertEquals("from-query", service().nodeText(mapOf("query" to "from-query")))
        assertEquals("from-value", service().nodeText(mapOf("value" to "from-value")))
    }

    @Test
    fun `nodeText skips blank known keys and falls through to next`() {
        val map = mapOf("text" to "  ", "message" to "", "issue" to "kept", "description" to "after")
        assertEquals("kept", service().nodeText(map))
    }

    @Test
    fun `nodeText returns toString when map has no known non-blank keys`() {
        val map = mapOf("foo" to "bar", "baz" to "")
        val s = service().nodeText(map)
        assertTrue(s.contains("foo"))
    }

    @Test
    fun `nodeText returns toString for arbitrary non-string non-map types`() {
        assertEquals("42", service().nodeText(42))
    }

    @Test
    fun `extractJsonBlock extracts content from a triple-backtick fence`() {
        val text = "```\n{\"foo\": \"bar\"}\n```"
        assertEquals("{\"foo\": \"bar\"}", service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock strips the 'json' tag from a typed fence`() {
        val text = "```json\n{\"x\": 1}\n```"
        assertEquals("{\"x\": 1}", service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock strips leading and trailing whitespace`() {
        val text = "```\n  \n  {\"a\": \"b\"}\n\n```"
        assertEquals("{\"a\": \"b\"}", service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock falls back to brace-matching when no fence is present`() {
        val text = "Some preamble {\"k\": \"v\"} trailing text"
        assertEquals("{\"k\": \"v\"}", service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock uses both the first { and the last } when matching`() {
        val text = "prefix {\"a\": \"b{c}d\"} suffix"
        assertEquals("{\"a\": \"b{c}d\"}", service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock returns the input text when there are no braces and no fence`() {
        val text = "plain text without any json"
        assertEquals(text, service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock returns the text when only opening brace is present`() {
        val text = "broken { only opening"
        assertEquals(text, service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock returns the text when only closing brace is present`() {
        val text = "broken } only closing"
        assertEquals(text, service().extractJsonBlock(text))
    }

    @Test
    fun `extractJsonBlock fenced wins over brace matching`() {
        val text = "```\n{\"from\":\"fence\"}\n``` preamble {\"ignored\": true}"
        assertEquals("{\"from\":\"fence\"}", service().extractJsonBlock(text))
    }
}
