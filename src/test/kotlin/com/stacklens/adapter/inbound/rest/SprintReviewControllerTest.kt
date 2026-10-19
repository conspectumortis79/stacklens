package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.SprintReviewUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.SprintReview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class SprintReviewControllerTest {
    private lateinit var useCase: SprintReviewUseCase
    private lateinit var i18n: I18nUseCase
    private lateinit var controller: SprintReviewController

    @BeforeEach
    fun setUp() {
        i18n = mock()
        useCase = mock()
        whenever(i18n.t(any(), any())).thenAnswer { inv -> "i18n:${inv.arguments[0]}" }
        controller = SprintReviewController(useCase, i18n)
    }

    private fun sampleReview(
        id: Long = 42L,
        sprintName: String = "Sprint 1",
        targetTicketKey: String = "PROJ-100",
        status: String = "DONE",
        totalIssues: Int = 5,
        createdAt: LocalDateTime = LocalDateTime.of(2024, 1, 1, 12, 0),
        completedAt: LocalDateTime? = LocalDateTime.of(2024, 1, 1, 13, 0),
    ): SprintReview =
        SprintReview(
            id = id,
            sprintName = sprintName,
            targetTicketKey = targetTicketKey,
            status = status,
            totalIssues = totalIssues,
            createdAt = createdAt,
            completedAt = completedAt,
        )

    @Test
    fun `start rejects blank sprintName with a 400 error`() {
        val response =
            controller.start(mapOf("sprintName" to "   ", "targetTicketKey" to "PROJ-1"))
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body!!.containsKey("error"))
    }

    @Test
    fun `start rejects blank targetTicketKey with a 400 error`() {
        val response =
            controller.start(mapOf("sprintName" to "Sprint 1", "targetTicketKey" to "  "))
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body!!.containsKey("error"))
    }

    @Test
    fun `start rejects missing jira config with a 400 error`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(null)
        val response =
            controller.start(mapOf("sprintName" to "Sprint 1", "targetTicketKey" to "PROJ-1"))
        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `start rejects unknown jiraConfigId with a 400 error`() {
        whenever(useCase.jiraConfigExists(99L)).thenReturn(false)
        val response =
            controller.start(
                mapOf(
                    "sprintName" to "Sprint 1",
                    "targetTicketKey" to "PROJ-1",
                    "jiraConfigId" to 99,
                ),
            )
        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `start accepts numeric jiraConfigId and returns the persisted review`() {
        stubStartReview()
        org.mockito.kotlin
            .doReturn(true)
            .`when`(useCase)
            .jiraConfigExists(7L)
        val response =
            controller.start(
                mapOf(
                    "sprintName" to "Sprint 12",
                    "targetTicketKey" to "PROJ-1",
                    "jiraConfigId" to 7,
                ),
            )
        assertEquals(200, response.statusCode.value())
        assertEquals(7L, response.body!!["id"])
        assertEquals("DONE", response.body!!["status"])
    }

    @Test
    fun `start accepts string-encoded jiraConfigId`() {
        stubStartReview()
        org.mockito.kotlin
            .doReturn(true)
            .`when`(useCase)
            .jiraConfigExists(7L)
        val response =
            controller.start(
                mapOf(
                    "sprintName" to "Sprint 12",
                    "targetTicketKey" to "PROJ-1",
                    "jiraConfigId" to "7",
                ),
            )
        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `start falls back to active config when jiraConfigId is null`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(7L)
        stubStartReview()
        org.mockito.kotlin
            .doReturn(true)
            .`when`(useCase)
            .jiraConfigExists(7L)
        controller.start(mapOf("sprintName" to "Sprint", "targetTicketKey" to "P-1"))
        org.mockito.kotlin
            .verify(useCase, org.mockito.kotlin.times(1))
            .startReview(
                org.mockito.kotlin.eq("Sprint"),
                org.mockito.kotlin.eq("P-1"),
                org.mockito.kotlin.eq(7L),
                org.mockito.kotlin.isNull(),
                org.mockito.kotlin.any(),
            )
    }

    @Test
    fun `start reads includeImprovement=true when supplied as a boolean`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(7L)
        stubStartReview()
        org.mockito.kotlin
            .doReturn(true)
            .`when`(useCase)
            .jiraConfigExists(7L)
        controller.start(
            mapOf(
                "sprintName" to "Sprint",
                "targetTicketKey" to "P-1",
                "includeImprovement" to true,
            ),
        )
        org.mockito.Mockito
            .verify(useCase, org.mockito.Mockito.times(1))
            .startReview(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(Long::class.java),
                org.mockito.ArgumentMatchers.nullable(String::class.java),
                org.mockito.ArgumentMatchers.eq(true),
            )
    }

    @Test
    fun `start reads includeImprovement=false from a string`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(7L)
        stubStartReview()
        org.mockito.kotlin
            .doReturn(true)
            .`when`(useCase)
            .jiraConfigExists(7L)
        controller.start(
            mapOf(
                "sprintName" to "Sprint",
                "targetTicketKey" to "P-1",
                "includeImprovement" to "false",
            ),
        )
        org.mockito.Mockito
            .verify(useCase, org.mockito.Mockito.times(1))
            .startReview(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(Long::class.java),
                org.mockito.ArgumentMatchers.nullable(String::class.java),
                org.mockito.ArgumentMatchers.eq(false),
            )
    }

    private fun stubStartReview() {
        org.mockito.Mockito
            .`when`(
                useCase.startReview(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.nullable(Long::class.java),
                    org.mockito.ArgumentMatchers.nullable(String::class.java),
                    org.mockito.ArgumentMatchers.anyBoolean(),
                ),
            ).thenReturn(sampleReview(id = 7L))
    }

    @Test
    fun `list maps all reviews into the lightweight DTO shape`() {
        whenever(useCase.getAllReviews())
            .thenReturn(listOf(sampleReview(id = 1L), sampleReview(id = 2L)))
        val response = controller.list()
        assertEquals(2, response.size)
        assertEquals(1L, response[0]["id"])
        assertEquals("Sprint 1", response[0]["sprintName"])
        assertEquals("PROJ-100", response[0]["targetTicketKey"])
        assertEquals("DONE", response[0]["status"])
        assertEquals(5, response[0]["totalIssues"])
        assertTrue(response[0]["createdAt"] != null)
        assertTrue(response[0]["completedAt"] != null)
    }

    @Test
    fun `get returns 404 when the review is not found`() {
        whenever(useCase.getReview(99L)).thenReturn(null)
        val response = controller.get(99L)
        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get returns 200 with full DTO when the review is found`() {
        whenever(useCase.getReview(1L)).thenReturn(sampleReview(id = 1L))
        val response = controller.get(1L)
        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(1L, body["id"])
        assertEquals("Sprint 1", body["sprintName"])
        assertEquals("PROJ-100", body["targetTicketKey"])
        assertEquals("DONE", body["status"])
    }

    @Test
    fun `status returns 404 when review is missing`() {
        whenever(useCase.getReview(99L)).thenReturn(null)
        val response = controller.status(99L)
        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `status returns lightweight fields for an existing review`() {
        whenever(useCase.getReview(1L)).thenReturn(sampleReview(id = 1L))
        val response = controller.status(1L)
        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(1L, body["id"])
        assertEquals("DONE", body["status"])
        assertEquals(5, body["totalIssues"])
    }

    @Test
    fun `cancel returns 204`() {
        val response = controller.cancel(1L)
        assertEquals(204, response.statusCode.value())
    }

    @Test
    fun `delete returns 204`() {
        val response = controller.delete(1L)
        assertEquals(204, response.statusCode.value())
    }

    @Test
    fun `deleteAll returns 204`() {
        val response = controller.deleteAll()
        assertEquals(204, response.statusCode.value())
    }

    @Test
    fun `defaultTemplate returns the use-case template`() {
        whenever(useCase.defaultPromptTemplate()).thenReturn("TEMPLATE")
        val response = controller.defaultTemplate()
        assertEquals(200, response.statusCode.value())
        assertEquals("TEMPLATE", response.body!!["template"])
    }

    @Test
    fun `postToJira returns 200 with the full DTO on success`() {
        whenever(useCase.postAdfToJira(1L)).thenReturn(sampleReview(id = 1L))
        val response = controller.postToJira(1L)
        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals(1L, body["id"])
        assertEquals("Sprint 1", body["sprintName"])
    }

    @Test
    fun `postToJira maps IllegalArgumentException to a 400`() {
        whenever(useCase.postAdfToJira(1L)).thenThrow(IllegalArgumentException("bad"))
        val response = controller.postToJira(1L)
        assertEquals(400, response.statusCode.value())
        assertEquals("bad", response.body!!["error"])
    }

    @Test
    fun `postToJira maps IllegalStateException to a 400`() {
        whenever(useCase.postAdfToJira(1L)).thenThrow(IllegalStateException("nope"))
        val response = controller.postToJira(1L)
        assertEquals(400, response.statusCode.value())
        assertEquals("nope", response.body!!["error"])
    }

    @Test
    fun `postToJira maps generic exceptions to a 500`() {
        whenever(useCase.postAdfToJira(1L)).thenThrow(RuntimeException("kaboom"))
        val response = controller.postToJira(1L)
        assertEquals(500, response.statusCode.value())
        assertEquals("kaboom", response.body!!["error"])
    }

    @Test
    fun `toFullDto maps the SprintReview entity to a stable DTO shape`() {
        val review =
            sampleReview(
                id = 7L,
                sprintName = "Sprint 99",
                targetTicketKey = "P-1",
                status = "RUNNING",
                totalIssues = 12,
                createdAt = LocalDateTime.of(2024, 5, 1, 9, 30),
                completedAt = null,
            )
        val dto = controller.toFullDto(review)
        assertEquals(7L, dto["id"])
        assertEquals("Sprint 99", dto["sprintName"])
        assertEquals("P-1", dto["targetTicketKey"])
        assertEquals("RUNNING", dto["status"])
        assertEquals(12, dto["totalIssues"])
        assertNull(dto["completedAt"], "completedAt is null so the DTO should reflect that")
        assertNotNull(dto["createdAt"])
    }
}
