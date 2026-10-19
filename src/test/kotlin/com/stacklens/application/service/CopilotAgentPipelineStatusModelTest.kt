package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CopilotAgentPipelineStatusModelTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `PipelineStatus and StageStatus enum values`() {
        assertEquals(PipelineStatus.PENDING, PipelineStatus.valueOf("PENDING"))
        assertEquals(PipelineStatus.RUNNING, PipelineStatus.valueOf("RUNNING"))
        assertEquals(PipelineStatus.DONE, PipelineStatus.valueOf("DONE"))
        assertEquals(PipelineStatus.ERROR, PipelineStatus.valueOf("ERROR"))
        assertEquals(StageStatus.PENDING, StageStatus.valueOf("PENDING"))
        assertEquals(StageStatus.RUNNING, StageStatus.valueOf("RUNNING"))
        assertEquals(StageStatus.DONE, StageStatus.valueOf("DONE"))
        assertEquals(StageStatus.ERROR, StageStatus.valueOf("ERROR"))
    }
}
