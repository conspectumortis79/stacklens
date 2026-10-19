package com.stacklens.application.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.PptxStoragePort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.review.PptxAnalysisOrchestrator
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.infrastructure.config.CopilotProperties
import com.stacklens.testsupport.FakeTransactionPort
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.Instant

class ReviewAnalysisRunFailureLoggingTest {
    private val analysisRepo: ReviewAnalysisPort = mock()

    private val configRepo: ReviewAnalysisConfigPort = mock()

    private val jiraConfigRepo: JiraConfigPort = mock()

    private val sprintHttpClient: JiraSprintFetchPort = mock()

    private val copilot: CopilotPort = mock()

    private val aiUsageContext: AiUsageContextPort = mock()

    private val promptBuilder: ReviewAnalysisPromptBuilder = mock()

    private val knowledgePublication: KnowledgePublication = mock()

    private val knowledgeMapper: AnalysisKnowledgeMapper = mock()

    private val pptxStorage: PptxStoragePort = mock()

    private val pptxOrchestrator: PptxAnalysisOrchestrator = mock()

    private val aiCaller: AiCaller = mock()

    private val asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor()

    private val i18n = FakeI18n()

    private val stored = mutableListOf<ReviewAnalysis>()

    private val service =
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
        )

    private val logger = LoggerFactory.getLogger(ReviewAnalysisService::class.java) as Logger

    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun recordLogEvents() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun stopRecordingLogEvents() {
        logger.detachAppender(appender)
        appender.stop()
    }

    private fun analysisRow(): ReviewAnalysis =
        ReviewAnalysis(
            id = 5L,
            jiraSprintUrl = "https://jira.example.com/boards/9?selectedInstance=sprint-7",
            jiraConfigId = 10L,
            status = AnalysisStatus.PENDING,
        )

    private fun stubRunThatLosesTheRowOnTheFinalWrite() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(copilot.resolveModel()).thenReturn("gpt-test")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn("# Ergebnis")
        whenever(promptBuilder.buildPrompt(any(), any(), any(), any())).thenReturn("prompt")
        whenever(jiraConfigRepo.findJiraConfigById(10L)).thenReturn(
            JiraConfig(id = 10L, name = "j", baseUrl = "https://jira.example.com", username = "u", apiToken = "t"),
        )
        whenever(sprintHttpClient.fetchSprintData(any(), any())).thenReturn(
            SprintInfo(id = 7L, name = "Sprint 7", state = "active", startDate = null, endDate = null, goal = null) to
                listOf(
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
                    ),
                ),
        )
        whenever(analysisRepo.saveReviewAnalysis(any())).thenAnswer { invocation ->
            val row = invocation.arguments[0] as ReviewAnalysis
            if (row.status == AnalysisStatus.COMPLETED) {
                throw ObjectOptimisticLockingFailureException("ReviewAnalysis", 5L)
            }
            stored += row
            row
        }
    }

    @Test
    fun `a run whose row vanished mid flight is a warning and never claims a failed analysis`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysisRow()).doReturn(null)
        stubRunThatLosesTheRowOnTheFinalWrite()

        service.runAnalysis(5L)

        assertTrue(appender.list.none { it.level == Level.ERROR }, "unexpected ERROR events: ${appender.list.map { it.formattedMessage }}")
        assertTrue(
            appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("was deleted while the run was active") },
            "no cancellation warning in ${appender.list.map { it.formattedMessage }}",
        )
        assertTrue(stored.none { it.status == AnalysisStatus.FAILED }, "a deleted analysis must not be rewritten as FAILED")
    }

    @Test
    fun `a failure on a row that still exists is logged as an error and marked failed`() {
        val reloaded = analysisRow()
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysisRow(), reloaded)
        stubRunThatLosesTheRowOnTheFinalWrite()

        service.runAnalysis(5L)

        val events: List<ILoggingEvent> = appender.list
        val errorEvent: ILoggingEvent = events.single { it.level == Level.ERROR }
        assertNotNull(errorEvent.throwableProxy, "the ERROR event must carry the causing exception")
        assertTrue(errorEvent.formattedMessage.contains("Error in analysis 5"), errorEvent.formattedMessage)
        assertEquals(AnalysisStatus.FAILED, stored.last().status)
        assertNull(stored.last().completedAt)
    }

    @Test
    fun `a cancellation whose exception carries no message still names its type`() {
        whenever(analysisRepo.findReviewAnalysisById(5L)).doReturn(analysisRow()).doReturn(null)
        stubRunThatLosesTheRowOnTheFinalWrite()
        whenever(sprintHttpClient.fetchSprintData(any(), any())).doThrow(IllegalStateException())

        service.runAnalysis(5L)

        val cancellation: ILoggingEvent = appender.list.single { it.formattedMessage.contains("was deleted while the run was active") }
        assertEquals(Level.WARN, cancellation.level)
        assertTrue(cancellation.formattedMessage.endsWith("(IllegalStateException: )"), cancellation.formattedMessage)
        assertNull(cancellation.throwableProxy, "a deleted row must not print a stack trace")
    }
}
