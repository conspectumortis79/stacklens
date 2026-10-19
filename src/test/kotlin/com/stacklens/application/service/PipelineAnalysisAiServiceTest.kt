package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.PipelineAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineAnalysisAiServiceTest {
    private lateinit var copilot: CopilotPort
    private lateinit var promptLoader: PromptReaderPort
    private lateinit var service: PipelineAnalysisAiService

    private val promptTemplates =
        mapOf(
            "pipeline.summaryFormatHint" to "(2–4 sentences)",
            "pipeline.mermaidSection" to "MERMAID_SECTION",
            "pipeline.userPromptSection" to "USER_PROMPT_SECTION({userPrompt})",
            "pipeline.analysisPrompt" to
                "Project: {projectName}\nPath: {projectPath}\n" +
                "Tree:\n{projectTree}\n\nFiles:\n{fileContents}\n" +
                "{mermaidSection}\n{userPromptSection}\n{summaryFormatHint}",
        )

    @BeforeEach
    fun setup() {
        copilot = mock()
        promptLoader = mock()
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.context(any())).thenReturn(PromptContext(promptTemplates))
        service =
            PipelineAnalysisAiService(
                copilot,
                promptLoader,
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
    }

    @Test
    fun `buildAnalysisPrompt substitutes all placeholders`() {
        val prompt =
            service.buildAnalysisPrompt(
                projectName = "myrepo",
                projectPath = "/repo",
                projectTree = "tree",
                fileContents = "contents",
                includeMermaid = true,
                userPrompt = null,
            )
        assertTrue(prompt.contains("Project: myrepo"))
        assertTrue(prompt.contains("Path: /repo"))
        assertTrue(prompt.contains("tree"))
        assertTrue(prompt.contains("contents"))
        assertTrue(prompt.contains("MERMAID_SECTION"))
        assertTrue(prompt.contains("\n\n(2"))
    }

    @Test
    fun `buildAnalysisPrompt omits Mermaid section when disabled`() {
        val prompt =
            service.buildAnalysisPrompt(
                projectName = "x",
                projectPath = "/x",
                projectTree = "t",
                fileContents = "c",
                includeMermaid = false,
                userPrompt = null,
            )
        assertFalse(prompt.contains("MERMAID_SECTION"))
    }

    @Test
    fun `buildAnalysisPrompt appends user instructions when provided`() {
        val prompt =
            service.buildAnalysisPrompt(
                projectName = "x",
                projectPath = "/x",
                projectTree = "t",
                fileContents = "c",
                includeMermaid = true,
                userPrompt = "Focus on security",
            )
        assertTrue(prompt.contains("Focus on security"))
    }

    @Test
    fun `loadRepoData returns null when loader returns null`() {
        assertNull(service.loadRepoData { null })
    }

    @Test
    fun `loadRepoData returns null when loader returns empty tree`() {
        assertNull(
            service.loadRepoData {
                com.stacklens.application.port.out.pipeline.PipelineRepoData(
                    appName = "x",
                    repoPath = "/x",
                    tree = "",
                    contents = "c",
                )
            },
        )
    }

    @Test
    fun `loadRepoData returns data when both tree and contents are non-blank`() {
        val data =
            com.stacklens.application.port.out.pipeline.PipelineRepoData(
                appName = "x",
                repoPath = "/x",
                tree = "docker-compose.yml",
                contents = "content",
            )
        val result = service.loadRepoData { data }
        assertNotNull(result)
        assertEquals("x", result!!.appName)
    }

    @Test
    fun `applyAiOutput extracts summary, findings, descriptions, optimizations and mermaid`() {
        val output =
            """
            SUMMARY:
            This repository has a small docker-compose stack with a couple of issues.

            FINDINGS:
            ```json
            [
              {"id":"f1","file":"docker-compose.yml","type":"COMPOSE_WARNING","severity":"MEDIUM","description":"Use :latest tag"},
              {"id":"f2","file":"Dockerfile","type":"COMPOSE_SMELL","severity":"LOW","description":"Large image"}
            ]
            ```

            FILE_DESCRIPTIONS:
            ```json
            [
              {"path":"docker-compose.yml","fileKind":"docker-compose","description":"top-level stack","keys":[{"key":"version","value":"3.9","meaning":"compose format"}]}
            ]
            ```

            OPTIMIZATIONS:
            ```json
            [
              {"title":"Pin the image tag","rationale":"reproducibility","patchSnippet":"image: myapp:1.4.2"}
            ]
            ```

            ```mermaid
            graph LR
              A --> B
            ```
            """.trimIndent()

        val analysis = PipelineAnalysis()
        service.applyAiOutput(analysis, output, includeMermaid = true)

        assertTrue(analysis.summaryText!!.contains("docker-compose stack"))
        val findings = extractJsonArray(analysis.findingsJson!!)
        assertEquals(2, findings.size)
        val descs = extractJsonArray(analysis.fileDescriptionsJson!!)
        assertEquals(1, descs.size)
        val opts = extractJsonArray(analysis.optimizationsJson!!)
        assertEquals(1, opts.size)
        assertNotNull(analysis.mermaid)
        assertTrue(analysis.mermaid!!.contains("graph LR"))
    }

    @Test
    fun `applyAiOutput tolerates case-variant markers`() {
        val output =
            """
            Summary:
            short summary.

            Findings:
            ```json
            []
            ```

            File Descriptions:
            ```json
            []
            ```

            Optimizations:
            ```json
            []
            ```
            """.trimIndent()

        val analysis = PipelineAnalysis()
        service.applyAiOutput(analysis, output, includeMermaid = false)

        assertEquals("short summary.", analysis.summaryText?.trim())
        assertEquals("[]", analysis.findingsJson)
        assertEquals("[]", analysis.fileDescriptionsJson)
        assertEquals("[]", analysis.optimizationsJson)
        assertNull(analysis.mermaid)
    }

    @Test
    fun `applyAiOutput falls back to freeform prose when no markers present`() {
        val analysis = PipelineAnalysis()
        service.applyAiOutput(analysis, "Just a few sentences without any markers.", includeMermaid = false)
        assertNotNull(analysis.summaryText)
        assertTrue(analysis.summaryText!!.contains("Just a few sentences"))
        assertNull(analysis.findingsJson)
        assertNull(analysis.fileDescriptionsJson)
    }

    @Test
    fun `applyAiOutput handles output with leading fence-free prose then no markers`() {
        val analysis = PipelineAnalysis()
        service.applyAiOutput(
            analysis,
            "This is just plain prose with no fence and no summary marker.",
            includeMermaid = false,
        )
        assertNotNull(analysis.summaryText)
    }

    private fun extractJsonArray(s: String): List<Map<String, Any?>> {
        val mapper = ObjectMapper()
        return try {
            mapper.readValue(s)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
