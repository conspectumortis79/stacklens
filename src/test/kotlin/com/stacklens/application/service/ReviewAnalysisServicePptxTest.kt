package com.stacklens.application.service

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.PptxStoragePort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.service.FakeI18n
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.service.review.PptxAnalysisOrchestrator
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.ReviewAnalysisConfig
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.infrastructure.config.CopilotProperties
import com.stacklens.testsupport.FakeTransactionPort
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.File
import java.time.Instant

class ReviewAnalysisServicePptxTest {
    private val analysisRepo: ReviewAnalysisPort = mock()

    private val configRepo: ReviewAnalysisConfigPort = mock()

    private val jiraConfigRepo: JiraConfigPort = mock()

    private val sprintHttpClient: JiraSprintFetchPort = mock()

    private val copilot: CopilotPort = mock()

    private val aiUsageContext: AiUsageContextPort = mock()

    private val promptBuilder: ReviewAnalysisPromptBuilder = mock()

    private val i18n = FakeI18n()

    private val asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor()

    private val knowledgePublication: KnowledgePublication = mock()

    private val knowledgeMapper: AnalysisKnowledgeMapper = mock()

    private val pptxStorage: PptxStoragePort = mock()

    private val pptxOrchestrator: PptxAnalysisOrchestrator = mock()

    private val aiCaller: AiCaller = mock()

    private val stored = mutableListOf<ReviewAnalysis>()

    private val service =
        spy(
            ReviewAnalysisService(
                ReviewAnalysisServiceDeps(
                    analysisRepo = analysisRepo,
                    configRepo = configRepo,
                    jiraConfigRepo = jiraConfigRepo,
                    sprintHttpClient = sprintHttpClient,
                    copilotCliService = copilot,
                    aiUsageContext = aiUsageContext,
                    promptBuilder = promptBuilder,
                    jsonCodec = TestJsonCodec.create(),
                    i18n = i18n,
                    asyncExecutor = asyncExecutor,
                    startupHealer = StartupHealer(),
                    knowledgePublication = knowledgePublication,
                    knowledgeMapper = knowledgeMapper,
                    copilotConfig = CopilotProperties(),
                ),
                pptxStorage = pptxStorage,
                pptxOrchestrator = pptxOrchestrator,
                aiCaller = aiCaller,
                transactionPort = FakeTransactionPort(),
                clock = FixedClockPort(Instant.EPOCH),
            ),
        )

    init {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("gpt-test")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn("# Ergebnis")
        whenever(analysisRepo.saveReviewAnalysis(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as ReviewAnalysis).also {
                stored += it
                stepsSeen += it.currentStep
            }
        }
        whenever(configRepo.findReviewAnalysisConfig()).thenReturn(ReviewAnalysisConfig())
        whenever(promptBuilder.buildPrompt(any(), any(), any(), any())).thenReturn("prompt")
    }

    private fun analysis(
        id: Long = 5L,
        pptxFileName: String? = null,
        pptxServerPath: String? = null,
    ): ReviewAnalysis =
        ReviewAnalysis(
            id = id,
            jiraSprintUrl = "https://jira.example.com/boards/9?selectedInstance=sprint-7",
            jiraConfigId = 10L,
            status = AnalysisStatus.PENDING,
            pptxFileName = pptxFileName,
            pptxServerPath = pptxServerPath,
        )

