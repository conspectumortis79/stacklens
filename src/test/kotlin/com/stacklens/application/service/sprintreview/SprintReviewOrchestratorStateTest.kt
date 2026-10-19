package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SprintReviewOrchestratorStateTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val i18n: I18nPort = mock()
    private val orchestrator = SprintReviewOrchestrator(copilot, promptLoader, i18n)

    private val ticket =
        SprintReviewTicket(
            key = "PROJ-7",
            summary = "Login rollout",
            description = "Some description",
            url = "https://jira.example.com/browse/PROJ-7",
            issueType = "Story",
            status = "In Progress",
        )

    @Test
    fun `normalizeState maps every known state and everything else to failed`() {
        assertEquals(SprintReviewState.BEARBEITET, orchestrator.normalizeState("bearbeitet"))
        assertEquals(SprintReviewState.OFFENE_FRAGE, orchestrator.normalizeState("OFFENE_FRAGE"))
        assertEquals(SprintReviewState.REFINEMENT, orchestrator.normalizeState("Refinement"))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("something else"))
    }

    @Test
    fun `blank and duplicated ai entries are dropped`() {
        whenever(copilot.resolveModel()).thenReturn("model")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                """
                {"state":"OFFENE_FRAGE","improvementSummary":"summary","refinementReasons":["   ","acceptance criteria missing","acceptance criteria missing"],"openQuestions":["","q1","  q1 ","q2"]}
                """.trimIndent(),
            )

        val row = orchestrator.reviewTicket("review {ticketKey}", ticket)

        assertEquals(SprintReviewState.OFFENE_FRAGE, row.state)
        assertEquals(listOf("q1", "q2"), row.openPoints)
        assertEquals(emptyList<String>(), row.refinementReasons)
    }

    @Test
    fun `a refinement answer keeps the mapped reasons only`() {
        whenever(copilot.resolveModel()).thenReturn("model")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                """
                {"state":"REFINEMENT","improvementSummary":"summary","refinementReasons":["","story points fehlen","unknown reason"],"openQuestions":["  "]}
                """.trimIndent(),
            )

        val row = orchestrator.reviewTicket("review {ticketKey}", ticket)

        assertEquals(listOf("missing_story_points", "unknown reason"), row.refinementReasons)
        assertEquals(emptyList<String>(), row.openPoints)
    }
}
