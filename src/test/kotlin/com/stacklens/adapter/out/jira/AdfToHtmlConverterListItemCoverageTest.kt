package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdfToHtmlConverterListItemCoverageTest {
    private val converter = AdfToHtmlConverter(ObjectMapper())

    @Test
    fun `a list item with a single paragraph is rendered inline`() {
        val adf =
            """{"type":"doc","content":[{"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"punkt"}]}]}]}]}"""

        val html = converter.render(adf)

        assertTrue(html?.contains("<li>punkt</li>") == true, "unexpected: $html")
    }

    @Test
    fun `a list item with several blocks renders every block`() {
        val adf =
            """{"type":"doc","content":[{"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"erstens"}]},{"type":"rule"}]}]}]}"""

        val html = converter.render(adf)

        assertTrue(html?.contains("erstens") == true, "unexpected: $html")
        assertTrue(html?.contains("sp-jira-hr") == true, "unexpected: $html")
    }
}
