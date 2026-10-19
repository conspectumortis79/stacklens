package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.service.DescriptionFormat
import com.stacklens.application.service.TicketRefinementJsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketReviewEngineParseDefaultsTest {
    private val jsonCodec = mock<JsonCodecPort>()

    private val engine =
        TicketReviewEngine(
            copilot = mock(),
            promptLoader = mock(),
            jira = mock(),
            i18n = mock(),
            jsonCodec = jsonCodec,
            fieldFormatter = mock(),
            parser = TicketRefinementJsonParser(jsonCodec, mock(), mock()),
        )

    @Test
    fun `an ai answer is parsed as markdown when no source format is given`() {
        whenever(jsonCodec.decodeMap(any<String>())).thenReturn(mapOf("improvedDescription" to "  verbesserte Beschreibung  "))

        val parsed = engine.parseAiResponse("""{"improvedDescription":"  verbesserte Beschreibung  "}""")

        assertEquals("verbesserte Beschreibung", parsed.improvedDescription)
        assertEquals(DescriptionFormat.MARKDOWN, parsed.improvedFormat)
    }
}
