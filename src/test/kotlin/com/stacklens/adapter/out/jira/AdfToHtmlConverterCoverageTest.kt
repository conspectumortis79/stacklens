package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdfToHtmlConverterCoverageTest {
    private val converter = AdfToHtmlConverter(ObjectMapper())

    private fun doc(vararg blocks: String) = """{"type":"doc","content":[${blocks.joinToString(",")}]}"""

    private fun text(
        value: String,
        vararg marks: String,
    ): String {
        val suffix = if (marks.isEmpty()) "" else ",\"marks\":[${marks.joinToString(",")}]"
        return """{"type":"text","text":"$value"$suffix}"""
    }

    private fun paragraph(vararg inlines: String) = """{"type":"paragraph","content":[${inlines.joinToString(",")}]}"""

    private fun listItem(vararg blocks: String) = """{"type":"listItem","content":[${blocks.joinToString(",")}]}"""

    @Test
    fun `absent or blank payloads render nothing`() {
        assertEquals(null, converter.render(null))
        assertEquals(null, converter.render("   "))
    }

    @Test
    fun `a plain list item renders its paragraph inline`() {
        val html = converter.render(doc("""{"type":"bulletList","content":[${listItem(paragraph(text("single")))}]}"""))

        assertEquals("<ul class=\"sp-jira-ul\"><li>single</li></ul>", html)
    }

    @Test
    fun `a list item with nested blocks renders them as blocks`() {
        val item = listItem(paragraph(text("first")), paragraph(text("second")))

        val html = converter.render(doc("""{"type":"bulletList","content":[$item]}"""))

        assertTrue(html!!.contains("<li>") && html.contains("first"), html)
        assertTrue(html.contains("second"), html)
    }

    @Test
    fun `an ordered list item collapses its single paragraph`() {
        val html = converter.render(doc("""{"type":"orderedList","content":[${listItem(paragraph(text("step")))}]}"""))

        assertEquals("<ol class=\"sp-jira-ol\"><li>step</li></ol>", html)
    }

    @Test
    fun `a block without content renders nothing`() {
        assertEquals(null, converter.render(doc("""{"type":"paragraph"}""")))
    }

    @Test
    fun `content that is not an array is ignored`() {
        assertEquals(null, converter.render(doc("""{"type":"paragraph","content":"plain"}""")))
    }

    @Test
    fun `unknown marks leave the text unchanged`() {
        val html = converter.render(doc(paragraph(text("kept", """{"type":"highlight"}"""))))

        assertEquals("<p class=\"sp-jira-p\">kept</p>", html)
    }

    @Test
    fun `marks that are not an array are ignored`() {
        val html = converter.render(doc("""{"type":"paragraph","content":[{"type":"text","text":"raw","marks":{"type":"strong"}}]}"""))

        assertEquals("<p class=\"sp-jira-p\">raw</p>", html)
    }

    @Test
    fun `an empty mark list leaves the text as is`() {
        assertEquals("<p class=\"sp-jira-p\">bare</p>", converter.render(doc(paragraph(text("bare", "")))))
    }

    @Test
    fun `the first mark wraps the outermost element`() {
        val html = converter.render(doc(paragraph(text("both", """{"type":"strong"}""", """{"type":"em"}"""))))

        assertEquals("<p class=\"sp-jira-p\"><strong><em>both</em></strong></p>", html)
    }
}
