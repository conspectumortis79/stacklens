package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiAnalysisControllerDtosTest {
    @Test
    fun `StartAnalysisRequest defaults both flags to false`() {
        val req = StartAnalysisRequest()
        assertFalse(req.includeMermaid)
        assertFalse(req.includeDrawio)
    }

    @Test
    fun `StartAnalysisRequest can enable both flags`() {
        val req = StartAnalysisRequest(includeMermaid = true, includeDrawio = true)
        assertTrue(req.includeMermaid)
        assertTrue(req.includeDrawio)
    }

    @Test
    fun `StartAnalysisRequest supports partial flags`() {
        val mermaidOnly = StartAnalysisRequest(includeMermaid = true)
        assertTrue(mermaidOnly.includeMermaid)
        assertFalse(mermaidOnly.includeDrawio)

        val drawioOnly = StartAnalysisRequest(includeDrawio = true)
        assertFalse(drawioOnly.includeMermaid)
        assertTrue(drawioOnly.includeDrawio)
    }

    @Test
    fun `SaveDiagramRequest stores path filename and content`() {
        val req =
            SaveDiagramRequest(
                path = "/tmp/diagrams",
                filename = "flow.drawio",
                content = "<mxfile>...</mxfile>",
            )
        assertEquals("/tmp/diagrams", req.path)
        assertEquals("flow.drawio", req.filename)
        assertEquals("<mxfile>...</mxfile>", req.content)
    }

    @Test
    fun `SaveDiagramRequest equality holds by field value`() {
        val a = SaveDiagramRequest(path = "p", filename = "f", content = "c")
        val b = SaveDiagramRequest(path = "p", filename = "f", content = "c")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
