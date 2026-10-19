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
import com.stacklens.application.service.sprintreview.SprintReviewLifecycle
import com.stacklens.application.service.sprintreview.SprintReviewOrchestrator
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReview
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.InlineAsyncExecutor
import com.stacklens.testsupport.TestExecutors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

class SprintReviewServiceTest {
    private val sprintReviewRepo: SprintReviewPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val jira: SprintReviewJiraPort = mock()
    private val copilot: CopilotPort = mock()
    private val aiUsageContext: AiUsageContextPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val jsonCodec: JsonCodecPort = mock()
    private val i18n: I18nPort = mock()
    private val asyncExecutor: AsyncExecutorPort = InlineAsyncExecutor()

    private val service by lazy {
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
            reviewExecutor = TestExecutors.daemon(),
            knowledgePublication = FakeKnowledgePublicationPort(),
            knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
            lifecycle = SprintReviewLifecycle(sprintReviewRepo, jiraConfigRepo, jsonCodec, i18n, copilot, promptLoader, clock = com.stacklens.adapter.out.time.SystemClockPort),
            orchestrator = SprintReviewOrchestrator(copilot, promptLoader, i18n),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    private fun jiraConfig(id: Long = 1L): JiraConfig = JiraConfig(id = id, name = "cfg", baseUrl = "https://example.atlassian.net", apiToken = "tok", cloudMode = true)

    private fun ticket(
        key: String = "PROJ-1",
        sp: Double? = 3.0,
    ): SprintReviewTicket =
        SprintReviewTicket(
            key = key,
            summary = "Sum $key",
            description = "Desc $key",
            url = "https://example.atlassian.net/browse/$key",
            issueType = "Story",
            status = "Open",
            assignee = "Alice",
            storyPoints = sp,
        )

    @Test
    fun `startReview persists entity and dispatches`() {
        val saved = SprintReview(id = 1L, sprintName = "Sprint 42", targetTicketKey = "PROJ-100")
        whenever(sprintReviewRepo.saveSprintReview(any())).thenReturn(saved)

        val result =
            service.startReview(
                sprintName = "Sprint 42",
                targetTicketKey = "PROJ-100",
                jiraConfigId = null,
                templateOverride = null,
                includeImprovement = true,
            )
        assertEquals(1L, result.id)
        verify(sprintReviewRepo).findSprintReviewById(1L)
    }

    @Test
    fun `runReview is a no-op when entity does not exist`() {
        whenever(sprintReviewRepo.findSprintReviewById(99L)).thenReturn(null)
        service.runReview(99L)
        verify(sprintReviewRepo, never()).saveSprintReview(any())
    }

    @Test
    fun `runReview is a no-op when entity is already RUNNING`() {
        val entity = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "PROJ-1", status = "RUNNING")
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        service.runReview(1L)
        verify(jira, never()).resolveSprintByName(any(), any())
    }

