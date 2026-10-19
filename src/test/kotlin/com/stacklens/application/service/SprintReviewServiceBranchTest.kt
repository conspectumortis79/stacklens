package com.stacklens.application.service

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.service.sprintreview.SprintReviewLifecycle
import com.stacklens.application.service.sprintreview.SprintReviewOrchestrator
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.InlineAsyncExecutor
import com.stacklens.testsupport.TestExecutors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class SprintReviewServiceBranchTest {
    private val sprintReviewRepo: SprintReviewPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val jira: SprintReviewJiraPort = mock()
    private val copilot: CopilotPort = mock()
    private val aiUsageContext: AiUsageContextPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val jsonCodec: JsonCodecPort = mock()
    private val i18n: I18nPort = mock()
    private val asyncExecutor: AsyncExecutorPort = InlineAsyncExecutor()
    private val knowledgeMapper: AnalysisKnowledgeMapper = mock()
    private val knowledgePublication: FakeKnowledgePublicationPort = FakeKnowledgePublicationPort()
    private val entity = review()

    private val service = buildService()

    private fun buildService(
        executor: Executor = TestExecutors.direct(),
        publication: KnowledgePublication = knowledgePublication,
    ): SprintReviewService =
        SprintReviewService(
            SprintReviewServiceDeps(
                sprintReviewRepo = sprintReviewRepo,
                jiraConfigRepo = jiraConfigRepo,
                jira = jira,
                copilot = copilot,
                aiUsageContext = aiUsageContext,
                promptLoader = promptLoader,
                jsonCodec = jsonCodec,
                i18n = i18n,
                asyncExecutor = asyncExecutor,
                startupHealer = StartupHealer(),
            ),
            reviewExecutor = executor,
            knowledgePublication = publication,
            knowledgeMapper = knowledgeMapper,
            lifecycle = SprintReviewLifecycle(sprintReviewRepo, jiraConfigRepo, jsonCodec, i18n, copilot, promptLoader, clock = FixedClockPort(FIXED_NOW)),
            orchestrator = SprintReviewOrchestrator(copilot, promptLoader, i18n),
            clock = FixedClockPort(FIXED_NOW),
        )

    private fun stubTranslations() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("model-x")
        whenever(i18n.t(any<String>())).thenAnswer { invocation -> invocation.arguments[0] as String }
        whenever(i18n.t(any<String>(), any())).thenAnswer { invocation ->
            "${invocation.arguments[0]}: ${invocation.arguments[1]}"
        }
        whenever(i18n.tFor(any<String>(), any<String>())).thenAnswer { invocation ->
            invocation.arguments[1] as String
        }
        whenever(i18n.tFor(any<String>(), any<String>(), any())).thenAnswer { invocation ->
            "${invocation.arguments[1]}: ${invocation.arguments[2]}"
        }
        whenever(i18n.tFor(any<String>(), any<String>(), any(), any())).thenAnswer { invocation ->
            "${invocation.arguments[1]}: ${invocation.arguments[2]}/${invocation.arguments[3]}"
        }
        whenever(i18n.translateError(any())).thenReturn("translated failure")
    }

    private fun stubSuccessfulReview(
        tickets: List<SprintReviewTicket>,
        stored: SprintReview = entity,
    ) {
        whenever(sprintReviewRepo.findSprintReviewById(any())).thenReturn(stored)
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(config())
        whenever(jira.resolveSprintByName(any(), any())).thenReturn(sprint())
        whenever(jira.fetchSprintTickets(any(), any())).thenReturn(tickets)
        whenever(promptLoader.get(any(), any(), any())).thenReturn("TEMPLATE")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"improvementSummary":"tightens the auth flow","openPoints":[]}""",
        )
        whenever(jsonCodec.encode(any<List<SprintReviewRow>>())).thenReturn("[]")
        whenever(jira.renderAdfDocument(any(), any(), any())).thenReturn(SprintReviewAdfDocument("""{"type":"doc"}"""))
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }
    }

    private fun sprint(): ResolvedSprint =
        ResolvedSprint(
            id = 42L,
            name = "Sprint 42",
            state = "active",
            url = "https://example.atlassian.net/secure/RapidBoard.jspa?rapidView=42",
        )

    private fun ticket(key: String): SprintReviewTicket =
        SprintReviewTicket(
            key = key,
            summary = "summary of $key",
            description = "description of $key",
            url = "https://example.atlassian.net/browse/$key",
            issueType = "Story",
            status = "Done",
            assignee = "Alice",
            storyPoints = 3.0,
        )

    private fun config(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "cfg",
            baseUrl = "https://example.atlassian.net/",
            apiToken = "tok",
            cloudMode = true,
        )

    private fun review(
        id: Long = 1L,
        status: String = AnalysisStatus.PENDING,
        adf: String? = """{"type":"doc","version":1}""",
        configId: Long? = 1L,
        templateOverride: String? = null,
    ): SprintReview =
        SprintReview(
            id = id,
            sprintName = "Sprint 42",
            targetTicketKey = "PROJ-100",
            jiraConfigId = configId,
            templateOverride = templateOverride,
            status = status,
            tableAdfJson = adf,
        )

    private fun candidate(): KnowledgePublicationCandidate =
        KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.SPRINT_REVIEW,
            externalId = "sprint-review:1",
            title = "Sprint-Review: Sprint 42",
            sourceUri = null,
            content = "# sprint review",
        )

    @Test fun `resetStaleReviews cancels reviews that were still in flight`() {
        val pending = review(status = AnalysisStatus.PENDING, adf = null)
        val running = review(id = 2L, status = AnalysisStatus.RUNNING, adf = null)
        val finished = review(id = 3L, status = AnalysisStatus.COMPLETED, adf = null)
        whenever(sprintReviewRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(pending, running, finished))
        whenever(i18n.t(any<String>())).thenReturn("aborted by restart")
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        buildService().heal()

        assertEquals(AnalysisStatus.CANCELLED, pending.status)
        assertEquals(AnalysisStatus.CANCELLED, running.status)
        assertEquals(AnalysisStatus.COMPLETED, finished.status)
        assertEquals("aborted by restart", pending.errorMessage)
        assertEquals(LocalDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC), running.completedAt)
        verify(sprintReviewRepo, times(2)).saveSprintReview(any())
    }

    @Test fun `listing and deletion delegate to the repository`() {
        val all = listOf(review())
        whenever(sprintReviewRepo.findRecentSprintReviews(any())).thenReturn(all)
        whenever(jiraConfigRepo.jiraConfigExistsById(1L)).thenReturn(true)
        whenever(jiraConfigRepo.jiraConfigExistsById(2L)).thenReturn(false)

        assertEquals(all, service.getAllReviews())
        service.deleteReview(1L)
        service.deleteAllReviews()
        assertTrue(service.jiraConfigExists(1L))
        assertTrue(!service.jiraConfigExists(2L))

        verify(sprintReviewRepo).deleteSprintReviewById(1L)
        verify(sprintReviewRepo).deleteAllSprintReviews()
    }

    @Test fun `postAdfToJira rejects a blank ADF document`() {
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(review(adf = "   "))

        val failure = assertThrows(IllegalStateException::class.java) { service.postAdfToJira(1L) }

        assertTrue(failure.message!!.contains("no ADF document"))
    }

    @Test fun `postAdfToJira rejects a review without a linked jira configuration`() {
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(review(configId = null))

        val failure = assertThrows(IllegalStateException::class.java) { service.postAdfToJira(1L) }

        assertTrue(failure.message!!.contains("No Jira configuration linked"))
    }

    @Test fun `postAdfToJira rejects a review whose jira configuration was deleted`() {
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(review(configId = 9L))
        whenever(jiraConfigRepo.findJiraConfigById(9L)).thenReturn(null)

        val failure = assertThrows(IllegalStateException::class.java) { service.postAdfToJira(1L) }

        assertTrue(failure.message!!.contains("No Jira configuration linked"))
    }

    @Test fun `postAdfToJira reports an unknown reason when jira fails without a message`() {
        stubTranslations()
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(review())
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(config())
        whenever(jira.appendAdfToDescription(any(), any(), any())).thenThrow(RuntimeException())
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        val updated = service.postAdfToJira(1L)

        assertEquals("sprint_review.err_post_failed: unknown", updated.errorMessage)
        assertNull(updated.targetTicketUrl)
    }

    @Test fun `postAdfToJira links the target ticket url on success`() {
        stubTranslations()
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(review())
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(config())
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        val updated = service.postAdfToJira(1L)

        assertEquals("https://example.atlassian.net/browse/PROJ-100", updated.targetTicketUrl)
        assertEquals("sprint_review.step_done_posted", updated.currentStep)
        assertNull(updated.errorMessage)
    }

    @Test fun `runReviewAsync runs the review for the given id`() {
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(null)

        buildService().runReviewAsync(1L)

        verify(sprintReviewRepo).findSprintReviewById(1L)
        verify(sprintReviewRepo, never()).saveSprintReview(any())
    }

    @Test fun `runReview publishes the finished review into the knowledge base`() {
        stubTranslations()
        stubSuccessfulReview(listOf(ticket("PROJ-1")))
        whenever(knowledgeMapper.fromSprintReview(any())).thenReturn(candidate())

        service.runReview(1L)

        assertEquals(AnalysisStatus.COMPLETED, entity.status)
        assertEquals(listOf("sprint-review:1"), knowledgePublication.published.map { it.externalId })
        verify(aiUsageContext, times(2)).clearContext()
    }

    @Test fun `runReview still completes when knowledge publication breaks`() {
        stubTranslations()
        stubSuccessfulReview(listOf(ticket("PROJ-1")))
        whenever(knowledgeMapper.fromSprintReview(any())).thenReturn(candidate())
        val broken =
            mock<KnowledgePublication> {
                on { publish(any<KnowledgePublicationCandidate>()) } doThrow IllegalStateException("index offline")
            }

        buildService(publication = broken).runReview(1L)

        verify(sprintReviewRepo, atLeastOnce()).saveSprintReview(
            argThat { entity -> entity.status == AnalysisStatus.COMPLETED },
        )
    }

    @Test fun `runReview uses the stored prompt template override`() {
        stubTranslations()
        stubSuccessfulReview(listOf(ticket("PROJ-1")), stored = review(templateOverride = "CUSTOM REVIEW of {ticketKey}"))

        service.runReview(1L)

        verify(copilot, atLeastOnce()).execute(
            eq("model-x"),
            argThat { prompt -> prompt.contains("CUSTOM REVIEW") },
            anyOrNull(),
            anyOrNull(),
        )
        verify(promptLoader, never()).get(any<String>(), eq("sprintreview.review_ticket"), any())
    }

    @Test fun `runReview leaves the review untouched when another writer changed it`() {
        stubTranslations()
        val entity = review()
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(sprintReviewRepo.saveSprintReview(any())).thenThrow(EntityConcurrentlyModifiedException("stale row"))

        service.runReview(1L)

        assertEquals(AnalysisStatus.RUNNING, entity.status)
        verify(aiUsageContext, times(1)).clearContext()
    }

    @Test fun `runReview skips the failure state when the review vanished meanwhile`() {
        stubTranslations()
        val lookups = AtomicInteger(0)
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenAnswer {
            if (lookups.incrementAndGet() == 1) review() else null
        }
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(config())
        whenever(jira.resolveSprintByName(any(), any())).thenThrow(IllegalStateException("sprint gone"))

        service.runReview(1L)

        verify(sprintReviewRepo, times(1)).saveSprintReview(any())
        verify(sprintReviewRepo, never()).saveSprintReview(argThat { stored -> stored.status == AnalysisStatus.FAILED })
        verify(aiUsageContext, times(1)).clearContext()
    }

    @Test fun `runReview reports when the failure state cannot be persisted`() {
        stubTranslations()
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(review())
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(config())
        whenever(jira.resolveSprintByName(any(), any())).thenThrow(IllegalStateException("sprint gone"))
        val saves = AtomicInteger(0)
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            if (saves.incrementAndGet() == 1) {
                invocation.arguments[0] as SprintReview
            } else {
                throw IllegalStateException("db down")
            }
        }

        service.runReview(1L)

        verify(sprintReviewRepo, times(2)).saveSprintReview(any())
    }

    @Test fun `runReview keeps going after a single ticket review fails`() {
        stubTranslations()
        stubSuccessfulReview(listOf(ticket("PROJ-1"), ticket("PROJ-2")))
        val clears = AtomicInteger(0)
        doAnswer {
            if (clears.incrementAndGet() == 1) throw IllegalStateException("usage tracker down")
            null
        }.whenever(aiUsageContext).clearContext()

        service.runReview(1L)

        assertEquals(AnalysisStatus.COMPLETED, entity.status)
        assertEquals(2, entity.totalIssues)
    }

    @Test fun `runReview keeps the table empty when the adf document cannot be encoded`() {
        stubTranslations()
        stubSuccessfulReview(listOf(ticket("PROJ-1")))
        whenever(jira.renderAdfDocument(any(), any(), any())).thenThrow(IllegalStateException("encoder broken"))

        service.runReview(1L)

        assertNull(entity.tableAdfJson)
        assertEquals(AnalysisStatus.COMPLETED, entity.status)
    }

    @Test fun `runReview reports an unknown reason when the sprint cannot be resolved without a message`() {
        stubTranslations()
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(config())
        whenever(jira.resolveSprintByName(any(), any())).thenThrow(RuntimeException())
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        service.runReview(1L)

        assertEquals(AnalysisStatus.FAILED, entity.status)
        assertEquals("translated failure", entity.errorMessage)
    }

    @Test fun `reviewAllTickets returns no rows for a sprint without tickets`() {
        val rows = invokeReviewAllTickets(emptyList())

        assertTrue(rows.isEmpty())
        verify(sprintReviewRepo, never()).saveSprintReview(any())
    }

    @Test fun `runReview fails when the resolved sprint has no tickets`() {
        stubTranslations()
        stubSuccessfulReview(emptyList())

        service.runReview(1L)

        assertEquals(AnalysisStatus.FAILED, entity.status)
        assertEquals("translated failure", entity.errorMessage)
        assertEquals(0, entity.totalIssues)
    }

    @Test fun `runReview ignores the failure state when the repository cannot be read again`() {
        stubTranslations()
        val lookups = AtomicInteger(0)
        whenever(sprintReviewRepo.findSprintReviewById(any())).thenAnswer {
            if (lookups.incrementAndGet() == 1) entity else throw IllegalStateException("connection reset")
        }
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(config())
        whenever(jira.resolveSprintByName(any(), any())).thenThrow(IllegalStateException("sprint gone"))
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        service.runReview(1L)

        assertEquals(AnalysisStatus.RUNNING, entity.status)
        verify(sprintReviewRepo, times(1)).saveSprintReview(any())
    }

    @Test fun `reviewAllTickets keeps going when the status lookup returns nothing`() {
        stubTranslations()
        stubSuccessfulReview(listOf(ticket("PROJ-1")))
        val lookups = AtomicInteger(0)
        whenever(sprintReviewRepo.findSprintReviewById(any())).thenAnswer {
            if (lookups.incrementAndGet() == 1) entity else null
        }

        service.runReview(1L)

        assertEquals(AnalysisStatus.COMPLETED, entity.status)
        assertEquals(1, entity.totalIssues)
    }

    @Test fun `stale review statuses are the ones an aborted run can leave behind`() {
        assertEquals(setOf(AnalysisStatus.PENDING, AnalysisStatus.RUNNING), staleReviewStatuses())
    }

    @Test fun `review context carries the resolved sprint of the run`() {
        val contextClass = Class.forName("com.stacklens.application.service.sprintreview.SprintReviewRunner\$ReviewContext")
        val context = newContext(contextClass)
        val resolved = sprint()
        contextClass
            .getMethod("setSprint", ResolvedSprint::class.java)
            .apply { isAccessible = true }
            .invoke(context, resolved)

        assertEquals(
            resolved,
            contextClass.getMethod("getSprint").apply { isAccessible = true }.invoke(context),
        )
    }

    private fun newContext(contextClass: Class<*>): Any =
        contextClass
            .getDeclaredConstructor(SprintReview::class.java, Long::class.javaPrimitiveType, JiraConfig::class.java)
            .apply { isAccessible = true }
            .newInstance(entity, 1L, config())

    private fun staleReviewStatuses(): Set<*> {
        val companionField = SprintReviewService::class.java.getDeclaredField("Companion")
        companionField.isAccessible = true
        val companion = companionField.get(null)
        val getter = companion.javaClass.getDeclaredMethod("getSTALE_REVIEW_STATUSES")
        getter.isAccessible = true
        return getter.invoke(companion) as Set<*>
    }

    private fun invokeReviewAllTickets(tickets: List<SprintReviewTicket>): List<SprintReviewRow> {
        val contextClass = Class.forName("com.stacklens.application.service.sprintreview.SprintReviewRunner\$ReviewContext")
        val context = newContext(contextClass)
        contextClass
            .getDeclaredField("tickets")
            .apply { isAccessible = true }
            .set(context, tickets)
        val runnerField = SprintReviewService::class.java.getDeclaredField("runner")
        runnerField.isAccessible = true
        val runner = runnerField.get(service)
        val reviewAllTickets = runner.javaClass.getDeclaredMethod("reviewAllTickets", contextClass)
        reviewAllTickets.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return reviewAllTickets.invoke(runner, context) as List<SprintReviewRow>
    }

    private companion object {
        val FIXED_NOW: Instant = Instant.ofEpochMilli(1_700_000_000_000L)
    }
}
