package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CopilotAgentPipelineRunLookupTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `get returns null for unknown id`() {
        val svc = makeService()
        assertNull(svc.get(99L))
    }
}
