package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.ai.PromptLoader
import com.stacklens.adapter.out.review.pptx.PptxSlideHtmlRenderer
import com.stacklens.adapter.out.review.pptx.PptxStructureExtractor
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.ai.CopilotAiCaller
import com.stacklens.application.service.review.PptxAnalysisLog
import com.stacklens.application.service.review.PptxAnalysisOrchestrator
import com.stacklens.application.service.review.PptxAnswerMapper
import com.stacklens.application.service.review.PptxPromptAssembler
import com.stacklens.application.service.review.PptxQuestionExtractor
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.ReviewAnalysisConfig
import com.stacklens.infrastructure.config.CopilotProperties
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.FakeTransactionPort
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.io.DefaultResourceLoader

class ReviewAnalysisServiceTest {
    private companion object {
        const val REVIEW_ANALYSIS_TIMEOUT_SECONDS = 777L
    }

    private val analysisRepo: ReviewAnalysisPort = mock()
    private val configRepo: ReviewAnalysisConfigPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val sprintHttpClient: JiraSprintFetchPort = mock()
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val promptBuilder = ReviewAnalysisPromptBuilder(promptLoader, clock = com.stacklens.adapter.out.time.SystemClockPort)
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }
    private val jsonCodec = JacksonJsonCodecAdapter(objectMapper)
    private val i18n: I18nService = mock()
    private val copilotConfig = CopilotProperties(reviewAnalysisTimeoutSeconds = REVIEW_ANALYSIS_TIMEOUT_SECONDS)
    private val self: ReviewAnalysisService = mock()
    private lateinit var service: ReviewAnalysisService

    @BeforeEach
    fun setup() {
        service =
            ReviewAnalysisService(
                ReviewAnalysisServiceDeps(
                    analysisRepo,
                    configRepo,
                    jiraConfigRepo,
                    sprintHttpClient,
                    copilotCliService,
                    aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                    promptBuilder,
                    jsonCodec,
                    i18n,
                    asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                    startupHealer = StartupHealer(),
                    knowledgePublication = FakeKnowledgePublicationPort(),
                    knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
                    copilotConfig = copilotConfig,
                ),
                pptxStorage = mock<com.stacklens.application.port.out.PptxStoragePort>(),
                pptxOrchestrator = defaultPptxOrchestrator(),
                aiCaller = CopilotAiCaller(copilotCliService),
                transactionPort = FakeTransactionPort(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt-text")
        whenever(promptLoader.context(any())).thenReturn(PromptContext(emptyMap()))
        whenever(i18n.translateError(any())).thenReturn("error message")
        whenever(i18n.t(any(), any())).thenReturn("i18n text")
        whenever(i18n.t(any(), any(), any())).thenReturn("i18n text with args")
        whenever(analysisRepo.saveReviewAnalysis(any())).thenAnswer { it.arguments[0] as ReviewAnalysis }
    }

    private fun analysis() =
        ReviewAnalysis(
            id = 1L,
            jiraSprintUrl = "https://jira.example.com/boards/1",
            jiraConfigId = 10L,
            status = "PENDING",
        )

    private fun sprintInfo() =
        SprintInfo(
            id = 1L,
            name = "Sprint 1",
            state = "active",
            startDate = "2024-01-01",
            endDate = "2024-01-14",
            goal = "Deliver feature X",
        )

    private fun issue(
        key: String = "PROJ-1",
        type: String = "Story",
        comments: String = "",
    ) = SprintIssue(
        key = key,
        summary = "Fix something",
        description = "Details here",
        issueType = type,
        status = "Done",
        priority = "High",
        assignee = "Alice",
        labels = listOf("backend"),
        comments = comments,
        url = "https://jira.example.com/browse/$key",
    )

    private fun jiraConfig() =
        JiraConfig(
            id = 10L,
            name = "jira-test",
            baseUrl = "https://jira.example.com",
            username = "user",
            apiToken = "token",
        )

    private fun defaultPptxOrchestrator(): PptxAnalysisOrchestrator {
        val promptLoader = PromptLoader(DefaultResourceLoader(), ObjectMapper(), "./data")
        return PptxAnalysisOrchestrator(
            PptxStructureExtractor(),
            TestJsonCodec.create(),
            PptxQuestionExtractor(TestJsonCodec.create()),
            PptxAnswerMapper(TestJsonCodec.create()),
            PptxSlideHtmlRenderer(),
            mock<com.stacklens.application.port.out.PptxStoragePort>(),
            PptxPromptAssembler(promptLoader),
            PptxAnalysisLog(TestJsonCodec.create()),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    @Test
    fun `resetStaleAnalyses does nothing when no stale analyses`() {
        whenever(analysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())
        service.heal()
    }

    @Test
    fun `resetStaleAnalyses marks RUNNING analyses as FAILED`() {
        val staleRunning = analysis().apply { status = "RUNNING" }
        val stalePending = analysis().apply { status = "PENDING" }
        val done = analysis().apply { status = "COMPLETED" }
        whenever(analysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(staleRunning, stalePending, done))
        service.resetStaleAnalyses()
        assertEquals("CANCELLED", staleRunning.status)
        assertEquals("CANCELLED", stalePending.status)
        assertEquals("COMPLETED", done.status)
    }

    @Test
    fun `resetStaleAnalyses handles save failure gracefully`() {
        val stale = analysis().apply { status = "RUNNING" }
        whenever(analysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(stale))
        whenever(analysisRepo.saveReviewAnalysis(any())).thenThrow(RuntimeException("save failed"))
        service.resetStaleAnalyses()
    }

    @Test
    fun `resetStaleAnalyses handles outer exception gracefully`() {
        whenever(analysisRepo.findAllByOrderByCreatedAtDesc()).thenThrow(RuntimeException("db error"))
        service.resetStaleAnalyses()
    }

    @Test
    fun `startAnalysis returns PENDING analysis`() {
        whenever(configRepo.findReviewAnalysisConfig()).thenReturn(ReviewAnalysisConfig())
        val result = service.startAnalysis("https://jira.example.com", 10L, "Analyse alle Bugs")
        assertEquals("PENDING", result.status)
    }

    @Test
    fun `startAnalysis persists analysisInstructions on entity`() {
        whenever(configRepo.findReviewAnalysisConfig()).thenReturn(ReviewAnalysisConfig())
        val result = service.startAnalysis("https://jira.example.com", 10L, "Meine Anweisungen")
        assertEquals("Meine Anweisungen", result.analysisInstructions)
    }

    @Test
    fun `startAnalysis creates new config when none exists`() {
        whenever(configRepo.findReviewAnalysisConfig()).thenReturn(null)
        val result = service.startAnalysis("https://jira.example.com", 10L, "instructions")
        assertEquals("PENDING", result.status)
        verify(configRepo).saveReviewAnalysisConfig(any())
    }

    @Test
    fun `startAnalysis updates existing config with analysisInstructions`() {
        val existingConfig = ReviewAnalysisConfig()
        whenever(configRepo.findReviewAnalysisConfig()).thenReturn(existingConfig)
        service.startAnalysis("https://jira.example.com", 10L, "neue Anweisungen")
        assertEquals("neue Anweisungen", existingConfig.analysisInstructions)
    }

    @Test
    fun `getAnalysis returns null when not found`() {
        whenever(analysisRepo.findReviewAnalysisById(99L)).thenReturn(null)
        assertNull(service.getAnalysis(99L))
    }

    @Test
    fun `getAllAnalyses returns list`() {
        whenever(analysisRepo.findRecentReviewAnalyses(200)).thenReturn(listOf(analysis()))
        assertEquals(1, service.getAllAnalyses().size)
    }

    @Test
    fun `jiraConfigExists returns false when config is missing`() {
        whenever(jiraConfigRepo.jiraConfigExistsById(99L)).thenReturn(false)
        assertFalse(service.jiraConfigExists(99L))
    }

    @Test
    fun `jiraConfigExists returns true when config exists`() {
        whenever(jiraConfigRepo.jiraConfigExistsById(10L)).thenReturn(true)
        assertEquals(true, service.jiraConfigExists(10L))
    }

    @Test
    fun `resolveLanguage delegates to copilot`() {
        assertEquals("de", service.resolveLanguage())
    }

    @Test
    fun `runAnalysis exits early when analysis not found`() {
        whenever(analysisRepo.findReviewAnalysisById(99L)).thenReturn(null)
        service.runAnalysis(99L)
    }

    @Test
    fun `runAnalysis marks FAILED when jira config not found`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(null)
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis marks FAILED when jiraConfigId is null`() {
        val a =
            ReviewAnalysis(
                id = 1L,
                jiraSprintUrl = "https://jira.example.com/boards/1",
                jiraConfigId = null,
                status = "PENDING",
            )
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis marks FAILED when issues are empty`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to emptyList())
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis completes successfully`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(issue()))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("## Sprint-Analyse\n\nAll done.")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis passes the configured review timeout to the AI call`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(issue()))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("## Sprint-Analyse\n\nAll done.")
        service.runAnalysis(1L)
        verify(copilotCliService).execute(eq("gpt-4"), any(), eq(REVIEW_ANALYSIS_TIMEOUT_SECONDS), anyOrNull())
    }

    @Test
    fun `runAnalysis FAILED uses in-memory entity`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(null)
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis with issues having comments builds complete agent input`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val issues =
            listOf(
                issue("PROJ-1", "Story", "User says: looks good"),
                issue("PROJ-2", "Bug", "Reproducible on login"),
                issue("PROJ-3", "Task", ""),
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to issues)
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("Analysis done.")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `processLog is populated from valid existing JSON`() {
        val a =
            analysis().apply {
                processLogJson = """["step1","step2"]"""
            }
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(null)
        service.runAnalysis(1L)
        assertNotNull(a.processLogJson)
    }

    @Test
    fun `processLog handles broken existing JSON gracefully`() {
        val a =
            analysis().apply {
                processLogJson = "not-json"
            }
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(null)
        service.runAnalysis(1L)
        assertNotNull(a.processLogJson)
    }

    @Test
    fun `runAnalysis with issue having empty labels omits label line`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val noLabelIssue =
            SprintIssue(
                key = "PROJ-1",
                summary = "Test",
                description = "desc",
                issueType = "Story",
                status = "Done",
                priority = "High",
                assignee = "Alice",
                labels = emptyList(),
                comments = "",
                url = "https://jira.example.com/browse/PROJ-1",
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(noLabelIssue))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with issue having blank description omits description line`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val blankDescIssue =
            SprintIssue(
                key = "PROJ-1",
                summary = "Test",
                description = "",
                issueType = "Task",
                status = "Done",
                priority = "High",
                assignee = "Alice",
                labels = listOf("backend"),
                comments = "",
                url = "https://jira.example.com/browse/PROJ-1",
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(blankDescIssue))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with issue having null priority and assignee uses fallbacks`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val nullFieldIssue =
            SprintIssue(
                key = "PROJ-1",
                summary = "Test",
                description = "desc",
                issueType = "Story",
                status = "Done",
                priority = null,
                assignee = null,
                labels = emptyList(),
                comments = "",
                url = "https://jira.example.com/browse/PROJ-1",
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(nullFieldIssue))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with sprint having null dates and goal uses empty strings`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val partial =
            SprintInfo(
                id = 1L,
                name = "Sprint 1",
                state = "active",
                startDate = null,
                endDate = null,
                goal = null,
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(partial to listOf(issue()))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with issue having null timestamps uses unknown fallback`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val nullTimestampIssue =
            SprintIssue(
                key = "PROJ-1",
                summary = "Test",
                description = "desc",
                issueType = "Story",
                status = "Done",
                priority = "Medium",
                assignee = "Alice",
                labels = emptyList(),
                comments = "",
                url = "https://jira.example.com/browse/PROJ-1",
                createdAt = null,
                startedAt = null,
                resolvedAt = null,
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(nullTimestampIssue))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with issue having non-null timestamps includes them`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val timestampIssue =
            SprintIssue(
                key = "PROJ-1",
                summary = "Test",
                description = "desc",
                issueType = "Story",
                status = "Done",
                priority = "Medium",
                assignee = "Alice",
                labels = emptyList(),
                comments = "",
                url = "https://jira.example.com/browse/PROJ-1",
                createdAt = "2024-01-01",
                startedAt = "2024-01-02",
                resolvedAt = "2024-01-10",
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(timestampIssue))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with only Task issues produces empty comments block`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val taskOnly =
            SprintIssue(
                key = "PROJ-1",
                summary = "Setup CI",
                description = "desc",
                issueType = "Task",
                status = "Done",
                priority = "Low",
                assignee = "Bob",
                labels = emptyList(),
                comments = "some comment",
                url = "https://jira.example.com/browse/PROJ-1",
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(taskOnly))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis with blank key and summary uses fallback text`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val blankKeyIssue =
            SprintIssue(
                key = "",
                summary = "",
                description = "",
                issueType = "",
                status = "",
                priority = null,
                assignee = null,
                labels = emptyList(),
                comments = "",
                url = "https://jira.example.com/browse/X",
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(blankKeyIssue))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `runAnalysis error handler with null exception message uses empty string`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenThrow(RuntimeException(null as String?))
        service.runAnalysis(1L)
        assertEquals("FAILED", a.status)
    }

    @Test
    fun `runAnalysis with story having comments includes comments block`() {
        val a = analysis()
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(a)
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(jiraConfig())
        val storyWithComments =
            SprintIssue(
                key = "PROJ-1",
                summary = "Feature",
                description = "desc",
                issueType = "Story",
                status = "Done",
                priority = "High",
                assignee = "Alice",
                labels = listOf("backend"),
                comments = "Great work!",
                url = "https://jira.example.com/browse/PROJ-1",
            )
        val bugWithComments =
            SprintIssue(
                key = "PROJ-2",
                summary = "Fix crash",
                description = "desc",
                issueType = "Bug",
                status = "Done",
                priority = "Critical",
                assignee = "Bob",
                labels = emptyList(),
                comments = "Fixed in v2",
                url = "https://jira.example.com/browse/PROJ-2",
            )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(sprintInfo() to listOf(storyWithComments, bugWithComments))
        whenever(copilotCliService.execute(any(), any(), any(), anyOrNull())).thenReturn("result")
        service.runAnalysis(1L)
        assertEquals("COMPLETED", a.status)
    }

    @Test
    fun `deleteAnalysis delegates to analysisRepo`() {
        service.deleteAnalysis(1L)
        verify(analysisRepo).deleteReviewAnalysisById(1L)
    }

    @Test
    fun `deleteAllAnalyses delegates to analysisRepo`() {
        service.deleteAllAnalyses()
        verify(analysisRepo).deleteAllReviewAnalyses()
    }

    @Test
    fun `runAnalysis when analysis not found returns immediately`() {
        whenever(analysisRepo.findReviewAnalysisById(1L)).thenReturn(null)
        service.runAnalysis(1L)
    }

    @Test
    fun `buildPrompt uses analysisInstructions as instructions prefix`() {
        val instructions = "Analysiere alle technischen Schulden"
        val prompt = service.buildPrompt("de", sprintInfo(), listOf(issue()), instructions)
        assertTrue(prompt.startsWith(instructions))
    }

    @Test
    fun `buildPrompt uses blank instructions as-is without fallback`() {
        val prompt = service.buildPrompt("de", sprintInfo(), listOf(issue()), "   ")
        assertTrue(prompt.startsWith("   "))
    }

    @Test
    fun `buildPrompt contains separator between instructions and sprint data`() {
        val prompt = service.buildPrompt("de", sprintInfo(), listOf(issue()), "instructions")
        assertTrue(prompt.contains("---"))
    }

    @Test
    fun `buildPrompt always appends dashboard instruction at the end`() {
        whenever(promptLoader.get("de", "review.dashboardInstruction", emptyMap()))
            .thenReturn("DASHBOARD-INSTRUCTION")
        val prompt = service.buildPrompt("de", sprintInfo(), listOf(issue()), "instructions")
        assertTrue(prompt.contains("DASHBOARD-INSTRUCTION"))
    }

    @Test
    fun `buildPrompt appends German language directive when lang is de`() {
        whenever(promptLoader.get("de", "review.dashboardLangDirective", emptyMap()))
            .thenReturn("DE-LANG-DIRECTIVE")
        val prompt = service.buildPrompt("de", sprintInfo(), listOf(issue()), "instructions")
        assertTrue(prompt.contains("DE-LANG-DIRECTIVE"))
    }

    @Test
    fun `buildPrompt appends English language directive when lang is en`() {
        whenever(promptLoader.get("en", "review.dashboardLangDirective", emptyMap()))
            .thenReturn("EN-LANG-DIRECTIVE")
        val prompt = service.buildPrompt("en", sprintInfo(), listOf(issue()), "instructions")
        assertTrue(prompt.contains("EN-LANG-DIRECTIVE"))
    }
}
