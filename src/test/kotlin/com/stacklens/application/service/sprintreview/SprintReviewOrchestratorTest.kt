package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SprintReviewOrchestratorTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val i18n: I18nPort = mock()

    private fun orchestrator(): SprintReviewOrchestrator =
        SprintReviewOrchestrator(
            copilot = copilot,
            promptLoader = promptLoader,
            i18n = i18n,
        )

    private fun ticket(
        key: String = "PROJ-1",
        summary: String = "Test",
        description: String = "Body",
        storyPoints: Double? = 3.0,
        issueType: String = "Story",
        status: String = "Open",
        assignee: String? = "Alice",
    ): SprintReviewTicket =
        SprintReviewTicket(
            key = key,
            summary = summary,
            description = description,
            url = "https://jira/$key",
            issueType = issueType,
            status = status,
            assignee = assignee,
            labels = listOf("foo", "bar"),
            storyPoints = storyPoints,
        )

    @BeforeEach
    fun setUp() {
        whenever(promptLoader.get(any(), any<String>(), any())).thenReturn("PROMPT_TEMPLATE")
    }

    @Test
    fun `reviewTicket returns BEARBEITET when AI reports a complete ticket`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"BEARBEITET","improvementSummary":"Refactors the auth flow.","refinementReasons":[],"openQuestions":[]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(SprintReviewState.BEARBEITET, row.state)
        assertEquals("Refactors the auth flow.", row.improvementSummary)
        assertTrue(row.openPoints.isEmpty())
        assertTrue(row.refinementReasons.isEmpty())
    }

    @Test
    fun `reviewTicket returns BEARBEITET even when story points are missing — AI decides`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"BEARBEITET","improvementSummary":"All good.","refinementReasons":[],"openQuestions":[]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket(storyPoints = null))
        assertEquals(SprintReviewState.BEARBEITET, row.state)
    }

    @Test
    fun `reviewTicket returns OFFENE_FRAGE when AI reports open questions`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"OFFENE_FRAGE","improvementSummary":"Adds a new endpoint.","refinementReasons":[],"openQuestions":["Klärung mit @anna nötig","Design-Review ausstehend"]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(SprintReviewState.OFFENE_FRAGE, row.state)
        assertEquals(2, row.openPoints.size)
        assertEquals("Klärung mit @anna nötig", row.openPoints[0])
        assertTrue(row.refinementReasons.isEmpty())
    }

    @Test
    fun `reviewTicket returns REFINEMENT with the reasons the AI returned`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"REFINEMENT","improvementSummary":"Ticket is incomplete.","refinementReasons":["missing_story_points","missing_acceptance_criteria","missing_user_story"],"openQuestions":[]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket(storyPoints = null))
        assertEquals(SprintReviewState.REFINEMENT, row.state)
        assertEquals(3, row.refinementReasons.size)
        assertEquals("missing_story_points", row.refinementReasons[0])
        assertTrue(row.openPoints.isEmpty())
    }

    @Test
    fun `reviewTicket translates English AI reason variants to canonical i18n keys`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"REFINEMENT","improvementSummary":"Incomplete.","refinementReasons":["Story Points not estimated","Akzeptanzkriterien fehlen","user story fehlt","affected components missing"],"openQuestions":[]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket(storyPoints = null))
        assertEquals(SprintReviewState.REFINEMENT, row.state)
        assertEquals(
            listOf(
                "missing_story_points",
                "missing_acceptance_criteria",
                "missing_user_story",
                "missing_components",
            ),
            row.refinementReasons,
        )
    }

    @Test
    fun `reviewTicket keeps unknown reason text as-is for forward compatibility`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"REFINEMENT","improvementSummary":"x","refinementReasons":["missing_business_case"],"openQuestions":[]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket(storyPoints = null))
        assertEquals(listOf("missing_business_case"), row.refinementReasons)
    }

    @Test
    fun `reviewTicket returns FAILED when the AI call itself throws`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("AI provider down"))
        whenever(i18n.translateError(any())).thenReturn("Translated error")

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(SprintReviewState.FAILED, row.state)
        assertEquals("Translated error", row.errorMessage)
    }

    @Test
    fun `reviewTicket returns FAILED when AI returns an unknown state value`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"UNKNOWN_STATE","improvementSummary":"x","refinementReasons":[],"openQuestions":[]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(SprintReviewState.FAILED, row.state)
    }

    @Test
    fun `reviewTicket returns FAILED when AI returns a string without any JSON`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("I am not sure how to respond.")

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(SprintReviewState.FAILED, row.state)
    }

    @Test
    fun `reviewTicket parses fenced JSON response`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """Sure, here's the JSON:
```json
{"state":"BEARBEITET","improvementSummary":"Done.","refinementReasons":[],"openQuestions":[]}
```""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(SprintReviewState.BEARBEITET, row.state)
        assertEquals("Done.", row.improvementSummary)
    }

    @Test
    fun `reviewTicket uses the supplied template when it contains the placeholder`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"BEARBEITET","improvementSummary":"ok","refinementReasons":[],"openQuestions":[]}""",
        )
        val customTemplate = "Custom prompt for {ticketKey}"
        val row = orchestrator().reviewTicket(customTemplate, ticket())
        assertEquals(SprintReviewState.BEARBEITET, row.state)
    }

    @Test
    fun `reviewTicket truncates long summaries to 280 characters`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val longSummary = "x".repeat(800)
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"BEARBEITET","improvementSummary":"$longSummary","refinementReasons":[],"openQuestions":[]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(280, row.improvementSummary.length)
    }

    @Test
    fun `reviewTicket truncates openQuestions to at most 10 entries`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val points = (1..20).joinToString(",") { "\"q$it\"" }
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"OFFENE_FRAGE","improvementSummary":"x","refinementReasons":[],"openQuestions":[$points]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(10, row.openPoints.size)
    }

    @Test
    fun `reviewTicket passes the sprintName in the German prompt and accepts the German state literal`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"OFFENE_FRAGE","improvementSummary":"Implementiert X.","refinementReasons":[],"openQuestions":["Klärung mit Team Y ausstehend"]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertEquals(SprintReviewState.OFFENE_FRAGE, row.state)
        assertEquals(1, row.openPoints.size)
        assertEquals("Klärung mit Team Y ausstehend", row.openPoints[0])
    }

    @Test
    fun `reviewTicket ignores refinementReasons when state is OFFENE_FRAGE`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"OFFENE_FRAGE","improvementSummary":"x","refinementReasons":["should_be_ignored"],"openQuestions":["real_question"]}""",
        )

        val row = orchestrator().reviewTicket("ignored", ticket())
        assertTrue(row.refinementReasons.isEmpty(), "refinementReasons should be empty for OFFENE_FRAGE")
        assertEquals(listOf("real_question"), row.openPoints)
    }
}
