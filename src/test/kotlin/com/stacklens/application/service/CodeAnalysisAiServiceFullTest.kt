package com.stacklens.application.service

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.codeanalysis.CodeAnalysisAi
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CodeAnalysisAiServiceFullTest {
    private lateinit var copilotCliService: CopilotPort
    private lateinit var promptLoader: PromptReaderPort
    private lateinit var service: CodeAnalysisAiService

    @BeforeEach
    fun setup() {
        copilotCliService = mock()
        promptLoader = mock()
        service =
            CodeAnalysisAiService(
                copilotCliService,
                promptLoader,
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any(), any(), any())).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            val vars = invocation.arguments[2] as? Map<*, *> ?: emptyMap<Any, Any>()
            "PROMPT[$key|${vars.entries.joinToString(",") { "${it.key}=${it.value}" }}]"
        }
    }

    private fun buildAnalysis(): CodeAnalysis =
        CodeAnalysis(
            id = 42L,
            status = AnalysisStatus.RUNNING,
        )

    @Test
    fun `buildAnalysisPrompt calls resolveLanguage`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "",
            fileContents = "",
        )
        verify(copilotCliService).resolveLanguage()
    }

    @Test
    fun `buildAnalysisPrompt loads summaryFormatHint prompt`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "",
            fileContents = "",
        )
        verify(promptLoader).get(eq("en"), eq("codeanalysis.summaryFormatHint"), any())
    }

    @Test
    fun `buildAnalysisPrompt loads mermaidSection prompt when includeMermaid true`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "",
            fileContents = "",
            includeMermaid = true,
        )
        verify(promptLoader).get(eq("en"), eq("codeanalysis.mermaidSection"), any())
    }

    @Test
    fun `buildAnalysisPrompt does not load mermaidSection when includeMermaid false`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "",
            fileContents = "",
            includeMermaid = false,
        )
        org.mockito.kotlin.verify(promptLoader, org.mockito.kotlin.never()).get(
            eq("en"),
            eq("codeanalysis.mermaidSection"),
            any(),
        )
    }

    @Test
    fun `buildAnalysisPrompt loads drawioSection prompt when includeDrawio true`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "",
            fileContents = "",
            includeDrawio = true,
        )
        verify(promptLoader).get(eq("en"), eq("codeanalysis.drawioSection"), any())
    }

    @Test
    fun `buildAnalysisPrompt does not load drawioSection when includeDrawio false`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "",
            fileContents = "",
            includeDrawio = false,
        )
        org.mockito.kotlin.verify(promptLoader, org.mockito.kotlin.never()).get(
            eq("en"),
            eq("codeanalysis.drawioSection"),
            any(),
        )
    }

    @Test
    fun `buildAnalysisPrompt truncates large projectTree and fileContents`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "x".repeat(5000),
            fileContents = "y".repeat(50_000),
        )
    }

    @Test
    fun `buildAnalysisPrompt uses default false for includeMermaid and includeDrawio`() {
        service.buildAnalysisPrompt(
            projectName = "p",
            projectPath = "/p",
            projectTree = "",
            fileContents = "",
        )
    }

    @Test
    fun `buildAnalysisPrompt passes project metadata to analysis prompt`() {
        service.buildAnalysisPrompt(
            projectName = "my-project",
            projectPath = "/tmp/my-project",
            projectTree = "src/",
            fileContents = "fun foo() {}",
        )
        verify(promptLoader).get(
            eq("en"),
            eq("codeanalysis.analysisPrompt"),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `applyAiOutput extracts summary from well-formed output`() {
        val analysis = buildAnalysis()
        val output =
            """
            SUMMARY: This is a summary.
            FINDINGS: [{"severity":"HIGH","file":"Foo.kt"}]
            FILE_CHANGES: [{"path":"Foo.kt","action":"modify"}]
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)
        assertEquals("This is a summary.", analysis.summaryText)
        assertEquals(AnalysisStatus.COMPLETED, analysis.status)
        assertNotNull(analysis.findingsJson)
        assertNotNull(analysis.fileChangesJson)
        assertNotNull(analysis.completedAt)
    }

    @Test
    fun `applyAiOutput falls back to loose marker extraction when strict summary blank`() {
        val analysis = buildAnalysis()
        val output =
            """
            **Summary:** A loose summary line.
            FINDINGS: [{"severity":"LOW"}]
            FILE_CHANGES: [{"path":"X.kt"}]
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)
        assertTrue(analysis.summaryText!!.contains("A loose summary line"))
    }

    @Test
    fun `applyAiOutput uses freeform preamble when no markers present`() {
        val analysis = buildAnalysis()
        val output =
            """
            This is a freeform preamble paragraph.
            It explains the result in prose without any markers.

            ```json
            [{"key":"value"}]
            ```
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)
        assertNotNull(analysis.summaryText)
        assertTrue(analysis.summaryText!!.contains("freeform preamble paragraph"))
    }

    @Test
    fun `applyAiOutput sets summary to blank when output is blank`() {
        val analysis = buildAnalysis()
        service.applyAiOutput(analysis, "", includeMermaid = false, includeDrawio = false)
        assertEquals("", analysis.summaryText)
    }

    @Test
    fun `applyAiOutput extracts mermaid when includeMermaid true`() {
        val analysis = buildAnalysis()
        val output =
            """
            SUMMARY: ok
            FINDINGS: []
            FILE_CHANGES: []
            ```mermaid
            graph TD; A-->B
            ```
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = true, includeDrawio = false)
        assertNotNull(analysis.mermaid)
        assertTrue(analysis.mermaid!!.contains("graph TD"))
    }

    @Test
    fun `applyAiOutput extracts drawioXml when includeDrawio true`() {
        val analysis = buildAnalysis()
        val output =
            """
            SUMMARY: ok
            FINDINGS: []
            FILE_CHANGES: []
            <mxfile><diagram name="x"><mxGraphModel><root></root></mxGraphModel></diagram></mxfile>
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = true)
        assertNotNull(analysis.drawioXml)
    }

    @Test
    fun `applyAiOutput does not set mermaid when includeMermaid false`() {
        val analysis = buildAnalysis()
        val output =
            """
            SUMMARY: ok
            FINDINGS: []
            FILE_CHANGES: []
            ```mermaid
            graph TD; A-->B
            ```
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)
        assertNull(analysis.mermaid)
    }

    @Test
    fun `applyAiOutput does not set drawioXml when includeDrawio false`() {
        val analysis = buildAnalysis()
        val output =
            """
            SUMMARY: ok
            FINDINGS: []
            FILE_CHANGES: []
            <mxfile><diagram name="x"></diagram></mxfile>
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)
        assertNull(analysis.drawioXml)
    }

    @Test
    fun `applyAiOutput extracts findings using fallback variant when strict missing`() {
        val analysis = buildAnalysis()
        val output =
            """
            SUMMARY: ok
            ## Findings
            [{"severity":"MEDIUM","file":"Y.kt"}]
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)
        assertNotNull(analysis.findingsJson)
        assertTrue(analysis.findingsJson!!.contains("MEDIUM"))
    }

    @Test
    fun `applyAiOutput extracts file changes using fallback variant when strict missing`() {
        val analysis = buildAnalysis()
        val output =
            """
            SUMMARY: ok
            FINDINGS: []
            Datei-Änderungen: [{"path":"Z.kt"}]
            """.trimIndent()
        service.applyAiOutput(analysis, output, includeMermaid = false, includeDrawio = false)
        assertNotNull(analysis.fileChangesJson)
        assertTrue(analysis.fileChangesJson!!.contains("Z.kt"))
    }

    @Test
    fun `extractMermaid returns null when no mermaid block`() {
        val result = service.extractMermaid("no mermaid here")
        assertNull(result)
    }

    @Test
    fun `extractMermaid returns the mermaid block when present`() {
        val text =
            """
            some intro
            ```mermaid
            graph TD; A-->B
            ```
            trailing
            """.trimIndent()
        val result = service.extractMermaid(text)
        assertNotNull(result)
        assertTrue(result!!.contains("graph TD"))
    }

    @Test
    fun `extractDrawioXml returns null when no xml block`() {
        val result = service.extractDrawioXml("no xml")
        assertNull(result)
    }

    @Test
    fun `extractDrawioXml returns xml when present`() {
        val text =
            """
            intro
            <mxfile><diagram name="x"><mxGraphModel><root></root></mxGraphModel></diagram></mxfile>
            trailing
            """.trimIndent()
        val result = service.extractDrawioXml(text)
        assertNotNull(result)
        assertTrue(result!!.contains("mxfile"))
    }

    @Test
    fun `sanitizeDrawioXml delegates to DrawioSupport and returns sanitized output`() {
        val input = "<mxfile bad=\"x\"></mxfile>"
        val result = service.sanitizeDrawioXml(input)
        assertNotNull(result)
    }

    @Test
    fun `extractSection returns text between markers`() {
        val text = "START middle END more"
        val result = service.extractSection(text, "START", "END")
        assertEquals("middle", result)
    }

    @Test
    fun `extractSection returns empty when start marker missing`() {
        val text = "nothing here"
        val result = service.extractSection(text, "START", "END")
        assertEquals("", result)
    }

    @Test
    fun `extractSection returns tail to end when no end marker`() {
        val text = "START text without end"
        val result = service.extractSection(text, "START", "NOEND")
        assertEquals("text without end", result)
    }

    @Test
    fun `extractJsonBlock returns null when section marker missing`() {
        val result = service.extractJsonBlock("text", "MISSING", "END")
        assertNull(result)
    }

    @Test
    fun `extractJsonBlock extracts array between markers`() {
        val text = "SEC [1,2,3] END"
        val result = service.extractJsonBlock(text, "SEC", "END")
        assertNotNull(result)
        assertTrue(result!!.startsWith("["))
        assertTrue(result.endsWith("]"))
    }

    @Test
    fun `extractJsonBlock returns section when no end marker present`() {
        val text = "SEC [1,2,3]"
        val result = service.extractJsonBlock(text, "SEC", "NOEND")
        assertNotNull(result)
        assertTrue(result!!.startsWith("["))
    }

    @Test
    fun `extractJsonBlockAfter returns null when marker missing`() {
        val result = service.extractJsonBlockAfter("text", "MISSING")
        assertNull(result)
    }

    @Test
    fun `extractJsonBlockAfter extracts array after marker`() {
        val text = "MARKER [1,2,3] trailing"
        val result = service.extractJsonBlockAfter(text, "MARKER")
        assertNotNull(result)
        assertTrue(result!!.startsWith("["))
    }

    @Test
    fun `extractFirstJsonArray returns null when no array`() {
        assertNull(service.extractFirstJsonArray("no json here"))
    }

    @Test
    fun `extractFirstJsonArray extracts json code block`() {
        val text =
            """
            intro
            ```json
            [{"key":"value"}]
            ```
            trailing
            """.trimIndent()
        val result = service.extractFirstJsonArray(text)
        assertNotNull(result)
        assertTrue(result!!.contains("\"key\":\"value\""))
    }

    @Test
    fun `extractFirstJsonArray extracts raw array when no code block`() {
        val text = "intro [a,b,c] trailing"
        val result = service.extractFirstJsonArray(text)
        assertNotNull(result)
        assertEquals("[a,b,c]", result)
    }

    @Test
    fun `extractFirstJsonArray returns null when only opening bracket`() {
        assertNull(service.extractFirstJsonArray("only [bracket"))
    }

    @Test
    fun `findMarkerSection picks earliest match when multiple variants present`() {
        val text = "FINDINGS: x BEFUNDE: y"
        val result = service.findMarkerSection(text, listOf("FINDINGS:", "BEFUNDE:"))
        assertNotNull(result)
        assertTrue(result!!.contains("x"))
    }

    @Test
    fun `findMarkerSection skips whitespace colon and star after marker`() {
        val text = "FINDINGS:**\ncontent here"
        val result = service.findMarkerSection(text, listOf("FINDINGS:"))
        assertNotNull(result)
        assertTrue(result!!.contains("content here"))
    }

    @Test
    fun `extractSectionLoose returns tail without trailing whitespace when no end`() {
        val text = "FINDINGS: content"
        val result = service.extractSectionLoose(text, listOf("FINDINGS"), listOf("NONE"))
        assertTrue(result.contains("content"))
    }

    @Test
    fun `extractSectionLoose picks earliest start across words`() {
        val text = "X then SUMMARY: middle END"
        val result = service.extractSectionLoose(text, listOf("X", "SUMMARY"), listOf("END"))
        assertTrue(result.contains("then SUMMARY"))
    }

    @Test
    fun `extractFindingsJson returns null when only file-changes marker present`() {
        val result = service.extractFindingsJson("FILE_CHANGES: [{}]")
        assertNull(result)
    }

    @Test
    fun `extractFindingsJson falls through to variant when primary path returns nothing`() {
        val text =
            """
            SUMMARY: ok
            Befunde:
            [{"sev":"LOW"}]
            Datei-Änderungen:
            [{"path":"Q"}]
            """.trimIndent()
        val result = service.extractFindingsJson(text)
        assertNotNull(result)
        assertTrue(result!!.contains("LOW"))
    }

    @Test
    fun `extractFileChangesJson returns null when no marker at all`() {
        assertNull(service.extractFileChangesJson("no markers"))
    }

    @Test
    fun `extractFileChangesJson uses variant marker when strict marker absent`() {
        val text =
            """
            FILE CHANGES: [{"path":"F.kt"}]
            """.trimIndent()
        val result = service.extractFileChangesJson(text)
        assertNotNull(result)
        assertTrue(result!!.contains("F.kt"))
    }

    @Test
    fun `service implements CodeAnalysisAi`() {
        assertNotNull(service)
        assertNotNull(CodeAnalysisAi::class.java.declaredMethods.firstOrNull())
    }
}
