package com.stacklens.application.service

import com.stacklens.application.port.out.AiJsonRepairerPort
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.testsupport.SyntheticDefaultBridges
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketRefinementJsonParserFallbackBridgeTest {
    private val jsonCodec: JsonCodecPort = mock()
    private val parser =
        TicketRefinementJsonParser(
            jsonCodec,
            mock<AiJsonRepairerPort>(),
            mock<AiResponseJsonExtractorPort>(),
        )

    private fun stubMap() {
        whenever(jsonCodec.decodeMap(any<String>()))
            .thenReturn(
                mapOf(
                    "improvedDescription" to "  improved body  ",
                    "openPoints" to listOf("  point ", "", null, "point"),
                    "improvedSummary" to " shorter ",
                ),
            )
    }

    @Test
    fun `a plain answer falls back to the markdown review`() {
        stubMap()

        val parsed = parser.parseAiResponse("""{"improvedDescription":"improved body"}""")

        assertEquals("improved body", parsed.improvedDescription)
        assertEquals(listOf("point", "point"), parsed.openPoints)
        assertEquals("shorter", parsed.improvedSummary)
        assertEquals(DescriptionFormat.MARKDOWN, parsed.improvedFormat)
        assertEquals(emptyList<Any>(), parsed.suggestedSubtasks)
    }

    @Test
    fun `a wiki answer without wiki content falls back to markdown`() {
        stubMap()

        val parsed = parser.parseAiResponse("""{"improvedDescription":"improved body"}""", DescriptionFormat.WIKI)

        assertEquals("improved body", parsed.improvedDescription)
    }

    @Test
    fun `the fallback keeps its default subtask list when the caller omits it`() {
        stubMap()

        val parsed =
            SyntheticDefaultBridges.invoke(
                parser,
                "markdownFallback",
                listOf(mapOf("improvedDescription" to "body"), "raw text", listOf("a"), "summary"),
                1 shl 4,
            )

        assertEquals("body", (parsed as TicketRefinementJsonParser.ParsedReview).improvedDescription)
        assertEquals(emptyList<Any>(), parsed.suggestedSubtasks)
    }
}
