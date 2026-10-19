package com.stacklens.application.service

import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CodeAnalysisAiServiceMissedBranchTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val extractor: AiResponseJsonExtractorPort = DefaultAiResponseJsonExtractor()
    private val service = CodeAnalysisAiService(copilot, promptLoader, extractor, clock = com.stacklens.adapter.out.time.SystemClockPort)

    @Test
    fun `a fence at the very beginning is dropped instead of cut off`() {
        val analysis = CodeAnalysis()

        service.applyAiOutput(analysis, "```mermaid\ngraph TD\nA-->B\n```", includeMermaid = false, includeDrawio = false)

        assertNotNull(analysis.summaryText)
        assertNull(analysis.mermaid)
        assertNull(analysis.drawioXml)
    }

    @Test
    fun `prose before the first fence becomes the summary`() {
        val analysis = CodeAnalysis()

        service.applyAiOutput(analysis, "SYSTEM has one module\n```json\n{}\n```", includeMermaid = false, includeDrawio = false)

        assertEquals("SYSTEM has one module", analysis.summaryText)
    }

    @Test
    fun `mermaid and drawio blocks are reported with their length when requested`() {
        val output =
            """
            SUMMARY: everything fine
            ```mermaid
            graph TD
            A-->B
            ```
            <mxfile><diagram id="1">x</diagram></mxfile>
            """.trimIndent()
        val analysis = CodeAnalysis()

        service.applyAiOutput(analysis, output, includeMermaid = true, includeDrawio = true)

        assertNotNull(analysis.mermaid)
        assertNotNull(analysis.drawioXml)
    }

    @Test
    fun `mermaid and drawio are skipped when their flags are off`() {
        val analysis = CodeAnalysis()

        service.applyAiOutput(analysis, "SUMMARY: fine\n```mermaid\ngraph TD\nA-->B\n```", includeMermaid = false, includeDrawio = false)

        assertNull(analysis.mermaid)
        assertNull(analysis.drawioXml)
    }

    @Test
    fun `the earliest marker wins over later duplicates`() {
        val text = "alpha FILE_CHANGES: first body\nbeta file changes: second body"

        val section = service.findMarkerSection(text, listOf("FILE_CHANGES:", "file changes:"))

        assertEquals("first body\nbeta file changes: second body", section)
        assertNull(service.findMarkerSection("nothing at all", listOf("FILE_CHANGES:")))
        assertEquals("body", service.findMarkerSection("MARKER:**  body", listOf("marker:"))?.trim())
    }

    @Test
    fun `a loose section reads to the earliest end word and stops at the text border`() {
        assertEquals("", service.extractSectionLoose("no word here", listOf("summary"), listOf("findings")))
        assertEquals("", service.extractSectionLoose("SUMMARY:** ", listOf("summary"), listOf("findings")))
        assertEquals(
            "text",
            service.extractSectionLoose("Zusammenfassung: text Befunde: x", listOf("summary", "zusammenfassung"), listOf("findings", "befunde")).trim(),
        )
        assertEquals(
            "text until the end",
            service.extractSectionLoose("SUMMARY: text until the end", listOf("summary"), listOf("findings")).trim(),
        )
    }
}
