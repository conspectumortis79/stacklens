package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SprintReviewOrchestratorStateCoverageTest {
    private val orchestrator =
        SprintReviewOrchestrator(
            copilot = mock<CopilotPort>(),
            promptLoader = mock<PromptReaderPort>(),
            i18n = mock<I18nPort>(),
        )

    @Test
    fun `every known state is normalised to its canonical value`() {
        assertEquals(SprintReviewState.BEARBEITET, orchestrator.normalizeState(SprintReviewState.BEARBEITET.name.lowercase()))
        assertEquals(SprintReviewState.OFFENE_FRAGE, orchestrator.normalizeState(SprintReviewState.OFFENE_FRAGE.name.lowercase()))
        assertEquals(SprintReviewState.REFINEMENT, orchestrator.normalizeState(SprintReviewState.REFINEMENT.name.lowercase()))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("irgendwas"))
    }

    @Test
    fun `a reason naming an anwenderfall maps to the missing user story key`() {
        assertEquals("missing_user_story", orchestrator.resolveReasonKey("Es fehlt ein Anwenderfall"))

        assertEquals("missing_user_story", orchestrator.resolveReasonKey("no user story given"))
    }

    @Test
    fun `a reason without a user story hint falls through to the next category`() {
        assertEquals("missing_background", orchestrator.resolveReasonKey("Hintergrund fehlt"))

        assertEquals("missing_components", orchestrator.resolveReasonKey("Komponenten unklar"))
    }
}
