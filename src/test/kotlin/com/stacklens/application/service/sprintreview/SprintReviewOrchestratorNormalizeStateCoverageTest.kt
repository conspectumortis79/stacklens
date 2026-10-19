package com.stacklens.application.service.sprintreview

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SprintReviewOrchestratorNormalizeStateCoverageTest {
    private val orchestrator =
        SprintReviewOrchestrator(mock<CopilotPort>(), mock<PromptReaderPort>(), mock<I18nPort>())

    @Test
    fun `each known state survives normalization`() {
        assertEquals(SprintReviewState.BEARBEITET, orchestrator.normalizeState("BEARBEITET"))
        assertEquals(SprintReviewState.OFFENE_FRAGE, orchestrator.normalizeState("OFFENE_FRAGE"))
        assertEquals(SprintReviewState.REFINEMENT, orchestrator.normalizeState("REFINEMENT"))
    }

    @Test
    fun `lower case known states are lifted before matching`() {
        assertEquals(SprintReviewState.BEARBEITET, orchestrator.normalizeState("bearbeitet"))
        assertEquals(SprintReviewState.REFINEMENT, orchestrator.normalizeState("refinement"))
    }

    @Test
    fun `a hash collision of a known state still normalizes to failed`() {
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("BEARBEITF5"))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("OFFENE_FRAH&"))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("REFINEMEO5"))
    }

    @Test
    fun `an unknown state is normalized to failed`() {
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState("ERLEDIGT"))
        assertEquals(SprintReviewState.FAILED, orchestrator.normalizeState(""))
    }
}
