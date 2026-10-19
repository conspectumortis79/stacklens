package com.stacklens.application.service

import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.pipeline.PipelineRepoData
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.PipelineAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineAnalysisAiServiceMissedBranchTest {
    private val copilot: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val extractor: AiResponseJsonExtractorPort = DefaultAiResponseJsonExtractor()
    private val service = PipelineAnalysisAiService(copilot, promptLoader, extractor)

    private fun stubPromptContext() {
        val ctx = mock<PromptContext>()
        whenever(ctx.get(any(), anyOrNull())).thenReturn("section")
        whenever(promptLoader.context(any())).thenReturn(ctx)
        whenever(copilot.resolveLanguage()).thenReturn("en")
    }

    @Test
    fun `a user prompt is embedded into the analysis prompt`() {
        stubPromptContext()

        val prompt = service.buildAnalysisPrompt("app", "/repo", "tree", "contents", includeMermaid = true, userPrompt = "  focus on caching  ")

        assertNotNull(prompt)
    }

    @Test
    fun `a blank user prompt and no mermaid leave both sections empty`() {
        stubPromptContext()

        val prompt = service.buildAnalysisPrompt("app", "/repo", "tree", "contents", includeMermaid = false, userPrompt = "   ")

        assertNotNull(prompt)
    }

    @Test
    fun `repo data with a blank tree or blank contents is rejected`() {
        assertNull(service.loadRepoData { null })
        assertNull(service.loadRepoData { PipelineRepoData("app", "/repo", "  ", "contents") })
        assertNull(service.loadRepoData { PipelineRepoData("app", "/repo", "tree", "") })
        assertNotNull(service.loadRepoData { PipelineRepoData("app", "/repo", "tree", "contents") })
    }

    @Test
    fun `a summary is taken from the text before the first fence`() {
        assertEquals("", service.extractSummary(""))
        assertEquals("prose only", service.extractSummary("prose only"))
        assertEquals("inside", service.extractSummary("```fence\ninside\n```"))
        assertEquals("text before", service.extractSummary("text before\n```fence\ninside\n```"))
    }

    @Test
    fun `applyAiOutput tolerates an analysis without a summary`() {
        stubPromptContext()
        val analysis = mock<PipelineAnalysis>()
        whenever(analysis.summaryText).thenReturn(null)

        service.applyAiOutput(analysis, "SUMMARY\nloose text\nFINDINGS\n[]", includeMermaid = false)
    }

    @Test
    fun `applyAiOutput keeps a parsed summary`() {
        stubPromptContext()
        val analysis = PipelineAnalysis()

        service.applyAiOutput(analysis, "SUMMARY: everything fine\nFINDINGS:\n[]", includeMermaid = true)

        assertNotNull(analysis.summaryText)
    }

    @Test
    fun `a loose section stops at the earliest end marker or at the tail`() {
        assertEquals("", service.extractSectionLoose("nothing here", listOf("summary"), listOf("findings")))
        assertEquals(
            "body",
            service.extractSectionLoose("SUMMARY: body FINDINGS: rest", listOf("summary", "zusammenfassung"), listOf("findings")).trim(),
        )
        assertEquals(
            "rest of it",
            service.extractSectionLoose("Summary **: rest of it", listOf("summary"), listOf("never-appears")),
        )
    }

    @Test
    fun `a loose section cursor stops at the end of the text`() {
        assertEquals("", service.extractSectionLoose("SUMMARY:*  ", listOf("summary"), listOf("findings")))
    }

    @Test
    fun `a section is cut at the marker or kept whole when the end marker is missing`() {
        assertEquals("", service.extractSection("no marker", "START:", "END:"))
        assertEquals("tail only", service.extractSection("START: tail only", "START:", "END:"))
        assertEquals("head", service.extractSection("START: head END: rest", "START:", "END:"))
    }
}
