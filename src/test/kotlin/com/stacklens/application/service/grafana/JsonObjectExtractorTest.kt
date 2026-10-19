package com.stacklens.application.service.grafana

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonObjectExtractorTest {
    @Test
    fun `extractFirstJsonObject returns null for input without braces`() {
        assertNull(JsonObjectExtractor.extractFirstJsonObject("no json here"))
    }

    @Test
    fun `extractFirstJsonObject returns null for unbalanced braces`() {
        assertNull(JsonObjectExtractor.extractFirstJsonObject("{unbalanced"))
    }

    @Test
    fun `extractFirstJsonObject returns the first JSON object from text`() {
        val text = "Some prose. {\"x\":1} More prose."
        assertEquals("{\"x\":1}", JsonObjectExtractor.extractFirstJsonObject(text))
    }

    @Test
    fun `extractFirstJsonObject handles nested objects`() {
        val text = "prefix {\"a\":{\"b\":2}} suffix"
        assertEquals("{\"a\":{\"b\":2}}", JsonObjectExtractor.extractFirstJsonObject(text))
    }

    @Test
    fun `extractFirstJsonObject extracts the content of a json fenced block`() {
        val text =
            """
            preamble
            ```json
            {"hello":"world"}
            ```
            trailing
            """.trimIndent()
        assertEquals("{\"hello\":\"world\"}", JsonObjectExtractor.extractFirstJsonObject(text))
    }

    @Test
    fun `extractFirstJsonObject ignores a fenced block whose content is not JSON`() {
        val text =
            """
            preamble
            ```json
            not json
            ```
            {"fallback":true}
            """.trimIndent()
        assertEquals("{\"fallback\":true}", JsonObjectExtractor.extractFirstJsonObject(text))
    }
}