    @Test
    fun `runReview happy path resolves sprint, fetches tickets, classifies and persists ADF (no auto-post)`() {
        val entity = SprintReview(id = 1L, sprintName = "Sprint 42", targetTicketKey = "PROJ-100", jiraConfigId = 1L)
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(jiraConfig())
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(eq("gpt-4"), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"improvementSummary":"Refactors the auth flow.","openPoints":[]}""",
        )
        whenever(i18n.tFor(any<String>(), any<String>())).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            key
        }
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            key
        }
        whenever(i18n.tFor(any<String>(), any<String>(), any(), any())).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            key
        }
        whenever(i18n.tFor(any<String>(), any<String>(), any(), any(), any())).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            key
        }
        whenever(i18n.tFor(any<String>(), any<String>(), any(), any(), any(), any())).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            key
        }
        whenever(i18n.t(any<String>())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            key
        }
        whenever(jsonCodec.encode(any<Map<String, Any?>>())).thenReturn("""{"type":"doc"}""")
        whenever(jira.renderAdfDocument(any(), any(), any())).thenReturn(SprintReviewAdfDocument("""{"type":"doc"}"""))
        whenever(i18n.translateError(any())).thenReturn("err")
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("PROMPT_TEMPLATE")
        whenever(
            jira.resolveSprintByName(
                any(),
                eq("Sprint 42"),
            ),
        ).thenReturn(
            ResolvedSprint(
                id = 42L,
                name = "Sprint 42",
                state = "active",
                boardId = 7L,
                boardName = "My Board",
                url = "https://example.atlassian.net/secure/RapidBoard.jspa?rapidView=7",
            ),
        )
        whenever(jira.fetchSprintTickets(any(), eq(42L))).thenReturn(
            listOf(
                ticket("PROJ-1", sp = 3.0),
                ticket("PROJ-2", sp = null),
            ),
        )
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        service.runReview(1L)

        verify(sprintReviewRepo, org.mockito.kotlin.atLeastOnce()).saveSprintReview(
            argThat { e -> e.id == 1L && e.status == "COMPLETED" && e.tableAdfJson != null },
        )

        verify(jira, org.mockito.kotlin.never()).appendAdfToDescription(any(), any(), any())

        verify(aiUsageContext, org.mockito.kotlin.atLeastOnce()).clearContext()
    }

    @Test
    fun `runReview marks FAILED when jira config is missing`() {
        val entity = SprintReview(id = 1L, sprintName = "Sprint 1", targetTicketKey = "PROJ-1")
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(null)
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.t(any<String>())).thenReturn("No active Jira configuration")
        whenever(i18n.tFor(any<String>(), any<String>())).thenReturn("No active Jira configuration")
        whenever(i18n.translateError(any())).thenReturn("Error")
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        service.runReview(1L)

        verify(sprintReviewRepo, org.mockito.kotlin.atLeastOnce()).saveSprintReview(
            argThat { e -> e.id == 1L && e.status == "FAILED" },
        )
        verify(aiUsageContext, org.mockito.kotlin.atLeastOnce()).clearContext()
    }

    @Test
    fun `runReview marks FAILED when sprint cannot be resolved`() {
        val entity = SprintReview(id = 1L, sprintName = "Nope", targetTicketKey = "PROJ-1")
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(jiraConfig())
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.t(any<String>())).thenReturn("err")
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenReturn("err")
        whenever(jira.resolveSprintByName(any(), any())).thenThrow(IllegalStateException("Sprint not found"))
        whenever(i18n.translateError(any())).thenReturn("Sprint 'Nope' could not be resolved: Sprint not found")
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        service.runReview(1L)

        verify(sprintReviewRepo, org.mockito.kotlin.atLeastOnce()).saveSprintReview(
            argThat { e -> e.id == 1L && e.status == "FAILED" },
        )
    }

    @Test
    fun `runReview marks FAILED when sprint has no tickets`() {
        val entity = SprintReview(id = 1L, sprintName = "Sprint 1", targetTicketKey = "PROJ-1")
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(jiraConfig())
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.t(any<String>())).thenReturn("err")
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenReturn("err")
        whenever(
            jira.resolveSprintByName(any(), any()),
        ).thenReturn(
            ResolvedSprint(id = 1L, name = "Sprint 1", state = "active", url = "u"),
        )
        whenever(jira.fetchSprintTickets(any(), any())).thenReturn(emptyList())
        whenever(i18n.translateError(any())).thenReturn("err")
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        service.runReview(1L)

        verify(sprintReviewRepo, org.mockito.kotlin.atLeastOnce()).saveSprintReview(
            argThat { e -> e.id == 1L && e.status == "FAILED" },
        )
    }

    @Test
    fun `runReview aborts when entity is cancelled between ticket reviews`() {
        val entity = SprintReview(id = 1L, sprintName = "Sprint 1", targetTicketKey = "PROJ-1", jiraConfigId = 1L)
        val callCount = AtomicInteger(0)

        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenAnswer {
            val current = SprintReview(id = 1L, sprintName = "Sprint 1", targetTicketKey = "PROJ-1", jiraConfigId = 1L)
            if (callCount.incrementAndGet() >= 2) current.status = "CANCELLED"
            current
        }
        whenever(jiraConfigRepo.findJiraConfigById(any())).thenReturn(jiraConfig())
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.execute(eq("gpt-4"), any(), anyOrNull(), anyOrNull())).thenReturn(
            """{"improvementSummary":"ok","openPoints":[]}""",
        )
        whenever(i18n.t(any<String>())).thenReturn("")
        whenever(i18n.tFor(any<String>(), any<String>())).thenReturn("")
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenReturn("")
        whenever(i18n.translateError(any())).thenReturn("")
        whenever(
            jira.resolveSprintByName(any(), any()),
        ).thenReturn(
            ResolvedSprint(id = 1L, name = "Sprint 1", state = "active", url = "u"),
        )
        whenever(jira.fetchSprintTickets(any(), any())).thenReturn(
            listOf(ticket("PROJ-1"), ticket("PROJ-2"), ticket("PROJ-3")),
        )
        whenever(jsonCodec.encode(any<List<Any>>())).thenReturn("[]")

        service.runReview(1L)

        verify(jira, never()).appendAdfToDescription(any(), any(), any())
        verify(aiUsageContext, org.mockito.kotlin.atLeastOnce()).clearContext()
    }

    @Test
    fun `getReview delegates to lifecycle`() {
        val e = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "P-1")
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(e)
        assertEquals(e, service.getReview(1L))
    }

    @Test
    fun `cancelReview delegates to lifecycle`() {
        val e = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "P-1", status = "RUNNING")
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(e)
        whenever(i18n.t("sprint_review.be_cancelled")).thenReturn("Abgebrochen")
        service.cancelReview(1L)
        assertEquals("CANCELLED", e.status)
    }

    @Test
    fun `postAdfToJira posts the persisted ADF to the target ticket and clears any prior error`() {
        val entity =
            SprintReview(
                id = 1L,
                sprintName = "Sprint 42",
                targetTicketKey = "PROJ-100",
                jiraConfigId = 1L,
                tableAdfJson = """{"type":"doc","version":1,"content":[]}""",
                errorMessage = "old failure",
            )
        val adfCaptor = org.mockito.kotlin.argumentCaptor<SprintReviewAdfDocument>()
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(jiraConfig())

        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.t(any<String>())).thenReturn("step_done_posted")
        whenever(i18n.tFor(any<String>(), any<String>())).thenReturn("step_done_posted")
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenReturn("step_done_posted")
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { it.arguments[0] as SprintReview }

        val updated = service.postAdfToJira(1L)

        verify(jira).appendAdfToDescription(any(), eq("PROJ-100"), adfCaptor.capture())
        assertEquals("""{"type":"doc","version":1,"content":[]}""", adfCaptor.firstValue.json)

        assertEquals(null, updated.errorMessage)
        assertEquals("step_done_posted", updated.currentStep)
    }

    @Test
    fun `postAdfToJira records the failure when the Jira PUT throws`() {
        val entity =
            SprintReview(
                id = 1L,
                sprintName = "Sprint 42",
                targetTicketKey = "PROJ-100",
                jiraConfigId = 1L,
                tableAdfJson = """{"type":"doc","version":1,"content":[]}""",
            )
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(jiraConfigRepo.findJiraConfigById(1L)).thenReturn(jiraConfig())

        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            val arg = invocation.arguments[2] as String
            "$key: $arg"
        }
        whenever(sprintReviewRepo.saveSprintReview(any())).thenAnswer { it.arguments[0] as SprintReview }
        whenever(jira.appendAdfToDescription(any(), any(), any()))
            .thenThrow(RuntimeException("HTTP 500"))

        val updated = service.postAdfToJira(1L)

        assertEquals(
            true,
            updated.errorMessage!!.contains("err_post_failed"),
            "Expected errorMessage to contain 'err_post_failed', got: ${updated.errorMessage}",
        )
        assertEquals(
            true,
            updated.errorMessage!!.contains("HTTP 500"),
            "Expected errorMessage to contain 'HTTP 500', got: ${updated.errorMessage}",
        )
    }

    @Test
    fun `postAdfToJira throws IllegalArgumentException when the run has no target ticket key`() {
        val entity =
            SprintReview(
                id = 1L,
                sprintName = "Sprint 42",
                targetTicketKey = "",
                jiraConfigId = 1L,
                tableAdfJson = """{"type":"doc"}""",
            )
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenReturn("err_post_failed")
        val ex = assertThrows(IllegalArgumentException::class.java) { service.postAdfToJira(1L) }
        assertTrue(ex.message!!.contains("target ticket key"))
    }

    @Test
    fun `postAdfToJira throws IllegalStateException when the run has no ADF document`() {
        val entity =
            SprintReview(
                id = 1L,
                sprintName = "Sprint 42",
                targetTicketKey = "PROJ-100",
                jiraConfigId = 1L,
                tableAdfJson = null,
            )
        whenever(sprintReviewRepo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(i18n.tFor(any<String>(), any<String>(), any<String>())).thenReturn("err_post_failed")
        val ex = assertThrows(IllegalStateException::class.java) { service.postAdfToJira(1L) }
        assertTrue(ex.message!!.contains("ADF document"))
    }

    @Test
    fun `postAdfToJira throws IllegalArgumentException when the run id is unknown`() {
        whenever(sprintReviewRepo.findSprintReviewById(99L)).thenReturn(null)
        val ex = assertThrows(IllegalArgumentException::class.java) { service.postAdfToJira(99L) }
        assertTrue(ex.message!!.contains("not found"))
    }

    @Test
    fun `firstActiveJiraConfigId delegates to port`() {
        whenever(jiraConfigRepo.findFirstByActiveTrue())
            .thenReturn(JiraConfig(id = 7L, name = "p", baseUrl = "u", apiToken = "t", active = true))
        assertEquals(7L, service.firstActiveJiraConfigId())
    }

    @Test
    fun `defaultPromptTemplate delegates to prompt loader`() {
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("sprintreview.review_ticket"), eq(emptyMap()))).thenReturn("TPL")
        assertEquals("TPL", service.defaultPromptTemplate())
    }
}
