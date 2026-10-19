package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketRefinementUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.TicketRefinement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class TicketRefinementControllerTest {
    private lateinit var useCase: TicketRefinementUseCase
    private lateinit var i18n: I18nUseCase
    private lateinit var controller: TicketRefinementController

    @BeforeEach
    fun setup() {
        useCase = mock()
        i18n = mock()
        controller = TicketRefinementController(useCase, i18n)
        whenever(i18n.t(any<String>(), any())).thenAnswer { invocation ->
            invocation.arguments[0] as String
        }
    }

    private fun refinement(
        id: Long = 1L,
        parent: String = "PROJ-1",
        status: String = "RUNNING",
        jiraConfigId: Long = 10L,
    ): TicketRefinement =
        TicketRefinement(
            id = id,
            parentTicketKey = parent,
            jiraConfigId = jiraConfigId,
            status = status,
            createdAt = LocalDateTime.now(),
            completedAt = null,
        )

    @Test
    fun `start returns 400 when parentTicketKey is blank`() {
        val result = controller.start(mapOf("parentTicketKey" to ""))
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `start returns 400 when parentTicketKey is missing`() {
        val result = controller.start(emptyMap())
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `start returns 400 when jiraConfigId is not provided and no active config`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(null)
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                ),
            )
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `start returns 400 when jiraConfigId does not exist`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(99L)
        whenever(useCase.jiraConfigExists(99L)).thenReturn(false)
        val result = controller.start(mapOf("parentTicketKey" to "PROJ-1"))
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `start accepts numeric jiraConfigId`() {
        whenever(useCase.jiraConfigExists(eq(42L))).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement(id = 7L))
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 42.0,
                ),
            )
        assertEquals(200, result.statusCode.value())
        assertEquals(7L, result.body!!["id"])
    }

    @Test
    fun `start accepts string jiraConfigId`() {
        whenever(useCase.jiraConfigExists(eq(42L))).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement(id = 7L))
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to "42",
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start defaults to first active jiraConfigId when body has no jiraConfigId`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(77L)
        whenever(useCase.jiraConfigExists(eq(77L))).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                eq(77L),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement(id = 11L))
        val result = controller.start(mapOf("parentTicketKey" to "PROJ-1"))
        assertEquals(200, result.statusCode.value())
        verify(useCase).startPreparation(
            eq("PROJ-1"),
            eq(77L),
            anyOrNull(),
            any(),
            eq(true),
            any(),
        )
    }

    @Test
    fun `start parses repoSelections`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "repoSelections" to
                        listOf(
                            mapOf("type" to "local", "id" to 5),
                            mapOf("type" to "gitlab", "id" to "10", "projectId" to 99),
                            mapOf("type" to "unsupported", "id" to 1),
                            mapOf("id" to 1),
                        ),
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start parses databaseConfigIds list and single legacy id`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result1 =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "databaseConfigIds" to listOf(1.0, 2.0, 3.0),
                ),
            )
        assertEquals(200, result1.statusCode.value())
        val result2 =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "databaseConfigId" to 5.0,
                ),
            )
        assertEquals(200, result2.statusCode.value())
    }

    @Test
    fun `start parses databaseConfigIds as String list`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "databaseConfigIds" to listOf("1", "2", "3"),
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start parses legacy databaseConfigId as String`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "databaseConfigId" to "5",
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start parses legacy databaseConfigId as non-numeric String returns empty list`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "databaseConfigId" to "not-a-number",
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start parses repoSelections with string IDs`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "repoSelections" to
                        listOf(
                            mapOf("type" to "local", "id" to "1", "projectId" to "10"),
                            mapOf("type" to "gitlab", "id" to 2, "projectId" to 20),
                        ),
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start rejects repoSelections with invalid type`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "repoSelections" to
                        listOf(
                            mapOf("type" to "invalid", "id" to 1),
                        ),
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start rejects repoSelections with missing type`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "repoSelections" to
                        listOf(
                            mapOf("id" to 1),
                        ),
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start rejects repoSelections with non-numeric id`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "repoSelections" to
                        listOf(
                            mapOf("type" to "local", "id" to "not-a-number"),
                        ),
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start defaults singleTicketOnly to true`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                eq(true),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start honors singleTicketOnly boolean false`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                eq(false),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "singleTicketOnly" to false,
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start honors singleTicketOnly string true`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                eq(true),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "singleTicketOnly" to "TRUE",
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start uses templateOverride when present and non-blank`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                any(),
                eq("custom-template"),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "templateOverride" to "custom-template",
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start ignores blank templateOverride`() {
        whenever(useCase.jiraConfigExists(any())).thenReturn(true)
        whenever(
            useCase.startPreparation(
                any(),
                any(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(refinement())
        val result =
            controller.start(
                mapOf(
                    "parentTicketKey" to "PROJ-1",
                    "jiraConfigId" to 1.0,
                    "templateOverride" to "   ",
                ),
            )
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `start with a missing parent key returns the raw key as error`() {
        controller =
            TicketRefinementController(
                useCase,
                com.stacklens.application.service
                    .FakeI18n(),
            )
        val result = controller.start(mapOf("parentTicketKey" to ""))
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!["error"]!!.toString().contains("ticket_refinement"))
    }

    @Test
    fun `list returns summaries for all preparations`() {
        val e1 = refinement(id = 1L, parent = "PROJ-1", status = "COMPLETED")
        val e2 = refinement(id = 2L, parent = "PROJ-2", status = "RUNNING")
        whenever(useCase.getAllPreparations()).thenReturn(listOf(e1, e2))
        val result = controller.list()
        assertEquals(2, result.size)
        assertEquals(1L, result[0]["id"])
        assertEquals("PROJ-1", result[0]["parentTicketKey"])
    }

    @Test
    fun `list returns empty list when no preparations`() {
        whenever(useCase.getAllPreparations()).thenReturn(emptyList())
        val result = controller.list()
        assertEquals(0, result.size)
    }

    @Test
    fun `get returns 404 when preparation not found`() {
        whenever(useCase.getPreparation(eq(99L))).thenReturn(null)
        val result = controller.get(99L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `get returns full DTO when preparation exists`() {
        val e = refinement(id = 5L, parent = "PROJ-5", status = "RUNNING")
        whenever(useCase.getPreparation(eq(5L))).thenReturn(e)
        whenever(useCase.normalizeReviewsJson(anyOrNull())).thenReturn("[]")
        val result = controller.get(5L)
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertEquals(5L, body["id"])
        assertEquals("PROJ-5", body["parentTicketKey"])
        assertEquals("RUNNING", body["status"])
        assertEquals("[]", body["reviewsJson"])
    }

    @Test
    fun `status returns 404 when preparation not found`() {
        whenever(useCase.getPreparation(eq(7L))).thenReturn(null)
        val result = controller.status(7L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `status returns lightweight payload when preparation exists`() {
        val e = refinement(id = 8L, status = "RUNNING")
        whenever(useCase.getPreparation(eq(8L))).thenReturn(e)
        val result = controller.status(8L)
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertEquals(8L, body["id"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `postComment returns 200 on success`() {
        val updated = refinement(id = 9L, status = "COMPLETED")
        whenever(useCase.postReviewAsComment(eq(9L), eq("PROJ-99"))).thenReturn(updated)
        whenever(useCase.normalizeReviewsJson(any())).thenReturn("[]")
        val result = controller.postComment(9L, "PROJ-99")
        assertEquals(200, result.statusCode.value())
        assertEquals(9L, result.body!!["id"])
    }

    @Test
    fun `postComment returns 400 on IllegalArgumentException`() {
        whenever(useCase.postReviewAsComment(any(), any())).thenThrow(IllegalArgumentException("bad"))
        val result = controller.postComment(1L, "PROJ-99")
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `postComment returns 400 on IllegalStateException`() {
        whenever(useCase.postReviewAsComment(any(), any())).thenThrow(IllegalStateException("bad state"))
        val result = controller.postComment(1L, "PROJ-99")
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `cancel returns 204`() {
        val result = controller.cancel(99L)
        assertEquals(204, result.statusCode.value())
        verify(useCase).cancelPreparation(99L)
    }

    @Test
    fun `delete returns 204`() {
        val result = controller.delete(99L)
        assertEquals(204, result.statusCode.value())
        verify(useCase).deletePreparation(99L)
    }

    @Test
    fun `deleteAll returns 204`() {
        val result = controller.deleteAll()
        assertEquals(204, result.statusCode.value())
        verify(useCase).deleteAllPreparations()
    }

    @Test
    fun `defaultTemplate returns template`() {
        whenever(useCase.defaultPromptTemplate()).thenReturn("DEFAULT")
        val result = controller.defaultTemplate()
        assertEquals(200, result.statusCode.value())
        assertEquals("DEFAULT", result.body!!["template"])
    }

    @Test
    fun `createSubtasks returns 200 with body on success`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "subtasks" to listOf(mapOf("summary" to "S1", "key" to "PROJ-2"))))

        val result =
            controller.createSubtasks(
                9L,
                "PROJ-1",
                mapOf(
                    "subtasks" to
                        listOf(
                            mapOf(
                                "summary" to "Sub A",
                                "description" to "Body A",
                                "issueType" to "Subtask",
                                "priority" to "Minor",
                                "labels" to listOf("backend"),
                            ),
                        ),
                ),
            )
        assertEquals(200, result.statusCode.value())
        assertEquals("PROJ-1", result.body!!["key"])

        verify(useCase).createSuggestedSubtasks(
            eq(9L),
            eq("PROJ-1"),
            org.mockito.kotlin.check { drafts ->
                assertEquals(1, drafts.size)
                assertEquals("Sub A", drafts[0].summary)
                assertEquals("Body A", drafts[0].description)
                assertEquals("Subtask", drafts[0].issueType)
                assertEquals("Minor", drafts[0].priority)
                assertEquals(listOf("backend"), drafts[0].labels)
            },
        )
    }

    @Test
    fun `createSubtasks accepts a raw JSON array as request body`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "subtasks" to emptyList<Any>()))

        val result =
            controller.createSubtasks(
                9L,
                "PROJ-1",
                listOf(mapOf("summary" to "Direct", "description" to "x")),
            )
        assertEquals(200, result.statusCode.value())

        verify(useCase).createSuggestedSubtasks(
            eq(9L),
            eq("PROJ-1"),
            org.mockito.kotlin.check { drafts ->
                assertEquals(1, drafts.size)
                assertEquals("Direct", drafts[0].summary)
            },
        )
    }

    @Test
    fun `createSubtasks returns 400 when body has no subtasks`() {
        val result = controller.createSubtasks(9L, "PROJ-1", mapOf("subtasks" to emptyList<Any>()))
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
        verify(useCase, org.mockito.kotlin.never())
            .createSuggestedSubtasks(any(), any(), any())
    }

    @Test
    fun `createSubtasks returns 400 when body is null`() {
        val result = controller.createSubtasks(9L, "PROJ-1", null)
        assertEquals(400, result.statusCode.value())
        verify(useCase, org.mockito.kotlin.never())
            .createSuggestedSubtasks(any(), any(), any())
    }

    @Test
    fun `createSubtasks filters entries with blank summary`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "subtasks" to emptyList<Any>()))

        controller.createSubtasks(
            9L,
            "PROJ-1",
            mapOf(
                "subtasks" to
                    listOf(
                        mapOf("summary" to "   ", "description" to "skip"),
                        mapOf("summary" to "Keep me", "description" to "ok"),
                    ),
            ),
        )

        verify(useCase).createSuggestedSubtasks(
            any(),
            any(),
            org.mockito.kotlin.check { drafts ->
                assertEquals(1, drafts.size)
                assertEquals("Keep me", drafts[0].summary)
            },
        )
    }

    @Test
    fun `createSubtasks returns 400 on IllegalArgumentException from use case`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenThrow(IllegalArgumentException("preparation not found"))
        val result =
            controller.createSubtasks(
                9L,
                "PROJ-1",
                listOf(mapOf("summary" to "S", "description" to "")),
            )
        assertEquals(400, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `createSubtasks returns 400 on IllegalStateException from use case`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenThrow(IllegalStateException("no jira config"))
        val result =
            controller.createSubtasks(
                9L,
                "PROJ-1",
                listOf(mapOf("summary" to "S", "description" to "")),
            )
        assertEquals(400, result.statusCode.value())
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `createSubtasks returns 500 on generic exception from use case`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenThrow(RuntimeException("boom"))
        val result =
            controller.createSubtasks(
                9L,
                "PROJ-1",
                listOf(mapOf("summary" to "S", "description" to "")),
            )
        assertEquals(500, result.statusCode.value())
        assertTrue(result.body!!.containsKey("error"))
    }

    @Test
    fun `createSubtasks falls back to defaults for blank issueType and priority`() {
        whenever(useCase.createSuggestedSubtasks(any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "subtasks" to emptyList<Any>()))

        controller.createSubtasks(
            9L,
            "PROJ-1",
            listOf(
                mapOf(
                    "summary" to "S",
                    "description" to "",
                    "issueType" to "",
                    "priority" to "  ",
                ),
            ),
        )

        verify(useCase).createSuggestedSubtasks(
            any(),
            any(),
            org.mockito.kotlin.check { drafts ->
                assertEquals("Subtask", drafts[0].issueType)
                assertEquals("Minor", drafts[0].priority)
            },
        )
    }
}
