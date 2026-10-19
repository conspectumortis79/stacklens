package com.stacklens.application.service

import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.sprintreview.SprintReviewOrchestrator
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SprintReviewOrchestratorBlankShapeCoverageTest {
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val i18n = FakeI18n()

    private fun review(raw: String): SprintReviewRow {
        whenever(copilot.resolveModel()).thenReturn("model")
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn(raw)
        return SprintReviewOrchestrator(copilot, promptLoader, i18n).reviewTicket(
            template = "review {ticketKey}",
            ticket = SprintReviewTicket(key = "P-1", summary = "s", description = "d", url = "u"),
        )
    }

    @Test
    fun `a whitespace refinement reason is dropped`() {
        val row =
            review(
                """{"state":"REFINEMENT","improvementSummary":"zu grob",""" +
                    """"refinementReasons":["  ","missing story points"],"openQuestions":[]}""",
            )

        assertEquals(listOf("missing_story_points"), row.refinementReasons)
    }

    @Test
    fun `a whitespace open question is dropped and only ten survive`() {
        val questions = (1..14).joinToString(",") { "\"Frage $it\"" }
        val row =
            review(
                """{"state":"OFFENE_FRAGE","improvementSummary":"s","refinementReasons":[],""" +
                    """"openQuestions":["   ",$questions]}""",
            )

        assertEquals(10, row.openPoints.size)
        assertEquals("Frage 1", row.openPoints.first())
        assertEquals("Frage 10", row.openPoints.last())
    }
}