    private fun candidate() =
        KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.REVIEW_ANALYSIS,
            externalId = "review-5",
            title = "Review Analyse",
            sourceUri = null,
            content = "# Inhalt",
        )

    private val stepsSeen = mutableListOf<String?>()

    private fun stepsAfterOrchestrator(): String? = stepsSeen.lastOrNull { it == "RENDERING_PPTX_HTML" }

    private fun stubJiraConfig() {
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(
            JiraConfig(id = 10L, name = "j", baseUrl = "https://jira.example.com", username = "u", apiToken = "t"),
        )
    }

    private fun stubSprint(issues: List<SprintIssue>) {
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(
            Pair(
                SprintInfo(id = 7L, name = "Sprint 7", state = "active", startDate = null, endDate = null, goal = null),
                issues,
            ),
        )
    }

    private fun stubFetch(issues: List<SprintIssue>) {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis())
        stubJiraConfig()
        stubSprint(issues)
    }

    private val question =
        SprintIssue(
            key = "PROJ-1",
            summary = "Etwas reparieren",
            description = "Details",
            issueType = "Story",
            status = "Done",
            priority = null,
            assignee = null,
            labels = emptyList(),
            comments = "",
            url = "https://jira.example.com/browse/PROJ-1",
        )

    @Test
    fun `startAnalysis dispatches directly when no transaction is active`() {
        val saved = service.startAnalysis("  https://jira.example.com/sprint  ", 3L, "  mehr Tests  ")

        assertEquals("https://jira.example.com/sprint", saved.jiraSprintUrl)
        assertEquals("mehr Tests", saved.analysisInstructions)
        verify(service).runAnalysis(saved.id)
        verify(configRepo).saveReviewAnalysisConfig(any())
    }

    @Test
    fun `startAnalysis defers the dispatch until the transaction commits`() {
        TransactionSynchronizationManager.initSynchronization()
        try {
            val saved = service.startAnalysis("https://jira.example.com/sprint", 3L, "text")

            verify(service, never()).runAnalysis(any())
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(service).runAnalysis(saved.id)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `startAnalysisWithPptx stores the upload and keeps the file name`() {
        whenever(pptxStorage.saveUpload(0L, "deck.pptx", byteArrayOf(1, 2))).thenReturn(File("/data/deck.pptx"))

        val saved = service.startAnalysisWithPptx("https://jira.example.com/sprint", 3L, "text", "deck.pptx", byteArrayOf(1, 2))

        assertEquals("deck.pptx", saved.pptxFileName)
        verify(pptxStorage).saveUpload(any(), eq("deck.pptx"), eq(byteArrayOf(1, 2)))
        assertEquals(listOf("deck.pptx"), stored.mapNotNull { it.pptxFileName }.distinct())
    }

    @Test
    fun `startAnalysisWithPptxPath imports the server file and remembers the copy`() {
        whenever(pptxStorage.importFromServer(any(), eq("/srv/folien/deck.pptx"))).thenReturn(File("/data/copied/deck.pptx"))

        val saved = service.startAnalysisWithPptxPath("https://jira.example.com/sprint", 3L, "text", "/srv/folien/deck.pptx")

        assertEquals("deck.pptx", saved.pptxFileName)
        assertEquals("/data/copied/deck.pptx", saved.pptxServerPath)
    }

    @Test
    fun `firstActiveJiraConfigId returns the first active configuration or null`() {
        whenever(
            jiraConfigRepo.findByActiveTrue(),
        ).thenReturn(
            listOf(JiraConfig(id = 11L, name = "a", baseUrl = "https://jira.example.com", apiToken = "t"), JiraConfig(id = 12L, name = "b", baseUrl = "https://jira.example.com", apiToken = "t")),
        )

        assertEquals(11L, service.firstActiveJiraConfigId())

        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(emptyList())

        assertNull(service.firstActiveJiraConfigId())
    }

    @Test
    fun `runAnalysis renders the pptx html after a successful ai run`() {
        stubFetch(listOf(question))
        whenever(pptxOrchestrator.runPptxAnalysis(any(), anyOrNull(), anyOrNull(), any(), any(), any())).thenReturn(null)

        service.runAnalysis(5L)

        verify(pptxOrchestrator, never())
            .runPptxAnalysis(any(), any(), any(), any(), any(), any())
        assertEquals(AnalysisStatus.COMPLETED, stored.last().status)
        verify(knowledgeMapper).fromReviewAnalysis(any())
    }

    @Test
    fun `runAnalysis renders the pptx html when an upload is attached`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxFileName = "deck.pptx"))
        stubJiraConfig()
        stubSprint(listOf(question))

        service.runAnalysis(5L)

        verify(pptxOrchestrator).runPptxAnalysis(any(), anyOrNull(), anyOrNull(), eq("de"), eq("gpt-test"), eq(aiCaller))
        assertEquals(AnalysisStatus.COMPLETED, stored.last().status)
        assertEquals("RENDERING_PPTX_HTML", stepsAfterOrchestrator())
    }

    @Test
    fun `a failing pptx analysis does not fail the review run`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxServerPath = "/data/deck.pptx"))
        stubJiraConfig()
        stubSprint(listOf(question))
        whenever(
            pptxOrchestrator.runPptxAnalysis(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).doThrow(IllegalStateException("pptx kaputt"))

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.COMPLETED, stored.last().status)
    }

    @Test
    fun `the completed analysis is published to the knowledge base`() {
        val published = candidate()
        stubFetch(listOf(question))
        whenever(knowledgeMapper.fromReviewAnalysis(any())).thenReturn(published)

        service.runAnalysis(5L)

        verify(knowledgePublication).publish(published)
    }

    @Test
    fun `a failing knowledge publication keeps the analysis completed`() {
        val published = candidate()
        stubFetch(listOf(question))
        whenever(knowledgeMapper.fromReviewAnalysis(any())).thenReturn(published)
        doThrow(IllegalStateException("index vergeben")).whenever(knowledgePublication).publish(any())

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.COMPLETED, stored.last().status)
    }

    @Test
    fun `a failed run still renders the pptx fallback and stores the failure`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxFileName = "deck.pptx"))
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))

        service.runAnalysis(5L)

        verify(pptxOrchestrator).runPptxAnalysis(any(), eq(null), eq(emptyList<SprintIssue>()), any(), any(), eq(aiCaller))
        assertEquals(AnalysisStatus.FAILED, stored.last().status)
        assertEquals("jira nicht erreichbar", stored.last().errorMessage)
        assertTrue(stored.last().processLogJson!!.contains("be_log_error"), stored.last().processLogJson ?: "")
    }

    @Test
    fun `an analysis that vanished from the database is not rewritten on failure`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(), null)
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.RUNNING, stored.last().status)
        verify(pptxOrchestrator, never()).runPptxAnalysis(any(), anyOrNull(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun `an exception without a message is logged without a crash`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis())
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException())

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.FAILED, stored.last().status)
    }

    @Test
    fun `a missing jira configuration fails the analysis`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxFileName = null))

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.FAILED, stored.last().status)
        verify(aiUsageContext).clearContext()
    }

    @Test
    fun `a sprint without tickets fails the analysis`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis())
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(
            Pair(SprintInfo(7L, "Sprint 7", "active", null, null, null), emptyList()),
        )

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.FAILED, stored.last().status)
        verify(copilot, never()).execute(any(), any(), anyOrNull(), any())
    }

    @Test
    fun `an analysis with blank pptx references skips the rendering`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxFileName = "   ", pptxServerPath = ""))
        stubJiraConfig()
        stubSprint(listOf(question))

        service.runAnalysis(5L)

        verify(pptxOrchestrator, never()).runPptxAnalysis(any(), anyOrNull(), anyOrNull(), any(), any(), any())
        assertEquals(AnalysisStatus.COMPLETED, stored.last().status)
    }

    @Test
    fun `a failed run with only a server path renders the fallback`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxServerPath = "/data/deck.pptx"))
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))

        service.runAnalysis(5L)

        verify(pptxOrchestrator).runPptxAnalysis(any(), eq(null), eq(emptyList<SprintIssue>()), any(), any(), eq(aiCaller))
        assertEquals(AnalysisStatus.FAILED, stored.last().status)
    }

    @Test
    fun `a failed run without pptx references skips the fallback rendering`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxFileName = "", pptxServerPath = null))
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))

        service.runAnalysis(5L)

        verify(pptxOrchestrator, never()).runPptxAnalysis(any(), anyOrNull(), anyOrNull(), any(), any(), any())
        assertEquals(AnalysisStatus.FAILED, stored.last().status)
    }

    @Test
    fun `a failed run with only a file name renders the fallback`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxFileName = "deck.pptx"))
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))

        service.runAnalysis(5L)

        verify(pptxOrchestrator).runPptxAnalysis(any(), eq(null), eq(emptyList<SprintIssue>()), any(), any(), eq(aiCaller))
        assertEquals(AnalysisStatus.FAILED, stored.last().status)
    }

    @Test
    fun `a fallback render that itself fails keeps the analysis failed`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxServerPath = "/data/deck.pptx"))
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))
        whenever(pptxOrchestrator.runPptxAnalysis(any(), anyOrNull(), anyOrNull(), any(), any(), any()))
            .thenThrow(IllegalStateException("libreoffice fehlt"))

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.FAILED, stored.last().status)
    }

    @Test
    fun `a failed run with two blank pptx references skips the fallback rendering`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis(pptxFileName = null, pptxServerPath = " \t "))
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))

        service.runAnalysis(5L)

        verify(pptxOrchestrator, never()).runPptxAnalysis(any(), anyOrNull(), anyOrNull(), any(), any(), any())
        assertEquals(AnalysisStatus.FAILED, stored.last().status)
    }

    @Test
    fun `a reload that fails during the error handling keeps the failure logged`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysis()).doThrow(IllegalStateException("db weg"))

        service.runAnalysis(5L)

        verify(aiUsageContext).clearContext()
    }

    @Test
    fun `a failing save during the error handling is reported without throwing`() {
        val analysed = analysis(pptxFileName = "x.pptx")
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysed)
        stubJiraConfig()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException("jira nicht erreichbar"))
        whenever(analysisRepo.saveReviewAnalysis(any())).thenReturn(analysed).thenThrow(IllegalStateException("schreiben fehlgeschlagen"))

        service.runAnalysis(5L)

        assertEquals(AnalysisStatus.FAILED, analysed.status)
    }

    @Test
    fun `an unknown analysis id is ignored`() {
        whenever(analysisRepo.findReviewAnalysisById(99L)).thenReturn(null)

        service.runAnalysis(99L)

        verify(analysisRepo, never()).saveReviewAnalysis(any())
    }
}
