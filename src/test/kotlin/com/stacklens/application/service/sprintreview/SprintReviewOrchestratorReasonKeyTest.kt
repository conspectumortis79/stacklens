package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SprintReviewOrchestratorReasonKeyTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val i18n: I18nPort = mock()
    private val orchestrator = SprintReviewOrchestrator(copilot, promptLoader, i18n)

    private fun ticket(
        key: String = "PROJ-1",
        description: String = "desc",
        subtasks: List<SprintReviewTicket> = emptyList(),
        referencedTickets: List<SprintReviewTicket> = emptyList(),
    ): SprintReviewTicket =
        SprintReviewTicket(
            key = key,
            summary = "summary",
            description = description,
            url = "https://example.atlassian.net/browse/$key",
            issueType = "Story",
            status = "In Progress",
            assignee = "Alice",
            storyPoints = 3.0,
            subtasks = subtasks,
            referencedTickets = referencedTickets,
        )

    @Test fun `state names are normalised to the four known values`() {
        assertEquals(SprintReviewState.BEARBEITET, orchestrator.normalizeState("bearbeitet"))
        assertEquals(SprintReviewState.OFFENE_FRAGE, orchestrator.normalizeState("Offene_Frage"))
        assertEquals(SprintReviewState.REFINEMENT, orchestrator.normalizeState("Refinement"))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("irgendwas"))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState(""))
    }

    @Test fun `aliases resolve through the lookup table before keyword matching`() {
        assertEquals("", orchestrator.resolveReasonKey("   "))
        assertEquals("missing_story_points", orchestrator.resolveReasonKey("no_story_points"))
        assertEquals("missing_story_points", orchestrator.resolveReasonKey(" Story Points Not Estimated "))
        assertEquals("missing_story_points", orchestrator.resolveReasonKey("die story points fehlen"))
        assertEquals("missing_story_points", orchestrator.resolveReasonKey("story-point fehlt"))
        assertEquals("missing_story_points", orchestrator.resolveReasonKey("keine schätzung"))
        assertEquals(
            "missing_acceptance_criteria",
            orchestrator.resolveReasonKey("Acceptance criteria fehlen"),
        )
        assertEquals("missing_acceptance_criteria", orchestrator.resolveReasonKey("Akzeptanzkriterien"))
        assertEquals("missing_acceptance_criteria", orchestrator.resolveReasonKey("keine ak definiert"))
        assertEquals("missing_user_story", orchestrator.resolveReasonKey("user story fehlt"))
        assertEquals("missing_user_story", orchestrator.resolveReasonKey("Anwenderfall unklar"))
        assertEquals("missing_background", orchestrator.resolveReasonKey("background fehlt"))
        assertEquals("missing_background", orchestrator.resolveReasonKey("Hintergrund unklar"))
        assertEquals("missing_background", orchestrator.resolveReasonKey("kontext fehlt"))
        assertEquals("missing_components", orchestrator.resolveReasonKey("component unknown"))
        assertEquals("missing_components", orchestrator.resolveReasonKey("Modul unklar"))
        assertEquals("missing_components", orchestrator.resolveReasonKey("Komponente fehlt"))
        assertEquals("missing_clear_goal", orchestrator.resolveReasonKey("goal fehlt"))
        assertEquals("missing_clear_goal", orchestrator.resolveReasonKey("Ziel unbestimmt"))
        assertEquals("missing_clear_goal", orchestrator.resolveReasonKey("objective missing"))
        assertEquals("description_incomplete", orchestrator.resolveReasonKey("incomplete description"))
        assertEquals("description_incomplete", orchestrator.resolveReasonKey("unvollständige Beschreibung"))
        assertEquals("description_incomplete", orchestrator.resolveReasonKey("still a todo"))
        assertEquals("description_incomplete", orchestrator.resolveReasonKey("tbd"))
        assertEquals("description_incomplete", orchestrator.resolveReasonKey("<placeholder>"))
        assertEquals("sonstiger grund", orchestrator.resolveReasonKey("  sonstiger grund "))
    }

    @Test fun `sub tasks and referenced tickets render with and without descriptions`() {
        assertEquals("(none)", orchestrator.formatSubtasks(ticket()))
        assertEquals("(none)", orchestrator.formatReferencedTickets(ticket()))

        val withSubtasks =
            ticket(
                subtasks =
                    listOf(
                        ticket("PROJ-2", "  detail  "),
                        ticket("PROJ-3", "   "),
                    ),
                referencedTickets =
                    listOf(
                        ticket("PROJ-4", "related detail"),
                        ticket("PROJ-5", ""),
                    ),
            )

        val subtasks = orchestrator.formatSubtasks(withSubtasks)
        val referenced = orchestrator.formatReferencedTickets(withSubtasks)

        assertTrue(subtasks.contains("**PROJ-2** — summary\ndetail"))
        assertTrue(subtasks.contains("**PROJ-3** — summary"))
        assertTrue(referenced.contains("**PROJ-4** — summary\nrelated detail"))
        assertTrue(referenced.contains("**PROJ-5** — summary"))
        assertEquals(1, Regex("\\*\\*PROJ-3").findAll(subtasks).count())
    }

    @Test fun `ai responses without a json object parse to empty values`() {
        assertEquals("", orchestrator.parseAiResponse("no json here").state)
        assertEquals("", orchestrator.parseAiResponse("{").improvementSummary)
        assertEquals("", orchestrator.parseAiResponse("}only closer{").state)
        assertEquals(emptyList<String>(), orchestrator.parseAiResponse("{}").openQuestions)
    }

    @Test fun `review ticket drops reasons and questions that resolve to blank text`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("model-x")
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("Prompt für {ticketKey}")
        whenever(i18n.translateError(any())).thenReturn("übersetzt")
        whenever(copilot.execute(eq("model-x"), any<String>(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"REFINEMENT","improvementSummary":"x","refinementReasons":["","todo","todo"],"openQuestions":["   ","klar?"]}""",
        )

        val refinement = orchestrator.reviewTicket("Prompt für {ticketKey}", ticket())

        assertEquals(SprintReviewState.REFINEMENT, refinement.state)
        assertEquals(listOf("description_incomplete"), refinement.refinementReasons)
        assertTrue(refinement.openPoints.isEmpty())

        whenever(copilot.execute(eq("model-x"), any<String>(), anyOrNull(), anyOrNull())).thenReturn(
            """{"state":"OFFENE_FRAGE","improvementSummary":"x","refinementReasons":["   "],"openQuestions":["   ","klar?","klar?"]}""",
        )

        val open = orchestrator.reviewTicket("Prompt für {ticketKey}", ticket())

        assertEquals(SprintReviewState.OFFENE_FRAGE, open.state)
        assertEquals(listOf("klar?"), open.openPoints)
        assertTrue(open.refinementReasons.isEmpty())
    }

    @Test fun `open questions become the state for offene frage tickets`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("model-x")
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("P {ticketKey}")
        whenever(copilot.execute(eq("model-x"), any<String>(), anyOrNull(), anyOrNull())).thenReturn(
            "```json\n{\"state\":\"OFFENE_FRAGE\",\"improvementSummary\":\"fragend\",\"openQuestions\":\"not-an-array\"}\n```",
        )

        val row = orchestrator.reviewTicket("P {ticketKey}", ticket())

        assertEquals(SprintReviewState.OFFENE_FRAGE, row.state)
        assertTrue(row.openPoints.isEmpty())
    }
}
