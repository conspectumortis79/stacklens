package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AiAnalysisTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val a = AiAnalysis(sessionId = 1L)
        assertEquals(0L, a.id)
        assertEquals(1L, a.sessionId)
        assertEquals("PENDING", a.status)
        assertNull(a.analysisText)
        assertNull(a.drawioXml)
        assertNull(a.mermaid)
        assertNull(a.modelUsed)
        assertNull(a.inputTokens)
        assertNull(a.outputTokens)
        assertNull(a.errorMessage)
        assertNull(a.completedAt)
    }

    @Test
    fun `named constructor populates all fields`() {
        val a =
            AiAnalysis(
                id = 7L,
                sessionId = 2L,
                status = "COMPLETED",
                analysisText = "analysis",
                drawioXml = "<mxfile/>",
                mermaid = "graph TD;",
                modelUsed = "gpt-4",
                inputTokens = 100,
                outputTokens = 200,
                errorMessage = null,
            )
        assertEquals(7L, a.id)
        assertEquals(2L, a.sessionId)
        assertEquals("COMPLETED", a.status)
        assertEquals("analysis", a.analysisText)
        assertEquals("<mxfile/>", a.drawioXml)
        assertEquals("graph TD;", a.mermaid)
        assertEquals("gpt-4", a.modelUsed)
        assertEquals(100, a.inputTokens)
        assertEquals(200, a.outputTokens)
    }
}
