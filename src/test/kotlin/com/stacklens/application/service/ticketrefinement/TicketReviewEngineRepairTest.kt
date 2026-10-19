package com.stacklens.application.service.ticketrefinement

import com.stacklens.adapter.out.textfilter.DefaultAiJsonRepairer
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.TicketRefinementJsonParser
import com.stacklens.domain.model.TicketReview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketReviewEngineRepairTest {
    private val copilot: CopilotPort = mock()
    private val jsonCodec: JsonCodecPort = mock()
    private val engine =
        TicketReviewEngine(
            copilot = copilot,
            promptLoader = mock<PromptReaderPort>(),
            jira = mock<TicketRefinementJiraPort>(),
            i18n = mock<I18nPort>(),
            jsonCodec = jsonCodec,
            fieldFormatter = mock<TicketFieldFormatter>(),
            parser =
                TicketRefinementJsonParser(
                    jsonCodec,
                    DefaultAiJsonRepairer(),
                    DefaultAiResponseJsonExtractor(),
                ),
        )

    private fun review(body: String) =
        TicketReview(
            ticketKey = "PROJ-1",
            ticketUrl = "https://jira/PROJ-1",
            originalSummary = "old",
            originalDescription = "old body",
            improvedDescription = body,
            openPoints = emptyList(),
            state = "OK",
        )

    private fun invokeRepair(body: String): TicketReview {
        val method =
            TicketReviewEngine::class.java.getDeclaredMethod("repairReview", TicketReview::class.java)
        method.isAccessible = true
        return method.invoke(engine, review(body)) as TicketReview
    }

    @Test
    fun `a review with html is kept untouched`() {
        val repaired = invokeRepair("{not json at all")

        assertEquals("{not json at all", repaired.improvedDescription)
    }

    @Test
    fun `a truncated json body is repaired before the second decode`() {
        whenever(jsonCodec.decodeMap(any<String>()))
            .thenThrow(IllegalStateException("unterminated object"))
            .thenReturn(mapOf("improvedSummary" to "repaired"))

        val body = """{"improvedDescription":"body","openPoints":["a"""
        val repaired = invokeRepair(body)

        assertEquals(review(body), repaired)
    }

    @Test
    fun `an answer in another language still reports english`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        val method = TicketReviewEngine::class.java.getDeclaredMethod("outputLanguage")
        method.isAccessible = true

        assertEquals("English", method.invoke(engine))
    }

    @Test
    fun `a german answer reports german`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        val method = TicketReviewEngine::class.java.getDeclaredMethod("outputLanguage")
        method.isAccessible = true

        assertEquals("German", method.invoke(engine))
    }
}
