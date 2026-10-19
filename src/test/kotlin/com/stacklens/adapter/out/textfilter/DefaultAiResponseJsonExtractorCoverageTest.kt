package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultAiResponseJsonExtractorCoverageTest {
    private val extractor = DefaultAiResponseJsonExtractor()

    @Test
    fun `extractJsonBlockAfter returns the object between the marker and the last brace`() {
        assertEquals(
            """{"a": {"b": 1}}""",
            extractor.extractJsonBlockAfter("BEGIN {\"a\": {\"b\": 1}} trailing", "BEGIN"),
        )
    }

    @Test
    fun `extractJsonBlockAfter returns null when the section has no opening brace`() {
        assertNull(extractor.extractJsonBlockAfter("HEADER\nno json here\n```", "HEADER"))
    }

    @Test
    fun `extractJsonBlockAfter returns null when the section has no closing brace`() {
        assertNull(extractor.extractJsonBlockAfter("HEADER\n{\"a\": 1", "HEADER"))
    }

    @Test
    fun `extractJsonBlockAfter returns null when the closing brace precedes the opening one`() {
        assertNull(extractor.extractJsonBlockAfter("HEADER\n}{", "HEADER"))
    }

    @Test
    fun `extractJsonBlockAfter returns null when the start marker is missing`() {
        assertNull(extractor.extractJsonBlockAfter("{\"a\": 1}", "ABSENT"))
    }

    @Test
    fun `extractBalancedJsonObject tracks escaped quotes and braces inside string literals`() {
        val source = """{"k": "a\"b{c"}"""

        assertEquals(source, extractor.extractBalancedJsonObject(source, source.indexOf('{')))
    }

    @Test
    fun `extractBalancedJsonObject ignores backslashes outside string literals`() {
        val source = """{ \ "a": 1 }"""

        assertEquals(source, extractor.extractBalancedJsonObject(source, 0))
    }

    @Test
    fun `extractBalancedJsonObject skips nested objects`() {
        val source = """{"a":{"b":1}}"""

        assertEquals(source, extractor.extractBalancedJsonObject(source, 0))
    }

    @Test
    fun `extractBalancedJsonObject returns null for an unbalanced object`() {
        assertNull(extractor.extractBalancedJsonObject("{abc", 0))
    }

    @Test
    fun `extractSection returns empty text when the start marker is missing`() {
        assertEquals("", extractor.extractSection("abc", "<<", ">>"))
    }

    @Test
    fun `extractSection returns the remainder when the end marker is missing`() {
        assertEquals(" tail", extractor.extractSection("head tail", "head", ">>"))
    }

    @Test
    fun `extractSection cuts between both markers`() {
        assertEquals(" mid ", extractor.extractSection("a[ mid ]b", "[", "]"))
    }

    @Test
    fun `extractJsonBlock with markers returns the array between brackets`() {
        assertEquals("[1,2]", extractor.extractJsonBlock("x[1,2]y", "x", "y"))
        assertNull(extractor.extractJsonBlock("x no brackets y", "x", "y"))
        assertNull(extractor.extractJsonBlock("x[1,2 y", "x", "y"))
        assertNull(extractor.extractJsonBlock("x 1,2]y", "x", "y"))
    }

    @Test
    fun `extractJsonBlock prefers a fenced block and falls back to braces or raw text`() {
        assertEquals("{\"a\": 1}", extractor.extractJsonBlock("```json\n{\"a\": 1}\n```"))
        assertEquals("{\"a\": 1}", extractor.extractJsonBlock("noise {\"a\": 1} noise"))
        assertEquals("no json at all", extractor.extractJsonBlock("no json at all"))
    }

    @Test
    fun `extractJsonArray prefers a fenced json array and falls back to raw brackets`() {
        assertEquals("[1,2]", extractor.extractJsonArray("```json\n[1,2]\n```"))
        assertEquals("[1,2]", extractor.extractJsonArray(" preamble [1,2] trailer"))
        assertNull(extractor.extractJsonArray("nothing here"))
    }

    @Test
    fun `extractCodeBlock matches the requested language or any fence`() {
        assertEquals("SELECT 1", extractor.extractCodeBlock("```sql\nSELECT 1\n```", "yaml", "sql"))
        assertEquals("plain", extractor.extractCodeBlock("```text\nplain\n```", "sql"))
        assertNull(extractor.extractCodeBlock("unfenced", "sql"))
    }
}
