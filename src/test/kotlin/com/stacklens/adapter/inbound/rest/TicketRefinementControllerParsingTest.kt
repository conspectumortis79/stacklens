package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketRefinementUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity

class TicketRefinementControllerParsingTest {
    private lateinit var useCase: TicketRefinementUseCase
    private lateinit var i18n: I18nUseCase
    private lateinit var controller: TicketRefinementController

    @BeforeEach
    fun setUp() {
        useCase = mock()
        i18n = mock()
        controller = TicketRefinementController(useCase, i18n)
        whenever(i18n.t(any<String>(), any())).thenAnswer { invocation -> invocation.arguments[0] as String }
    }

    @Test
    fun `createSubtasks accepts a raw JSON array of drafts`() {
        val body =
            listOf(
                mapOf(
                    "summary" to "task 1",
                    "description" to "d1",
                    "issueType" to "Task",
                    "priority" to "Major",
                    "labels" to listOf("a", "b"),
                ),
                mapOf("summary" to "task 2"),
            )
        val body2 = body.map { it.toMutableMap() }
        val saved =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                jiraConfigId = 10L,
                status = "DONE",
                createdAt = java.time.LocalDateTime.now(),
            )
        whenever(useCase.createSuggestedSubtasks(any(), any(), any())).thenReturn(mapOf("id" to 1L, "ok" to true))
        val response = controller.createSubtasks(1L, "PROJ-1", body2)
        assertEquals(200, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val map = response.body as Map<String, Any?>
        assertEquals(1L, map["id"])
    }

    @Test
    fun `createSubtasks accepts a wrapped object body`() {
        val body = mapOf("subtasks" to listOf(mapOf("summary" to "task X")))
        val saved =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "P",
                jiraConfigId = 1L,
                status = "DONE",
                createdAt = java.time.LocalDateTime.now(),
            )
        whenever(useCase.createSuggestedSubtasks(any(), any(), any())).thenReturn(mapOf("id" to 1L, "ok" to true))
        val response = controller.createSubtasks(1L, "P", body)
        assertEquals(200, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val map = response.body as Map<String, Any?>
        assertEquals(1L, map["id"])
    }

