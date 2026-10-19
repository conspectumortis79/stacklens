package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CopilotCliAdapterCompanionTest {
    @Test
    fun `Companion processRunner is non-null and returns a CopilotProcessRunner instance`() {
        val pr = CopilotCliAdapter.processRunner
        assertNotNull(pr)
    }
}
