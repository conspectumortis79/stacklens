package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdfToHtmlConverterListItemShapeCoverageTest {
    private val converter = AdfToHtmlConverter(ObjectMapper())

    private fun render(adf: String): String = converter.render(adf).orEmpty()

    @Test
    fun `a list item with a single paragraph is rendered inline`() {
        val html =
            render(
                """{"type":"doc","content":[{"type":"bulletList","content":[
                   {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"only"}]}]}
                ]}]}""",
            )

        assertEquals("<ul class=\"sp-jira-ul\"><li>only</li></ul>", html)
    }

    @Test
    fun `a list item whose single child is not a paragraph keeps its block markup`() {
        val html =
            render(
                """{"type":"doc","content":[{"type":"bulletList","content":[
                   {"type":"listItem","content":[{"type":"codeBlock","attrs":{"language":"kotlin"},"content":[{"type":"text","text":"val x = 1"}]}]}
                ]}]}""",
            )

        assertTrue(html.contains("<li>") && html.contains("sp-jira-code"), html)
        assertTrue(html.contains("val x = 1"), html)
    }

    @Test
    fun `an empty list item renders as an empty entry`() {
        val html =
            render(
                """{"type":"doc","content":[{"type":"bulletList","content":[{"type":"listItem","content":[]}]}]}""",
            )

        assertTrue(html.contains("<li></li>"), html)
    }
}
