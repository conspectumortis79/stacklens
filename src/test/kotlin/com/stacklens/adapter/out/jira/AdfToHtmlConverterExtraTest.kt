package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdfToHtmlConverterExtraTest {
    private val converter = AdfToHtmlConverter(jacksonObjectMapper())
    private val mapper = jacksonObjectMapper()

    @Test
    fun `render of listItem with non-paragraph content uses block join`() {
        val html =
            converter.render(
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "listItem",
                        "content" to
                            listOf(
                                mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "A"))),
                                mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "B"))),
                            ),
                    ),
                ),
            )
        assertNotNull(html)
        assertTrue(html!!.contains("<li>"))
        assertTrue(html.contains("A"))
        assertTrue(html.contains("B"))
    }

    @Test
    fun `render of unknown block type with content uses content join`() {
        val html =
            converter.render(
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "doc",
                        "content" to
                            listOf(
                                mapOf(
                                    "type" to "someUnknownBlock",
                                    "content" to
                                        listOf(
                                            mapOf(
                                                "type" to "paragraph",
                                                "content" to listOf(mapOf("type" to "text", "text" to "x")),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
            )
        assertNotNull(html)
        assertTrue(html!!.contains("x"))
    }

    @Test
    fun `render of textColor mark with blank color returns html without color span`() {
        val html =
            converter.render(
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "doc",
                        "content" to
                            listOf(
                                mapOf(
                                    "type" to "paragraph",
                                    "content" to
                                        listOf(
                                            mapOf(
                                                "type" to "text",
                                                "text" to "x",
                                                "marks" to
                                                    listOf(
                                                        mapOf(
                                                            "type" to "textColor",
                                                            "attrs" to mapOf("color" to ""),
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
            )
        assertNotNull(html)
        assertFalse(html!!.contains("color:"))
    }

    @Test
    fun `render of subsup mark with no attrs defaults to sup`() {
        val html =
            converter.render(
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "doc",
                        "content" to
                            listOf(
                                mapOf(
                                    "type" to "paragraph",
                                    "content" to
                                        listOf(
                                            mapOf(
                                                "type" to "text",
                                                "text" to "x",
                                                "marks" to
                                                    listOf(
                                                        mapOf("type" to "subsup"),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
            )
        assertNotNull(html)
        assertTrue(html!!.contains("<sup>"))
    }
}
