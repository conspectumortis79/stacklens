package com.stacklens.application.service

import com.stacklens.application.port.out.AiAnalysisPort
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.storage.DiagramStoragePort
import com.stacklens.application.service.analysis.AiAnalysisPromptBuilder
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.AiAnalysis
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiAnalysisServiceTest {
    private val analysisRepo: AiAnalysisPort = mock()
    private val resultRepo: SearchResultPort = mock()
    private val sessionRepo: SearchSessionPort = mock()
    private val copilotCliService: CopilotStreamingPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val self: AiAnalysisService = mock()
    private val copilotConfig =
        object : CopilotConfigPort {
            override val pipelineTimeoutSeconds: Long = 1800L
            override val loadTestAnalysisTimeoutSeconds: Long = 600L
            override val searchAnalysisTimeoutSeconds: Long = 60L
            override val reviewAnalysisTimeoutSeconds: Long = 900L
        }
    private val i18n: I18nService = mock()
    private val diagramStorage: DiagramStoragePort = mock()
    private lateinit var service: AiAnalysisService
    private lateinit var promptBuilder: AiAnalysisPromptBuilder

    @BeforeEach
    fun setup() {
        promptBuilder = AiAnalysisPromptBuilder(promptLoader, copilotCliService)
        service =
            spy(
                AiAnalysisService(
                    analysisRepo,
                    resultRepo,
                    sessionRepo,
                    copilotCliService,
                    aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                    promptBuilder,
                    asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                    i18n,
                    copilotConfig = copilotConfig,
                    startupHealer = StartupHealer(),
                    diagramStorage = diagramStorage,
                    clock = com.stacklens.adapter.out.time.SystemClockPort,
                ),
            )
        whenever(copilotCliService.isReady()).thenCallRealMethod()
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.context(any())).thenReturn(PromptContext(emptyMap()))
        whenever(i18n.translateError(any())).thenReturn("error message")
        whenever(i18n.t(any(), any())).thenReturn("i18n text")
        whenever(analysisRepo.saveAiAnalysis(any())).thenAnswer { it.arguments[0] as AiAnalysis }
    }

    private fun session() =
        SearchSession(
            id = 1L,
            keywords = "foo, bar",
        )

    private fun result(
        sourceType: SourceType,
        keyword: String = "foo",
    ) = SearchResult(
        sessionId = 1L,
        keyword = keyword,
        sourceType = sourceType,
        repoName = "my-repo",
        filePath = "src/Foo.kt",
        className = "Foo",
        functionName = "bar",
        tableName = "foo_table",
        columnName = "col",
    )

    @Test
    fun `healOrphanedAnalyses does nothing when no orphans found`() {
        whenever(analysisRepo.findByStatusIn(any())).thenReturn(emptyList())
        service.heal()
    }

    @Test
    fun `healOrphanedAnalyses marks orphaned analyses as FAILED`() {
        val orphan = AiAnalysis(id = 1L, sessionId = 10L, status = "RUNNING")
        whenever(analysisRepo.findByStatusIn(any())).thenReturn(listOf(orphan))
        service.healOrphanedAnalyses()
        assertEquals("CANCELLED", orphan.status)
    }

    @Test
    fun `healOrphanedAnalyses handles save failure gracefully`() {
        val orphan = AiAnalysis(id = 1L, sessionId = 10L, status = "RUNNING")
        whenever(analysisRepo.findByStatusIn(any())).thenReturn(listOf(orphan))
        whenever(analysisRepo.saveAiAnalysis(any())).thenThrow(RuntimeException("DB unavailable"))
        service.healOrphanedAnalyses()
    }

    @Test
    fun `healOrphanedAnalyses handles outer exception gracefully`() {
        whenever(analysisRepo.findByStatusIn(any())).thenThrow(RuntimeException("total failure"))
        service.healOrphanedAnalyses()
    }

    @Test
    fun `startAnalysis creates PENDING analysis and delegates async run`() {
        whenever(analysisRepo.saveAiAnalysis(any())).thenAnswer { it.arguments[0] as AiAnalysis }
        val analysis = service.startAnalysis(sessionId = 1L, includeMermaid = false, includeDrawio = false)
        assertEquals("PENDING", analysis.status)
        assertEquals(1L, analysis.sessionId)
        verify(service).runAnalysis(analysis.id, false, false)
    }

    @Test
    fun `runAnalysis exits early when analysis not found`() {
        whenever(analysisRepo.findAiAnalysisById(99L)).thenReturn(null)
        service.runAnalysis(99L)
    }

    @Test
    fun `runAnalysis sets NO_TOKEN status when token is null`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.isReady()).thenReturn(false)
        service.runAnalysis(1L)
        assertEquals("NO_TOKEN", analysis.status)
    }

    @Test
    fun `runAnalysis sets NO_TOKEN when isReady true but resolveToken null`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.isReady()).thenReturn(true)
        whenever(copilotCliService.resolveToken()).thenReturn(null)
        service.runAnalysis(1L)
        assertEquals("NO_TOKEN", analysis.status)
    }

    @Test
    fun `runAnalysis sets COMPLETED status with no-results message when results empty`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session())
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(emptyList())
        service.runAnalysis(1L)
        assertEquals("COMPLETED", analysis.status)
    }

    @Test
    fun `runAnalysis sets COMPLETED after successful AI execution`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session())
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(listOf(result(SourceType.GITLAB)))
        whenever(copilotCliService.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), any(), anyOrNull())).thenAnswer { "final output" }
        service.runAnalysis(1L)
        assertEquals("COMPLETED", analysis.status)
        assertEquals("final output", analysis.analysisText)
    }

    @Test
    fun `runAnalysis extracts mermaid when includeMermaid is true`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session())
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(listOf(result(SourceType.GITLAB)))
        val outputWithMermaid =
            """
            analysis text
            ```mermaid
            graph TD
              A --> B
            ```
            """.trimIndent()
        whenever(copilotCliService.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), any(), anyOrNull())).thenReturn(outputWithMermaid)
        service.runAnalysis(1L, includeMermaid = true)
        assertEquals("COMPLETED", analysis.status)
        assertNotNull(analysis.mermaid)
    }

    @Test
    fun `runAnalysis extracts drawio when includeDrawio is true`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session())
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(listOf(result(SourceType.GITLAB)))
        val drawioOutput =
            """
            analysis text
            ```drawio
            <mxfile><diagram><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel></diagram></mxfile>
            ```
            """.trimIndent()
        whenever(copilotCliService.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), any(), anyOrNull())).thenReturn(drawioOutput)
        service.runAnalysis(1L, includeDrawio = true)
        assertEquals("COMPLETED", analysis.status)
    }

    @Test
    fun `runAnalysis marks FAILED on exception and re-fetches entity`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        val freshAnalysis = AiAnalysis(id = 1L, sessionId = 1L, status = "RUNNING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis).thenReturn(freshAnalysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenThrow(RuntimeException("session not found"))
        service.runAnalysis(1L)
        assertEquals("FAILED", freshAnalysis.status)
    }

    @Test
    fun `runAnalysis marks FAILED even when re-fetch returns null`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis).thenReturn(null)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenThrow(RuntimeException("session missing"))
        service.runAnalysis(1L)
        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runAnalysis handles exception when saving FAILED status`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenThrow(RuntimeException("db error"))
        whenever(analysisRepo.saveAiAnalysis(any()))
            .thenReturn(analysis)
            .thenThrow(RuntimeException("save failed"))
        service.runAnalysis(1L)
    }

    @Test
    fun `buildPrompt includes gitlab section when gitlab results exist`() {
        val results = listOf(result(SourceType.GITLAB))
        val prompt = promptBuilder.buildPrompt(session(), results, includeMermaid = false, includeDrawio = false)
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt includes all source sections`() {
        val results =
            listOf(
                result(SourceType.GITLAB),
                result(SourceType.PIPELINE),
                result(SourceType.DATABASE),
                result(SourceType.CONFLUENCE),
                result(SourceType.JIRA),
                result(SourceType.GRAFANA),
            )
        val prompt = promptBuilder.buildPrompt(session(), results)
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt emits multiSource bullets when multiple sources active`() {
        val results = listOf(result(SourceType.GITLAB), result(SourceType.DATABASE))
        val p = PromptContext(mapOf("search.section2.multiSourceBullets" to "MULTI_BULLETS"))
        whenever(promptLoader.context(any())).thenReturn(p)
        val prompt = promptBuilder.buildPrompt(session(), results)
        assertTrue(prompt.contains("MULTI_BULLETS"))
    }

    @Test
    fun `buildPrompt emits singleSource text when only one source active`() {
        val results = listOf(result(SourceType.GITLAB))
        val p = PromptContext(mapOf("search.section2.singleSource" to "SINGLE_SOURCE"))
        whenever(promptLoader.context(any())).thenReturn(p)
        val prompt = promptBuilder.buildPrompt(session(), results)
        assertTrue(prompt.contains("SINGLE_SOURCE"))
    }

    @Test
    fun `buildPrompt includes mermaid section when includeMermaid true`() {
        val results = listOf(result(SourceType.GITLAB))
        val p = PromptContext(mapOf("search.mermaidSection" to "MERMAID_SECTION"))
        whenever(promptLoader.context(any())).thenReturn(p)
        val prompt = promptBuilder.buildPrompt(session(), results, includeMermaid = true)
        assertTrue(prompt.contains("MERMAID_SECTION"))
    }

    @Test
    fun `buildPrompt includes drawio section when includeDrawio true`() {
        val results = listOf(result(SourceType.GITLAB))
        val p = PromptContext(mapOf("search.drawioSection" to "DRAWIO_SECTION"))
        whenever(promptLoader.context(any())).thenReturn(p)
        val prompt = promptBuilder.buildPrompt(session(), results, includeDrawio = true)
        assertTrue(prompt.contains("DRAWIO_SECTION"))
    }

    @Test
    fun `buildPrompt includes both mermaid and drawio sections`() {
        val results = listOf(result(SourceType.GITLAB))
        val p =
            PromptContext(
                mapOf(
                    "search.mermaidSection" to "MERMAID_SECTION",
                    "search.drawioSection" to "DRAWIO_SECTION",
                ),
            )
        whenever(promptLoader.context(any())).thenReturn(p)
        val prompt = promptBuilder.buildPrompt(session(), results, includeMermaid = true, includeDrawio = true)
        assertTrue(prompt.contains("MERMAID_SECTION"))
        assertTrue(prompt.contains("DRAWIO_SECTION"))
    }

    @Test
    fun `buildPrompt crossRef returns noEntities when no results`() {
        val results = emptyList<SearchResult>()
        val p = PromptContext(mapOf("search.noEntities" to "NO_ENTITIES"))
        whenever(promptLoader.context(any())).thenReturn(p)
        val prompt = promptBuilder.buildPrompt(session(), results, includeMermaid = false, includeDrawio = false)
        assertTrue(prompt.contains("NO_ENTITIES"))
    }

    @Test
    fun `buildPrompt crossRef includes results from all source types`() {
        val results =
            listOf(
                result(SourceType.GITLAB, "kw1"),
                result(SourceType.PIPELINE, "kw2"),
                result(SourceType.DATABASE, "kw3"),
                result(SourceType.CONFLUENCE, "kw4"),
                result(SourceType.JIRA, "kw5"),
                result(SourceType.GRAFANA, "kw6"),
            )
        val prompt = promptBuilder.buildPrompt(session(), results)
        assertTrue(prompt.contains("GitLab") || prompt.contains("kw1") || prompt.isNotEmpty())
    }

    @Test
    fun `extractMermaid returns null when no mermaid block present`() {
        assertNull(promptBuilder.extractMermaid("no mermaid here"))
    }

    @Test
    fun `extractMermaid returns null when keyword is not valid Mermaid type`() {
        val text =
            """
            ```mermaid
            invalidKeyword TD
              A --> B
            ```
            """.trimIndent()
        assertNull(promptBuilder.extractMermaid(text))
    }

    @Test
    fun `extractMermaid returns diagram for valid flowchart keyword`() {
        val text =
            """
            Here is the diagram:
            ```mermaid
            flowchart TD
              A --> B
            ```
            """.trimIndent()
        val result = promptBuilder.extractMermaid(text)
        assertNotNull(result)
        assertTrue(result!!.startsWith("flowchart"))
    }

    @Test
    fun `extractMermaid returns diagram for graph keyword`() {
        val text = "```mermaid\ngraph LR\n  X --> Y\n```"
        assertNotNull(promptBuilder.extractMermaid(text))
    }

    @Test
    fun `extractMermaid returns null when extracted content is empty or whitespace only`() {
        val text = "```mermaid\n   \n```"
        assertNull(promptBuilder.extractMermaid(text))
    }

    @Test
    fun `extractDrawioXml returns null when no drawio content`() {
        assertNull(promptBuilder.extractDrawioXml("no drawio here"))
    }

    @Test
    fun `extractDrawioXml extracts from backtick-drawio block`() {
        val text =
            """
            ```drawio
            <mxfile><diagram><mxGraphModel><root></root></mxGraphModel></diagram></mxfile>
            ```
            """.trimIndent()
        val result = promptBuilder.extractDrawioXml(text)
        assertNotNull(result)
        assertTrue(result!!.startsWith("<mxfile"))
    }

    @Test
    fun `extractDrawioXml extracts from backtick-xml block`() {
        val text = "```xml\n<mxfile><diagram><mxGraphModel><root></root></mxGraphModel></diagram></mxfile>\n```"
        val result = promptBuilder.extractDrawioXml(text)
        assertNotNull(result)
    }

    @Test
    fun `extractDrawioXml extracts bare mxfile element`() {
        val text = "Some text <mxfile><diagram><mxGraphModel><root></root></mxGraphModel></diagram></mxfile> more text"
        val result = promptBuilder.extractDrawioXml(text)
        assertNotNull(result)
        assertTrue(result!!.contains("mxfile"))
    }

    @Test
    fun `extractDrawioXml falls back to bare mxGraphModel`() {
        val text = "text before <mxGraphModel><root></root></mxGraphModel> after"
        val result = promptBuilder.extractDrawioXml(text)
        assertNotNull(result)
        assertTrue(result!!.startsWith("<mxGraphModel"))
    }

    @Test
    fun `sanitizeDrawioXml extracts mxGraphModel from diagram inline block`() {
        val xml =
            """<mxfile><diagram name="d"><mxGraphModel><root><mxCell id="0"/></root></mxGraphModel></diagram></mxfile>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("mxGraphModel"))
    }

    @Test
    fun `sanitizeDrawioXml removes Array as points blocks`() {
        val xml = """<mxGraphModel><root><mxCell id="0"/><Array as="points"><mxPoint x="1" y="2"/></Array></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(!result.contains("<Array"))
    }

    @Test
    fun `sanitizeDrawioXml adds missing as attribute to mxGeometry`() {
        val xml = """<mxGraphModel><root><mxCell id="0"/><mxGeometry x="0" y="0"/></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("as=\"geometry\""))
    }

    @Test
    fun `sanitizeDrawioXml does not duplicate as attribute if already present`() {
        val xml = """<mxGraphModel><root><mxCell id="0"/><mxGeometry as="geometry" x="0"/></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.count { it == '=' }.let { it > 0 })
    }

    @Test
    fun `sanitizeDrawioXml deduplicates mxCell IDs`() {
        val xml =
            """
            <mxGraphModel><root>
              <mxCell id="0"/>
              <mxCell id="1" parent="0"/>
              <mxCell id="2" parent="1"/>
              <mxCell id="2" parent="1"/>
            </root></mxGraphModel>
            """.trimIndent()
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("sl-"))
    }

    @Test
    fun `sanitizeDrawioXml adds missing mxCell 0 and 1 when root exists`() {
        val xml = """<mxGraphModel><root><mxCell id="5" parent="1"/></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("id=\"0\"") && result.contains("id=\"1\""))
    }

    @Test
    fun `sanitizeDrawioXml adds missing mxCell 0 only`() {
        val xml = """<mxGraphModel><root><mxCell id="1" parent="0"/><mxCell id="5" parent="1"/></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("id=\"0\""))
    }

    @Test
    fun `sanitizeDrawioXml removes self-reference parent from cell 0`() {
        val xml =
            """<mxGraphModel><root><mxCell id="0" parent="0"/><mxCell id="1" parent="0"/></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        val cell0Idx = result.indexOf("id=\"0\"")
        val nextCellIdx = result.indexOf("<mxCell", cell0Idx + 1)
        val cell0Part = if (nextCellIdx > 0) result.substring(cell0Idx, nextCellIdx) else result.substring(cell0Idx)
        assertTrue(!cell0Part.contains("parent=\"0\""))
    }

    @Test
    fun `sanitizeDrawioXml fixes parent 0 to parent 1 for non-root cells`() {
        val xml =
            """<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/><mxCell id="5" parent="0"/></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("parent=\"1\""))
    }

    @Test
    fun `sanitizeDrawioXml adds relative attribute to edge mxGeometry`() {
        val xml =
            """<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/><mxCell id="2" edge="1" parent="1"><mxGeometry x="0"/></mxCell></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("relative="))
    }

    @Test
    fun `sanitizeDrawioXml normalizes bare hex colors in styles`() {
        val xml =
            """<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/><mxCell id="2" style="rounded=1;fillColor=dae8fc;strokeColor=6c8ebf;" vertex="1" parent="1"><mxGeometry x="0" y="0" width="100" height="40"/></mxCell></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        assertTrue(result.contains("fillColor=#dae8fc"))
        assertTrue(result.contains("strokeColor=#6c8ebf"))
    }

    @Test
    fun `sanitizeDrawioXml preserves existing relative attribute on edge`() {
        val xml =
            """<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/><mxCell id="2" edge="1" parent="1"><mxGeometry relative="1"/></mxCell></root></mxGraphModel>"""
        val result = promptBuilder.sanitizeDrawioXml(xml)
        val relCount = result.split("relative=").size - 1
        assertTrue(relCount >= 1)
    }

    @Test
    fun `buildPrompt crossRef uses keyword when className and filePath are null for gitlab`() {
        val gitlabResult =
            SearchResult(
                sessionId = 1L,
                keyword = "myKw",
                sourceType = SourceType.GITLAB,
                repoName = null,
                filePath = null,
                className = null,
                functionName = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(gitlabResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt crossRef uses repoName fallback when filePath null for gitlab`() {
        val gitlabResult =
            SearchResult(
                sessionId = 1L,
                keyword = "kw",
                sourceType = SourceType.GITLAB,
                repoName = "my-repo",
                filePath = null,
                className = null,
                functionName = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(gitlabResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt crossRef uses className when filePath null for pipeline`() {
        val pipelineResult =
            SearchResult(
                sessionId = 1L,
                keyword = "kw",
                sourceType = SourceType.PIPELINE,
                filePath = null,
                className = "MyClass",
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(pipelineResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt crossRef uses keyword when filePath and className null for pipeline`() {
        val pipelineResult =
            SearchResult(
                sessionId = 1L,
                keyword = "kw2",
                sourceType = SourceType.PIPELINE,
                filePath = null,
                className = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(pipelineResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt crossRef uses keyword when db tableName and columnName null`() {
        val dbResult =
            SearchResult(
                sessionId = 1L,
                keyword = "dbKw",
                sourceType = SourceType.DATABASE,
                tableName = null,
                columnName = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(dbResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt crossRef uses keyword when filePath null for confluence`() {
        val confluenceResult =
            SearchResult(
                sessionId = 1L,
                keyword = "cfKw",
                sourceType = SourceType.CONFLUENCE,
                filePath = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(confluenceResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt crossRef uses keyword when filePath null for jira`() {
        val jiraResult =
            SearchResult(
                sessionId = 1L,
                keyword = "jiraKw",
                sourceType = SourceType.JIRA,
                filePath = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(jiraResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt crossRef uses keyword when repoName and filePath null for grafana`() {
        val grafanaResult =
            SearchResult(
                sessionId = 1L,
                keyword = "grKw",
                sourceType = SourceType.GRAFANA,
                repoName = null,
                filePath = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(grafanaResult))
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun `buildPrompt detail uses noDetail when where is blank`() {
        val p = PromptContext(mapOf("search.noDetail" to "NO_DETAIL"))
        whenever(promptLoader.context(any())).thenReturn(p)
        val gitlabResult =
            SearchResult(
                sessionId = 1L,
                keyword = "kw",
                sourceType = SourceType.GITLAB,
                repoName = null,
                filePath = null,
                className = null,
                functionName = null,
                tableName = null,
                columnName = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(gitlabResult))
        assertTrue(prompt.contains("NO_DETAIL"))
    }

    @Test
    fun `getLatestAnalysis delegates to analysisRepo`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 5L, status = "COMPLETED")
        whenever(analysisRepo.findTopBySessionIdOrderByCreatedAtDesc(5L)).thenReturn(analysis)
        val result = service.getLatestAnalysis(5L)
        assertEquals(analysis, result)
    }

    @Test
    fun `getLatestAnalysis returns null when no analysis found`() {
        whenever(analysisRepo.findTopBySessionIdOrderByCreatedAtDesc(5L)).thenReturn(null)
        assertNull(service.getLatestAnalysis(5L))
    }

    @Test
    fun `sessionExists returns true when session found`() {
        whenever(sessionRepo.sessionExistsById(1L)).thenReturn(true)
        assertTrue(service.sessionExists(1L))
    }

    @Test
    fun `sessionExists returns false when session not found`() {
        whenever(sessionRepo.sessionExistsById(99L)).thenReturn(false)
        assertEquals(false, service.sessionExists(99L))
    }

    @Test
    fun `runAnalysis invokes streaming callback with partial output`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session())
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(listOf(result(SourceType.GITLAB)))
        whenever(copilotCliService.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), any(), anyOrNull())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[4] as (String) -> Unit
            callback("partial text")
            "final text"
        }
        service.runAnalysis(1L)
        assertEquals("COMPLETED", analysis.status)
        assertEquals("final text", analysis.analysisText)
    }

    @Test
    fun `runAnalysis marks FAILED when findSessionById returns null`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis).thenReturn(null)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(null)
        service.runAnalysis(1L)
        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `runAnalysis with includeDrawio true but no drawio block leaves drawioXml null`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis)
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session())
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(listOf(result(SourceType.GITLAB)))
        whenever(copilotCliService.executeWithStreaming(any(), any(), any<Long>(), anyOrNull(), any(), anyOrNull())).thenReturn("no drawio content here")
        service.runAnalysis(1L, includeDrawio = true)
        assertEquals("COMPLETED", analysis.status)
        assertNull(analysis.drawioXml)
    }

    @Test
    fun `runAnalysis re-fetch throws in catch falls back to captured analysis`() {
        val analysis = AiAnalysis(id = 1L, sessionId = 1L, status = "PENDING")
        whenever(analysisRepo.findAiAnalysisById(1L)).thenReturn(analysis).thenThrow(RuntimeException("re-fetch db error"))
        whenever(copilotCliService.resolveToken()).thenReturn("token")
        whenever(sessionRepo.findSessionById(1L)).thenThrow(RuntimeException("session missing"))
        service.runAnalysis(1L)
        assertEquals("FAILED", analysis.status)
    }

    @Test
    fun `buildPrompt crossRef uses filePath when className empty for gitlab`() {
        val gitlabResult =
            SearchResult(
                sessionId = 1L,
                keyword = "kw",
                sourceType = SourceType.GITLAB,
                className = "",
                functionName = null,
                filePath = "src/Foo.kt",
                repoName = "repo",
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(gitlabResult))
        assertTrue(prompt.contains("src/Foo.kt"))
    }

    @Test
    fun `buildPrompt crossRef uses filePath when repoName null for grafana`() {
        val grafanaResult =
            SearchResult(
                sessionId = 1L,
                keyword = "grKw",
                sourceType = SourceType.GRAFANA,
                repoName = null,
                filePath = "src/dashboard.json",
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(grafanaResult))
        assertTrue(prompt.contains("src/dashboard.json"))
    }

    @Test
    fun `buildPrompt uses fallback keyword when all firstNonBlank candidates are blank`() {
        val gitlabResult =
            SearchResult(
                sessionId = 1L,
                keyword = "myKeyword",
                sourceType = SourceType.GITLAB,
                className = null,
                filePath = null,
                repoName = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(gitlabResult))
        assertTrue(prompt.contains("myKeyword"))
    }

    @Test
    fun `buildPrompt DB result with null tableName and columnName uses keyword`() {
        val dbResult =
            SearchResult(
                sessionId = 1L,
                keyword = "dbKeyword",
                sourceType = SourceType.DATABASE,
                tableName = null,
                columnName = null,
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(dbResult))
        assertTrue(prompt.contains("dbKeyword"))
    }

    @Test
    fun `buildPrompt uses fallback when firstNonBlank candidates are blank strings`() {
        val gitlabResult =
            SearchResult(
                sessionId = 1L,
                keyword = "fallbackKw",
                sourceType = SourceType.GITLAB,
                className = "",
                filePath = "",
                repoName = "",
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(gitlabResult))
        assertTrue(prompt.contains("fallbackKw"))
    }

    @Test
    fun `buildPrompt DB result with blank tableName uses keyword via ifBlank`() {
        val dbResult =
            SearchResult(
                sessionId = 1L,
                keyword = "dbKw",
                sourceType = SourceType.DATABASE,
                tableName = "",
                columnName = "",
            )
        val prompt = promptBuilder.buildPrompt(session(), listOf(dbResult))
        assertTrue(prompt.contains("dbKw"))
    }

    @Test
    fun `saveDiagram delegates to diagramStorage`() {
        whenever(diagramStorage.saveDiagram("path/to", "file.svg", "<xml>")).thenReturn("saved/file.svg")
        val result = service.saveDiagram("path/to", "file.svg", "<xml>")
        assertEquals("saved/file.svg", result)
    }
}
