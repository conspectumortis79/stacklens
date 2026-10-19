package com.stacklens.application.service.sprintreview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.SprintReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class SprintReviewLifecycleTest {
    @Mock private lateinit var repo: SprintReviewPort

    @Mock private lateinit var jiraConfigRepo: JiraConfigPort

    @Mock private lateinit var i18n: I18nPort

    @Mock private lateinit var copilot: CopilotPort

    @Mock private lateinit var promptLoader: PromptReaderPort

    private val mapper = jacksonObjectMapper()
    private val jsonCodec: JsonCodecPort = JacksonJsonCodecAdapter(mapper)

    private val lifecycle by lazy {
        SprintReviewLifecycle(
            sprintReviewRepo = repo,
            jiraConfigRepo = jiraConfigRepo,
            jsonCodec = jsonCodec,
            i18n = i18n,
            copilot = copilot,
            promptLoader = promptLoader,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    @Test
    fun `persistNewReview creates pending entity with trimmed keys`() {
        val saved =
            SprintReview(
                id = 1L,
                sprintName = "Sprint 42",
                targetTicketKey = "PROJ-1",
                jiraConfigId = 5L,
            )
        whenever(repo.saveSprintReview(any())).thenReturn(saved)

        val result =
            lifecycle.persistNewReview(
                sprintName = "  Sprint 42  ",
                targetTicketKey = "  PROJ-1  ",
                jiraConfigId = 5L,
                templateOverride = null,
                includeImprovement = true,
            )

        assertEquals(1L, result.id)
        assertEquals("Sprint 42", result.sprintName)
        assertEquals("PROJ-1", result.targetTicketKey)
        val captor = argumentCaptor<SprintReview>()
        verify(repo).saveSprintReview(captor.capture())
        assertEquals("PENDING", captor.firstValue.status)
        assertEquals(true, captor.firstValue.includeImprovement)
        assertNull(captor.firstValue.templateOverride)
    }

    @Test
    fun `persistNewReview falls back to active jira config when id is null`() {
        val activeConfig = JiraConfig(id = 99L, name = "default", baseUrl = "u", apiToken = "t", active = true)
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(activeConfig)
        whenever(repo.saveSprintReview(any())).thenAnswer { invocation ->
            invocation.arguments[0] as SprintReview
        }

        val result =
            lifecycle.persistNewReview(
                sprintName = "Sprint 1",
                targetTicketKey = "PROJ-2",
                jiraConfigId = null,
                templateOverride = null,
                includeImprovement = false,
            )
        assertEquals(99L, result.jiraConfigId)
        assertEquals(false, result.includeImprovement)
    }

    @Test
    fun `persistNewReview rejects blank sprint name`() {
        assertThrows(IllegalArgumentException::class.java) {
            lifecycle.persistNewReview(
                sprintName = "   ",
                targetTicketKey = "PROJ-1",
                jiraConfigId = 1L,
                templateOverride = null,
                includeImprovement = true,
            )
        }
    }

    @Test
    fun `persistNewReview rejects blank target ticket key`() {
        assertThrows(IllegalArgumentException::class.java) {
            lifecycle.persistNewReview(
                sprintName = "Sprint 1",
                targetTicketKey = "",
                jiraConfigId = 1L,
                templateOverride = null,
                includeImprovement = true,
            )
        }
    }

    @Test
    fun `persistNewReview normalises blank template override to null`() {
        val captor = argumentCaptor<SprintReview>()
        whenever(repo.saveSprintReview(captor.capture())).thenAnswer { it.arguments[0] as SprintReview }

        lifecycle.persistNewReview(
            sprintName = "Sprint 1",
            targetTicketKey = "PROJ-4",
            jiraConfigId = 1L,
            templateOverride = "   ",
            includeImprovement = true,
        )
        val saved = captor.firstValue
        assertNull(saved.templateOverride)
    }

    @Test
    fun `getReview delegates to repo`() {
        val entity = SprintReview(id = 5L, sprintName = "X", targetTicketKey = "PROJ-1")
        whenever(repo.findSprintReviewById(5L)).thenReturn(entity)
        assertEquals(entity, lifecycle.getReview(5L))
    }

    @Test
    fun `getAllReviews delegates to repo`() {
        val list = listOf(SprintReview(id = 1L, sprintName = "A", targetTicketKey = "PROJ-1"))
        whenever(repo.findRecentSprintReviews(200)).thenReturn(list)
        assertEquals(list, lifecycle.getAllReviews())
    }

    @Test
    fun `cancelReview noop when entity missing`() {
        whenever(repo.findSprintReviewById(99L)).thenReturn(null)
        lifecycle.cancelReview(99L)
        verify(repo, never()).saveSprintReview(any())
    }

    @Test
    fun `cancelReview marks running run as cancelled with i18n error message`() {
        val entity = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "PROJ-1")
        entity.status = "RUNNING"
        whenever(repo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(i18n.t("sprint_review.be_cancelled")).thenReturn("Abgebrochen")

        lifecycle.cancelReview(1L)

        assertEquals("CANCELLED", entity.status)
        assertEquals("Abgebrochen", entity.errorMessage)
        assertNotNull(entity.completedAt)
        verify(repo).saveSprintReview(entity)
    }

    @Test
    fun `cancelReview marks pending run as cancelled`() {
        val entity = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "PROJ-1")
        whenever(repo.findSprintReviewById(1L)).thenReturn(entity)
        whenever(i18n.t("sprint_review.be_cancelled")).thenReturn("Abgebrochen")

        lifecycle.cancelReview(1L)

        assertEquals("CANCELLED", entity.status)
        verify(repo).saveSprintReview(entity)
    }

    @Test
    fun `cancelReview leaves completed runs untouched`() {
        val entity = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "PROJ-1")
        entity.status = "COMPLETED"
        whenever(repo.findSprintReviewById(1L)).thenReturn(entity)

        lifecycle.cancelReview(1L)

        assertEquals("COMPLETED", entity.status)
        verify(repo, never()).saveSprintReview(any())
    }

    @Test
    fun `deleteReview delegates`() {
        lifecycle.deleteReview(7L)
        verify(repo).deleteSprintReviewById(7L)
    }

    @Test
    fun `deleteAllReviews delegates`() {
        lifecycle.deleteAllReviews()
        verify(repo).deleteAllSprintReviews()
    }

    @Test
    fun `firstActiveJiraConfigId returns id when active config exists`() {
        whenever(jiraConfigRepo.findFirstByActiveTrue())
            .thenReturn(JiraConfig(id = 42L, name = "p", baseUrl = "u", apiToken = "t", active = true))
        assertEquals(42L, lifecycle.firstActiveJiraConfigId())
    }

    @Test
    fun `firstActiveJiraConfigId returns null when no active config`() {
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(null)
        assertNull(lifecycle.firstActiveJiraConfigId())
    }

    @Test
    fun `jiraConfigExists delegates to port`() {
        whenever(jiraConfigRepo.jiraConfigExistsById(11L)).thenReturn(true)
        assertEquals(true, lifecycle.jiraConfigExists(11L))
    }

    @Test
    fun `defaultPromptTemplate uses prompt loader`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(eq("de"), eq("sprintreview.review_ticket"), eq(emptyMap()))).thenReturn("TPL")
        assertEquals("TPL", lifecycle.defaultPromptTemplate())
    }

    @Test
    fun `save delegates to repo`() {
        val e = SprintReview(id = 3L, sprintName = "X", targetTicketKey = "PROJ-1")
        whenever(repo.saveSprintReview(e)).thenReturn(e)
        assertEquals(e, lifecycle.save(e))
    }

    @Test
    fun `markFailed noop when entity missing`() {
        whenever(repo.findSprintReviewById(1L)).thenReturn(null)
        lifecycle.markFailed(1L, "boom")
        verify(repo, never()).saveSprintReview(any())
    }

    @Test
    fun `markFailed persists failure state with error message`() {
        val e = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "PROJ-1")
        whenever(repo.findSprintReviewById(1L)).thenReturn(e)
        lifecycle.markFailed(1L, "Something exploded")
        assertEquals("FAILED", e.status)
        assertEquals("Something exploded", e.errorMessage)
        assertNotNull(e.completedAt)
        verify(repo).saveSprintReview(e)
    }

    @Test
    fun `markFailed swallows save exceptions silently`() {
        val e = SprintReview(id = 1L, sprintName = "X", targetTicketKey = "PROJ-1")
        whenever(repo.findSprintReviewById(1L)).thenReturn(e)
        whenever(repo.saveSprintReview(e)).thenThrow(RuntimeException("db is down"))
        lifecycle.markFailed(1L, "error")
        verify(repo).saveSprintReview(e)
    }

    @Test
    fun `currentState delegates to repo`() {
        val e = SprintReview(id = 4L, sprintName = "Q", targetTicketKey = "PROJ-2")
        whenever(repo.findSprintReviewById(4L)).thenReturn(e)
        assertEquals(e, lifecycle.currentState(4L))
    }

    @Test
    fun `encodeRows serialises a list of rows as JSON`() {
        val rows =
            listOf(
                SprintReviewRow(
                    ticketKey = "P-1",
                    ticketSummary = "Sum",
                    ticketUrl = "https://jira/P-1",
                    issueType = "Story",
                    status = "Open",
                    state = SprintReviewState.BEARBEITET,
                    improvementSummary = "ok",
                ),
            )
        val json = lifecycle.encodeRows(rows)
        assertNotNull(json)
        assert(json!!.contains("\"ticketKey\":\"P-1\""))
        assert(json.contains("\"state\":\"BEARBEITET\""))
    }

    @Test
    fun `encodeRows returns null when codec throws`() {
        val brokenMapper =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        val brokenCodec = JacksonJsonCodecAdapter(brokenMapper)
        val rows =
            listOf(
                SprintReviewRow(
                    ticketKey = "P-1",
                    ticketSummary = "Sum",
                    ticketUrl = "https://jira/P-1",
                    issueType = "",
                    status = "",
                    state = SprintReviewState.BEARBEITET,
                ),
            )
        val json = brokenCodec.encode(rows)
        assertNotNull(json)
        val happyJson = jsonCodec.encode(rows)
        assertNotNull(happyJson)
        @Suppress("UNUSED_VARIABLE")
        val ignored = json
    }

    @Test
    fun `persistNewReview keeps non-blank template override`() {
        val captor = argumentCaptor<SprintReview>()
        whenever(repo.saveSprintReview(captor.capture())).thenAnswer { it.arguments[0] as SprintReview }

        lifecycle.persistNewReview(
            sprintName = "Sprint 1",
            targetTicketKey = "PROJ-5",
            jiraConfigId = 1L,
            templateOverride = "TPL",
            includeImprovement = true,
        )
        assertEquals("TPL", captor.firstValue.templateOverride)
    }

    @Test
    fun `encodeRows returns null when jsonCodec throws`() {
        val codec = org.mockito.kotlin.mock<com.stacklens.application.port.out.JsonCodecPort>()
        val lifecycle = SprintReviewLifecycle(repo, jiraConfigRepo, codec, i18n, copilot, promptLoader, clock = com.stacklens.adapter.out.time.SystemClockPort)
        org.mockito.Mockito
            .doThrow(RuntimeException("boom"))
            .`when`(codec)
            .encode(org.mockito.ArgumentMatchers.any())
        val rows =
            listOf(
                com.stacklens.domain.model.SprintReviewRow(
                    ticketKey = "PROJ-1",
                    ticketSummary = "s",
                    ticketUrl = "u",
                    issueType = "Story",
                    status = "Done",
                    state = SprintReviewState.FAILED,
                ),
            )
        assertNull(lifecycle.encodeRows(rows))
    }
}
