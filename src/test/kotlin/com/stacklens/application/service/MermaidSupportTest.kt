package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MermaidSupportTest {
    @Test
    fun `extract accepts uppercase mermaid fence and valid keyword`() {
        val text =
            """
            ```MERMAID
            sequenceDiagram
              Alice->>Bob: Hello
            ```
            """.trimIndent()

        val result = MermaidSupport.extract(text)

        assertTrue(result!!.contains("sequenceDiagram"))
    }

    @Test
    fun `extract returns null for whitespace only mermaid content`() {
        val text = "```mermaid\n   \n```"
        assertNull(MermaidSupport.extract(text))
    }

    @Test
    fun `sanitize trims trailing spaces and leaves non flowchart class line unchanged`() {
        val input = "class Foo Bar   \nsequenceDiagram   \n"

        val result = MermaidSupport.sanitize(input)

        assertEquals("class Foo Bar\nsequenceDiagram", result)
    }

    @Test
    fun `sanitize normalizes flowchart keyword class line`() {
        val input =
            """
            flowchart TD
            class A B styleOne
            """.trimIndent()

        val result = MermaidSupport.sanitize(input)

        assertTrue(result.contains("class A,B styleOne;"))
    }

    @Test
    fun `extract returns null when keyword not in validKeywords`() {
        val text = "```mermaid\nunknownDiagram stuff\n```"
        assertNull(MermaidSupport.extract(text))
    }

    @Test
    fun `sanitize processes graph TD as flowchart`() {
        val input = "graph TD\nclass A B styleTwo"
        val result = MermaidSupport.sanitize(input)
        assertTrue(result.contains("graph TD"))
    }

    @Test
    fun `normalizeFlowchartClassLine handles consecutive commas producing blank tokens`() {
        val input = "flowchart\nclass A,,B styleThree"
        val result = MermaidSupport.sanitize(input)
        assertTrue(result.contains("A,B"))
    }

    @Test
    fun `sanitize splits multiple classDef statements packed on one line with semicolons`() {
        val input = "sequenceDiagram\nclassDef active fill:#f00; classDef inactive fill:#0f0"
        val result = MermaidSupport.sanitize(input)
        assertTrue(result.contains("classDef active fill:#f00"))
        assertTrue(result.contains("classDef inactive fill:#0f0"))
        assertFalse(result.contains("; classDef"))
    }

    @Test
    fun `sanitize removes hyphens from alphanumeric identifiers`() {
        val input = "sequenceDiagram\nsome-thing->>other: message"
        val result = MermaidSupport.sanitize(input)
        assertTrue(result.contains("something->>other"))
    }
}
