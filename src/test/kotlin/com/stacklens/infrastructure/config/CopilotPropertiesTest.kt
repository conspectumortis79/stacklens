package com.stacklens.infrastructure.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CopilotPropertiesTest {
    @Test
    fun `CopilotProperties returns default timeout values`() {
        val props = CopilotProperties()
        assertEquals(1800L, props.pipelineTimeoutSeconds)
        assertEquals(600L, props.loadTestAnalysisTimeoutSeconds)
        assertEquals(600L, props.searchAnalysisTimeoutSeconds)
    }
}
