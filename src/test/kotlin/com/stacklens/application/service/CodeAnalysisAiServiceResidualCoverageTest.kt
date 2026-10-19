package com.stacklens.application.service

import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CodeAnalysisAiServiceResidualCoverageTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val extractor: AiResponseJsonExtractorPort = DefaultAiResponseJsonExtractor()
    private val service = CodeAnalysisAiService(copilot, promptLoader, extractor, clock = com.stacklens.adapter.out.time.SystemClockPort)

    @Test
    fun `requested drawio stays null when the output contains no diagram`() {
        val analysis = CodeAnalysis()

        service.applyAiOutput(analysis, "SUMMARY: all good", includeMermaid = false, includeDrawio = true)

        assertNull(analysis.drawioXml)
    }

    @Test
    fun `requested mermaid stays null when the output contains no diagram`() {
        val analysis = CodeAnalysis()

        service.applyAiOutput(analysis, "SUMMARY: all good", includeMermaid = true, includeDrawio = false)

        assertNull(analysis.mermaid)
    }

    @Test
    fun `marker section prefixes are stripped in every flavour`() {
        assertEquals("body", service.findMarkerSection("M: body", listOf("m:"))?.trim())
        assertEquals("body", service.findMarkerSection("M* body", listOf("m*"))?.trim())
        assertEquals("body", service.findMarkerSection("M   body", listOf("m"))?.trim())
        assertEquals("body", service.findMarkerSection("M:*  body", listOf("m"))?.trim())
        assertEquals("body", service.findMarkerSection("M *body", listOf("m "))?.trim())
    }
}