    @Test
    fun `createSubtasks returns 400 when body is not array or wrapped object`() {
        val response = controller.createSubtasks(1L, "P", "garbage")
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body.toString().contains("subtasks_empty"))
    }

    @Test
    fun `createSubtasks returns 400 when all drafts have blank summaries`() {
        val body = listOf(mapOf("summary" to "  "), mapOf("description" to "no summary"))
        val response = controller.createSubtasks(1L, "P", body)
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body.toString().contains("subtasks_empty"))
    }

    @Test
    fun `createSubtasks returns 400 on IllegalArgumentException from use case`() {
        val body = listOf(mapOf("summary" to "x"))
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenThrow(IllegalArgumentException("bad jira state"))
        val response = controller.createSubtasks(1L, "PROJ-1", body)
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body.toString().contains("bad jira state"))
    }

    @Test
    fun `createSubtasks returns 400 on IllegalStateException from use case`() {
        val body = listOf(mapOf("summary" to "x"))
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenThrow(IllegalStateException("already done"))
        val response = controller.createSubtasks(1L, "PROJ-1", body)
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body.toString().contains("already done"))
    }

    @Test
    fun `createSubtasks returns 500 on other Exception from use case`() {
        val body = listOf(mapOf("summary" to "x"))
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenThrow(RuntimeException("network broken"))
        val response = controller.createSubtasks(1L, "PROJ-1", body)
        assertEquals(500, response.statusCode.value())
        assertTrue(response.body.toString().contains("network broken"))
    }

    @Test
    fun `postComment returns 200 on success`() {
        val saved =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                jiraConfigId = 1L,
                status = "DONE",
                createdAt = java.time.LocalDateTime.now(),
            )
        whenever(useCase.postReviewAsComment(any(), any())).thenReturn(saved)
        val response = controller.postComment(1L, "PROJ-1")
        assertEquals(200, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val map = response.body as Map<String, Any?>
        assertEquals(1L, map["id"])
    }

    @Test
    fun `postComment returns 400 on IllegalArgumentException`() {
        whenever(useCase.postReviewAsComment(any(), any()))
            .thenThrow(IllegalArgumentException("bad key"))
        val response = controller.postComment(1L, "BAD")
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body.toString().contains("bad key"))
    }

    @Test
    fun `postComment returns 400 on IllegalStateException`() {
        whenever(useCase.postReviewAsComment(any(), any()))
            .thenThrow(IllegalStateException("closed"))
        val response = controller.postComment(1L, "PROJ-1")
        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `get returns not-found when preparation is missing`() {
        whenever(useCase.getPreparation(any())).thenReturn(null)
        val response: ResponseEntity<Map<String, Any?>> = controller.get(99L)
        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `get returns the full DTO when preparation exists`() {
        val prep =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                jiraConfigId = 1L,
                status = "DONE",
                createdAt = java.time.LocalDateTime.now(),
            )
        whenever(useCase.getPreparation(1L)).thenReturn(prep)
        whenever(useCase.normalizeReviewsJson(null)).thenReturn("[]")
        val response = controller.get(1L)
        assertEquals(200, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val map = response.body as Map<String, Any?>
        assertEquals(1L, map["id"])
        assertEquals("PROJ-1", map["parentTicketKey"])
    }

    @Test
    fun `status returns not-found when preparation is missing`() {
        whenever(useCase.getPreparation(any())).thenReturn(null)
        val response = controller.status(99L)
        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `status returns step info when preparation exists`() {
        val prep =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "P",
                jiraConfigId = 1L,
                status = "RUNNING",
                currentStep = "analyzing",
                createdAt = java.time.LocalDateTime.now(),
            )
        whenever(useCase.getPreparation(1L)).thenReturn(prep)
        val response = controller.status(1L)

        @Suppress("UNCHECKED_CAST")
        val map = response.body as Map<String, Any?>
        assertEquals("analyzing", map["currentStep"])
    }

    @Test
    fun `list returns DTOs with createdAt and completedAt iso string`() {
        val prep =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "P",
                jiraConfigId = 1L,
                status = "DONE",
                createdAt = java.time.LocalDateTime.of(2024, 1, 2, 3, 4),
                completedAt = java.time.LocalDateTime.of(2024, 1, 2, 4, 0),
            )
        whenever(useCase.getAllPreparations()).thenReturn(listOf(prep))
        val list = controller.list()
        assertEquals(1, list.size)
        val map = list[0]
        assertEquals("2024-01-02T03:04", map["createdAt"])
        assertEquals("2024-01-02T04:00", map["completedAt"])
    }

    @Test
    fun `list returns DTO with null completedAt when still running`() {
        val prep =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "P",
                jiraConfigId = 1L,
                status = "RUNNING",
                createdAt = java.time.LocalDateTime.now(),
            )
        whenever(useCase.getAllPreparations()).thenReturn(listOf(prep))
        val list = controller.list()

        val map = list[0]
        assertNull(map["completedAt"])
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
    fun `defaultTemplate returns the configured template`() {
        whenever(useCase.defaultPromptTemplate()).thenReturn("TEMPLATE")
        val response = controller.defaultTemplate()
        assertEquals(200, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val map = response.body as Map<String, String>
        assertEquals("TEMPLATE", map["template"])
    }

    @Test
    fun `parseRepoSelections returns empty for non-list`() {
        val result = controller.parseRepoSelections("not a list")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRepoSelections returns empty for empty list`() {
        val result = controller.parseRepoSelections(emptyList<Map<String, Any?>>())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRepoSelections returns empty when type is missing`() {
        val result = controller.parseRepoSelections(listOf(mapOf("id" to 1L)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRepoSelections returns empty when type is invalid`() {
        val result = controller.parseRepoSelections(listOf(mapOf("type" to "github", "id" to 1L)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRepoSelections returns empty when id is missing`() {
        val result = controller.parseRepoSelections(listOf(mapOf("type" to "local")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRepoSelections returns empty when id is not numeric`() {
        val result = controller.parseRepoSelections(listOf(mapOf("type" to "local", "id" to "not-a-number")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRepoSelections accepts local type with numeric id`() {
        val result = controller.parseRepoSelections(listOf(mapOf("type" to "local", "id" to 42L)))
        assertEquals(1, result.size)
        assertEquals("local", result[0].type)
        assertEquals(42L, result[0].id)
    }

    @Test
    fun `parseRepoSelections accepts gitlab type with projectId`() {
        val result = controller.parseRepoSelections(listOf(mapOf("type" to "gitlab", "id" to 1L, "projectId" to 100L)))
        assertEquals(1, result.size)
        assertEquals("gitlab", result[0].type)
        assertEquals(100L, result[0].projectId)
    }

    @Test
    fun `parseRepoSelections accepts id as String of digits`() {
        val result = controller.parseRepoSelections(listOf(mapOf("type" to "local", "id" to "42")))
        assertEquals(1, result.size)
    }

    @Test
    fun `parseRepoSelections accepts projectId as String of digits`() {
        val result = controller.parseRepoSelections(listOf(mapOf("type" to "gitlab", "id" to 1L, "projectId" to "100")))
        assertEquals(1, result.size)
    }

    @Test
    fun `parseSubtaskDrafts returns empty for non-array`() {
        val result = controller.parseSubtaskDrafts("not an array")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseSubtaskDrafts returns empty for empty array`() {
        val result = controller.parseSubtaskDrafts(emptyList<Map<String, Any?>>())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseSubtaskDrafts accepts a single draft`() {
        val result =
            controller.parseSubtaskDrafts(
                listOf(
                    mapOf(
                        "summary" to "Subtask 1",
                        "description" to "Description",
                        "issueType" to "Task",
                        "priority" to "High",
                        "labels" to listOf("frontend", "backend"),
                    ),
                ),
            )
        assertEquals(1, result.size)
        assertEquals("Subtask 1", result[0].summary)
        assertEquals("Task", result[0].issueType)
        assertEquals("High", result[0].priority)
    }

    @Test
    fun `parseSubtaskDrafts uses defaults when fields are missing`() {
        val result = controller.parseSubtaskDrafts(listOf(mapOf("summary" to "Subtask 1")))
        assertEquals(1, result.size)
        assertEquals("Subtask 1", result[0].summary)
        assertEquals("Subtask", result[0].issueType)
        assertEquals("Minor", result[0].priority)
    }

    @Test
    fun `parseSubtaskDrafts skips drafts with blank summary`() {
        val result =
            controller.parseSubtaskDrafts(
                listOf(
                    mapOf("summary" to ""),
                    mapOf("summary" to "  "),
                    mapOf("summary" to "Valid"),
                ),
            )
        assertEquals(1, result.size)
        assertEquals("Valid", result[0].summary)
    }

    @Test
    fun `parseSubtaskDrafts uses 'Subtask' as default issueType when blank`() {
        val result = controller.parseSubtaskDrafts(listOf(mapOf("summary" to "x", "issueType" to "")))
        assertEquals(1, result.size)
        assertEquals("Subtask", result[0].issueType)
    }

    @Test
    fun `parseSubtaskDrafts uses 'Minor' as default priority when blank`() {
        val result = controller.parseSubtaskDrafts(listOf(mapOf("summary" to "x", "priority" to "")))
        assertEquals(1, result.size)
        assertEquals("Minor", result[0].priority)
    }

    @Test
    fun `parseSubtaskDrafts filters blank labels`() {
        val result =
            controller.parseSubtaskDrafts(
                listOf(
                    mapOf("summary" to "x", "labels" to listOf("a", "", "b", "  ")),
                ),
            )
        assertEquals(1, result.size)
        assertEquals(listOf("a", "b"), result[0].labels)
    }

    @Test
    fun `parseSubtaskDrafts includes existingKey when present`() {
        val result =
            controller.parseSubtaskDrafts(
                listOf(mapOf("summary" to "x")),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `parseSubtaskDrafts skips non-Map entries`() {
        val result = controller.parseSubtaskDrafts(listOf("not a map", mapOf("summary" to "x"), 42))
        assertEquals(1, result.size)
    }

    @Test
    fun `parseDatabaseConfigIds returns empty for non-list`() {
        val result = controller.parseDatabaseConfigIds("not a list", null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseDatabaseConfigIds returns list of ids from a JSON array`() {
        val result = controller.parseDatabaseConfigIds(listOf(1L, 2L, 3L), null)
        assertEquals(listOf(1L, 2L, 3L), result)
    }

    @Test
    fun `parseDatabaseConfigIds filters non-numeric entries`() {
        val result = controller.parseDatabaseConfigIds(listOf(1L, "abc", 2L, null, 3L), null)
        assertEquals(listOf(1L, 2L, 3L), result)
    }

    @Test
    fun `parseDatabaseConfigIds falls back to singleRaw Number`() {
        val result = controller.parseDatabaseConfigIds(null, 42L)
        assertEquals(listOf(42L), result)
    }

    @Test
    fun `parseDatabaseConfigIds falls back to singleRaw String of digits`() {
        val result = controller.parseDatabaseConfigIds(null, "42")
        assertEquals(listOf(42L), result)
    }

    @Test
    fun `parseDatabaseConfigIds falls back to singleRaw String of non-digits returns empty`() {
        val result = controller.parseDatabaseConfigIds(null, "abc")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toFullDto maps all fields`() {
        val prep =
            com.stacklens.domain.model.TicketRefinement(
                id = 1L,
                parentTicketKey = "PROJ-1",
                jiraConfigId = 5L,
                createdAt = java.time.LocalDateTime.of(2024, 1, 1, 0, 0),
                completedAt = java.time.LocalDateTime.of(2024, 1, 2, 0, 0),
            )
        val result = controller.toFullDto(prep)
        assertEquals(1L, result["id"])
        assertEquals("PROJ-1", result["parentTicketKey"])
        assertEquals(5L, result["jiraConfigId"])
    }
}
