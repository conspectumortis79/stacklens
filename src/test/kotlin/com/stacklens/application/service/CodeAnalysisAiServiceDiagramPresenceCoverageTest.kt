package com.stacklens.application.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory

class CodeAnalysisAiServiceDiagramPresenceCoverageTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val extractor = DefaultAiResponseJsonExtractor()
    private val service = CodeAnalysisAiService(copilot, promptLoader, extractor, clock = com.stacklens.adapter.out.time.SystemClockPort)
    private val logger = LoggerFactory.getLogger(CodeAnalysisAiService::class.java) as Logger

    @BeforeEach
    fun enableDebug() {
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun disableDebug() {
        logger.level = null
    }

    private fun analysis() =
        CodeAnalysis(
            id = 3L,
            status = "RUNNING",
        )

    private val output =
        """
        SUMMARY: everything fine
        FINDINGS: []
        ```json
        {"findings":[]}
        ```
        MERMAID:
        ```mermaid
        graph TD; A-->B;
        ```
        DRAWIO:
        ```xml
        <mxfile><diagram/></mxfile>
        ```
        """.trimIndent()

    @Test
    fun `rendered diagrams are reported with their character count`() {
        val analysis = analysis()

        service.applyAiOutput(analysis, output, includeMermaid = true, includeDrawio = true)

        assertEquals("graph TD; A-->B;", analysis.mermaid)
        assertEquals("<mxfile><diagram/></mxfile>", analysis.drawioXml)
    }

    @Test
    fun `disabled diagram extraction is reported as NULL`() {
        val analysis = analysis()

        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)

        assertNull(analysis.mermaid)
        assertNull(analysis.drawioXml)
    }

    @Test
    fun `a diagram section that disappears between the check and the length read is still reported`() {
        val analysis = mock<CodeAnalysis>()
        whenever(analysis.mermaid).thenReturn("graph TD;", null)
        whenever(analysis.drawioXml).thenReturn("<mxfile/>", null)

        service.applyAiOutput(analysis, output, includeMermaid = true, includeDrawio = true)

        verify(analysis).summaryText = anyOrNull()
    }

    @Test
    fun `the earliest marker of the list wins regardless of its order`() {
        val text = "ZUSAMMENFASSUNG: spaeter\nSUMMARY: frueher\n"

        assertEquals("spaeter\nSUMMARY: frueher\n", service.findMarkerSection(text, listOf("SUMMARY:", "ZUSAMMENFASSUNG:")))
        assertEquals("frueher\n", service.findMarkerSection(text, listOf("SUMMARY:")))
        assertNull(service.findMarkerSection(text, listOf("MISSING:", "ABSENT:")))
    }
}
