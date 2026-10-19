package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentStage
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CopilotAgentPipelineStageModelTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `AgentStage initial state`() {
        val stage = AgentStage("nora")
        assertEquals(StageStatus.PENDING, stage.status)
        assertEquals("", stage.output)
    }
}
