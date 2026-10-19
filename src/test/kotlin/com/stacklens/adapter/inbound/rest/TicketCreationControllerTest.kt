package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

class TicketCreationControllerTest {
    private val useCase: TicketCreationUseCase = mock()
    private val i18n: I18nUseCase =
        mock(defaultAnswer = org.mockito.stubbing.Answer { it.arguments.firstOrNull() ?: "" })
    private val controller = TicketCreationController(useCase, i18n, StaleAnalysisStatusFixtures.withRealClock(), mock())

    @Test
    fun `generate returns 400 when inputText is missing`() {
        val resp = controller.generate(emptyMap())
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `generate returns 400 when inputText is blank`() {
        val resp = controller.generate(mapOf("inputText" to "   "))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `generate 400 body contains error key`() {
        whenever(i18n.t("ticket_creation.be_input_required", "Input text is required.")).thenReturn("Input required.")
        val resp = controller.generate(mapOf("inputText" to ""))

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["error"])
    }

    @Test
    fun `generate returns 200 with use-case result on valid input`() {
        val expected = mapOf<String, Any?>("summary" to "Fix bug", "issueType" to "Bug")
        whenever(useCase.generateTicket(eq("Fix bug"), any(), any())).thenReturn(expected)

        val resp = controller.generate(mapOf("inputText" to "Fix bug"))
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(expected, resp.body)
    }

    @Test
    fun `generate passes attachmentSection to use case`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenReturn(emptyMap())
        controller.generate(mapOf("inputText" to "Some input", "attachmentSection" to "## Files"))

        verify(useCase).generateTicket(
            "Some input",
            "## Files",
            com.stacklens.domain.model.IssueType.DEFAULT,
        )
    }

    @Test
    fun `generate passes empty attachmentSection when not provided`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenReturn(emptyMap())
        controller.generate(mapOf("inputText" to "Some input"))

        verify(useCase).generateTicket(
            "Some input",
            "",
            com.stacklens.domain.model.IssueType.DEFAULT,
        )
    }

    @Test
    fun `generate resolves the user-selected issueType to the matching enum`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenReturn(emptyMap())
        controller.generate(mapOf("inputText" to "Some input", "issueType" to "Technical Task"))

        org.mockito.kotlin.verify(useCase).generateTicket(
            org.mockito.kotlin.eq("Some input"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(com.stacklens.domain.model.IssueType.TECHNICAL_TASK),
        )
    }

    @Test
    fun `generate falls back to the default issueType when none is supplied`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenReturn(emptyMap())
        controller.generate(mapOf("inputText" to "Some input"))

        org.mockito.kotlin.verify(useCase).generateTicket(
            org.mockito.kotlin.eq("Some input"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(com.stacklens.domain.model.IssueType.DEFAULT),
        )
    }

    @Test
    fun `generate falls back to the default issueType for unknown values`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenReturn(emptyMap())
        controller.generate(mapOf("inputText" to "Some input", "issueType" to "Epic"))

        org.mockito.kotlin.verify(useCase).generateTicket(
            org.mockito.kotlin.eq("Some input"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(com.stacklens.domain.model.IssueType.DEFAULT),
        )
    }

    @Test
    fun `generate falls back to the default issueType for blank values`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenReturn(emptyMap())
        controller.generate(mapOf("inputText" to "Some input", "issueType" to "   "))

        org.mockito.kotlin.verify(useCase).generateTicket(
            org.mockito.kotlin.eq("Some input"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(com.stacklens.domain.model.IssueType.DEFAULT),
        )
    }

    @Test
    fun `generate returns 500 when use case throws`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenThrow(RuntimeException("AI failed"))

        val resp = controller.generate(mapOf("inputText" to "input"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }

    @Test
    fun `generate 500 body contains error key`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenThrow(RuntimeException("AI error"))

        val resp = controller.generate(mapOf("inputText" to "input"))

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["error"])
    }

    @Test
    fun `createJira returns 400 when jiraConfigId is null`() {
        val resp = controller.createJira(emptyMap())
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira returns 400 when jiraConfigId is a non-numeric string`() {
        val resp = controller.createJira(mapOf("jiraConfigId" to "not-a-number"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira accepts numeric Long jiraConfigId`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to "https://jira.example.com/browse/PROJ-1"))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "My summary",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira accepts numeric String jiraConfigId`() {
        whenever(useCase.jiraConfigExists(2L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-2", "id" to "10002", "url" to ""))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to "2",
                    "projectKey" to "PROJ",
                    "summary" to "Summary",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira returns 400 when config does not exist`() {
        whenever(useCase.jiraConfigExists(99L)).thenReturn(false)

        val resp = controller.createJira(mapOf("jiraConfigId" to 99L))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira accepts blank issueType and falls back to Story`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "issueType" to "   ",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira accepts blank priority and falls back to Minor`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "priority" to "",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira accepts non-string projectKey and returns 400`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to 42,
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira accepts blank summary and returns 400`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "   ",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira treats missing summary as empty via the null branch`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira treats missing labels as empty via the null branch`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira accepts non-list labels and falls back to empty list`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "labels" to "not-a-list",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira returns 400 when projectKey is blank`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "  ",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira returns 400 when summary is blank`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira returns 200 with key id and url on success`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-99", "id" to "10099", "url" to "https://jira.example.com/browse/PROJ-99"))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Deploy feature",
                    "description" to "Some desc",
                    "issueType" to "Story",
                    "labels" to listOf("backend"),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("PROJ-99", body["key"])
        assertEquals("10099", body["id"])
        assertEquals("https://jira.example.com/browse/PROJ-99", body["url"])
    }

    @Test
    fun `createJira uses default issueType Story when not provided`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), eq("Story"), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Default type ticket",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira forwards WebClientResponseException status`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        val ex =
            WebClientResponseException.create(
                422,
                "Unprocessable Entity",
                org.springframework.http.HttpHeaders.EMPTY,
                "field error".toByteArray(),
                null,
            )
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any())).thenThrow(ex)

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Bad ticket",
                ),
            )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.statusCode)
    }

    @Test
    fun `createJira returns 500 on generic exception`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenThrow(RuntimeException("Unexpected error"))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Failing ticket",
                ),
            )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }

    @Test
    fun `createJira 500 body contains error key`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenThrow(RuntimeException("boom"))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Failing ticket",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["error"])
    }

    @Test
    fun `createJira normalises projectKey to uppercase`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), eq("MYPROJECT"), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("key" to "MYPROJECT-1", "id" to "1", "url" to ""))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "myproject",
                    "summary" to "Uppercase test",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira passes historyId as Long to use case`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), eq(42L), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "1", "url" to ""))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Ticket with history",
                    "historyId" to 42L,
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        verify(useCase).createJiraTicket(any(), any(), any(), any(), any(), any(), eq(42L), any(), any(), any())
    }

    @Test
    fun `createJira parses historyId from string`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), eq(7L), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-7", "id" to "7", "url" to ""))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Ticket",
                    "historyId" to "7",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira passes null historyId when not provided`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), org.mockito.kotlin.anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "1", "url" to ""))

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Ticket no history",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `history returns list of record maps`() {
        val record =
            com.stacklens.domain.model
                .TicketCreationRecord(id = 1L, summary = "A ticket", issueType = "Story")
        whenever(useCase.getAllRecords()).thenReturn(listOf(record))

        val list = controller.history()

        assertEquals(1, list.size)
        assertEquals(1L, list[0]["id"])
        assertEquals("A ticket", list[0]["summary"])
    }

    @Test
    fun `history returns empty list when no records exist`() {
        whenever(useCase.getAllRecords()).thenReturn(emptyList())

        val list = controller.history()

        assertEquals(0, list.size)
    }

    @Test
    fun `historyItem returns 200 with record map when record exists`() {
        val record =
            com.stacklens.domain.model
                .TicketCreationRecord(id = 5L, summary = "My ticket", issueType = "Bug")
        whenever(useCase.getRecord(5L)).thenReturn(record)

        val resp = controller.historyItem(5L)

        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(5L, resp.body!!["id"])
        assertEquals("My ticket", resp.body!!["summary"])
    }

    @Test
    fun `historyItem returns 404 when record does not exist`() {
        whenever(useCase.getRecord(999L)).thenReturn(null)

        val resp = controller.historyItem(999L)

        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `deleteHistoryItem returns 204 and delegates to use case`() {
        val resp = controller.deleteHistoryItem(3L)

        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
        verify(useCase).deleteRecord(3L)
    }

    @Test
    fun `deleteAllHistory returns 204 and delegates to use case`() {
        val resp = controller.deleteAllHistory()

        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
        verify(useCase).deleteAllRecords()
    }

    @Test
    fun `cancelHistoryItem returns 200 with CANCELLED status when use case returns true`() {
        whenever(useCase.cancelRecord(5L)).thenReturn(true)

        val resp = controller.cancelHistoryItem(5L)

        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals("CANCELLED", resp.body!!["status"])
    }

    @Test
    fun `cancelHistoryItem returns 200 with record map when use case returns false and record exists`() {
        val record =
            com.stacklens.domain.model
                .TicketCreationRecord(id = 7L, status = "COMPLETED")
        whenever(useCase.cancelRecord(7L)).thenReturn(false)
        whenever(useCase.getRecord(7L)).thenReturn(record)

        val resp = controller.cancelHistoryItem(7L)

        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(7L, resp.body!!["id"])
    }

    @Test
    fun `cancelHistoryItem returns 404 when use case returns false and record not found`() {
        whenever(useCase.cancelRecord(99L)).thenReturn(false)
        whenever(useCase.getRecord(99L)).thenReturn(null)

        val resp = controller.cancelHistoryItem(99L)

        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `history marks stale RUNNING record as FAILED`() {
        val staleRunning =
            com.stacklens.domain.model
                .TicketCreationRecord(
                    id = 1L,
                    status = "RUNNING",
                    createdAt =
                        java.time.LocalDateTime
                            .now()
                            .minusMinutes(20),
                )
        whenever(useCase.getAllRecords()).thenReturn(listOf(staleRunning))

        val list = controller.history()

        assertEquals("FAILED", list[0]["status"])
    }

    @Test
    fun `history leaves recent RUNNING record as RUNNING`() {
        val recentRunning =
            com.stacklens.domain.model
                .TicketCreationRecord(
                    id = 2L,
                    status = "RUNNING",
                    createdAt =
                        java.time.LocalDateTime
                            .now()
                            .minusMinutes(1),
                )
        whenever(useCase.getAllRecords()).thenReturn(listOf(recentRunning))

        val list = controller.history()

        assertEquals("RUNNING", list[0]["status"])
    }

    @Test
    fun `history leaves CANCELLED record unchanged`() {
        val cancelled =
            com.stacklens.domain.model
                .TicketCreationRecord(
                    id = 3L,
                    status = "CANCELLED",
                    createdAt =
                        java.time.LocalDateTime
                            .now()
                            .minusMinutes(30),
                )
        whenever(useCase.getAllRecords()).thenReturn(listOf(cancelled))

        val list = controller.history()

        assertEquals("CANCELLED", list[0]["status"])
    }

    @Test
    fun `createJira passes subtasks list to use case when provided`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(
                mapOf(
                    "key" to "PROJ-1",
                    "id" to "10001",
                    "url" to "https://jira/browse/PROJ-1",
                    "subtasks" to listOf(mapOf("summary" to "S1", "key" to "PROJ-2")),
                ),
            )

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Parent",
                    "subtasks" to
                        listOf(
                            mapOf(
                                "summary" to "First subtask",
                                "description" to "First body",
                                "issueType" to "Subtask",
                                "priority" to "Minor",
                                "labels" to listOf("a"),
                            ),
                            mapOf(
                                "summary" to "Second subtask",
                                "description" to "Second body",
                                "issueType" to "Subtask",
                                "priority" to "Major",
                                "labels" to listOf("b"),
                            ),
                        ),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)

        org.mockito.kotlin.verify(useCase).createJiraTicket(
            org.mockito.kotlin.eq(1L),
            org.mockito.kotlin.eq("PROJ"),
            org.mockito.kotlin.eq("Parent"),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.anyOrNull(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.check { subtasks ->
                assertEquals(2, subtasks.size)
                assertEquals("First subtask", subtasks[0].summary)
                assertEquals("First body", subtasks[0].description)
                assertEquals("Minor", subtasks[0].priority)
                assertEquals(listOf("a"), subtasks[0].labels)
                assertEquals("Second subtask", subtasks[1].summary)
                assertEquals("Major", subtasks[1].priority)
            },
            any(),
        )
    }

    @Test
    fun `createJira filters subtasks with blank summary`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "1", "url" to "", "subtasks" to emptyList<Any>()))

        controller.createJira(
            mapOf(
                "jiraConfigId" to 1L,
                "projectKey" to "PROJ",
                "summary" to "Parent",
                "subtasks" to
                    listOf(
                        mapOf("summary" to "   ", "description" to "skip me"),
                        mapOf("summary" to "Keep me", "description" to "ok"),
                    ),
            ),
        )

        org.mockito.kotlin.verify(useCase).createJiraTicket(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            org.mockito.kotlin.check { subtasks ->
                assertEquals(1, subtasks.size)
                assertEquals("Keep me", subtasks[0].summary)
            },
            any(),
        )
    }

    @Test
    fun `createJira passes empty subtasks list when not provided`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "1", "url" to "", "subtasks" to emptyList<Any>()))

        controller.createJira(
            mapOf(
                "jiraConfigId" to 1L,
                "projectKey" to "PROJ",
                "summary" to "No subtasks",
            ),
        )

        org.mockito.kotlin.verify(useCase).createJiraTicket(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            org.mockito.kotlin.check { subtasks -> assertEquals(0, subtasks.size) },
            any(),
        )
    }

    @Test
    fun `createJira defaults subtask issueType to Subtask when missing`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "1", "url" to "", "subtasks" to emptyList<Any>()))

        controller.createJira(
            mapOf(
                "jiraConfigId" to 1L,
                "projectKey" to "PROJ",
                "summary" to "Parent",
                "subtasks" to
                    listOf(
                        mapOf("summary" to "S", "description" to "d"),
                    ),
            ),
        )

        org.mockito.kotlin.verify(useCase).createJiraTicket(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            org.mockito.kotlin.check { subtasks ->
                assertEquals("Subtask", subtasks[0].issueType)
            },
            any(),
        )
    }

    @Test
    fun `createJira returns subtasks list from use case in response body`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(
                mapOf(
                    "key" to "PROJ-1",
                    "id" to "1",
                    "url" to "https://jira/browse/PROJ-1",
                    "subtasks" to listOf(mapOf("summary" to "S1", "key" to "PROJ-2", "url" to "https://jira/browse/PROJ-2")),
                ),
            )

        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Parent",
                    "subtasks" to listOf(mapOf("summary" to "S1", "description" to "d")),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val subs = body["subtasks"] as List<Map<String, Any?>>
        assertEquals(1, subs.size)
        assertEquals("S1", subs[0]["summary"])
        assertEquals("PROJ-2", subs[0]["key"])
    }
}

class TicketCreationControllerTrBranchTest {
    private val useCase: TicketCreationUseCase = mock()
    private val i18n: I18nUseCase =
        mock(defaultAnswer = org.mockito.stubbing.Answer { it.arguments.firstOrNull() ?: "" })
    private val controller = TicketCreationController(useCase, i18n, StaleAnalysisStatusFixtures.withRealClock(), mock())

    @Test
    fun `tr delegates to i18n t when i18n is wired`() {
        val i18nImpl = mock<I18nUseCase>()
        org.mockito.kotlin
            .whenever(i18nImpl.t(org.mockito.kotlin.eq("hello")))
            .thenReturn("HELLO")
        val field = TicketCreationController::class.java.getDeclaredField("i18n")
        field.isAccessible = true
        field.set(controller, i18nImpl)
        val tr =
            TicketCreationController::class.java.getDeclaredMethod(
                "tr",
                String::class.java,
                Array<Any>::class.java,
            )
        tr.isAccessible = true
        val result =
            tr.invoke(controller, "hello", arrayOf<Any>()) as String
        assertEquals("HELLO", result)
    }
}

class TicketCreationControllerExtraBranchTest {
    private val useCase: com.stacklens.application.service.TicketCreationService = mock()
    private val i18n: com.stacklens.application.port.inbound.platform.I18nUseCase = mock()
    private val controller =
        TicketCreationController(useCase, i18n, StaleAnalysisStatusFixtures.withRealClock(), mock())

    @Test
    fun `createJira coerces non-string summary to empty string`() {
        whenever(i18n.t("ticket_creation.be_summary_required", "Summary is required."))
            .thenReturn("Summary is required.")
        val resp =
            controller.createJira(
                mapOf<String, Any?>(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to 42,
                ),
            )
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira uses Minor as default priority when not provided`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), eq("Minor"), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Default priority ticket",
                ),
            )
        assertEquals(org.springframework.http.HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira uses Minor as default priority when value is not a String`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), eq("Minor"), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))
        val resp =
            controller.createJira(
                mapOf<String, Any?>(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Default priority",
                    "priority" to 42L,
                ),
            )
        assertEquals(org.springframework.http.HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira uses Story as default issueType when value is not a String`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), eq("Story"), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1", "id" to "10001", "url" to ""))
        val resp =
            controller.createJira(
                mapOf<String, Any?>(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Default type",
                    "issueType" to 99L,
                ),
            )
        assertEquals(org.springframework.http.HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `tr uses i18n translate when i18n is wired`() {
        val i18n = org.mockito.kotlin.mock<com.stacklens.application.port.inbound.platform.I18nUseCase>()
        org.mockito.kotlin
            .whenever(i18n.t(org.mockito.kotlin.eq("hello")))
            .thenReturn("HELLO")
        val field = TicketCreationController::class.java.getDeclaredField("i18n")
        field.isAccessible = true
        field.set(controller, i18n)
        val tr =
            TicketCreationController::class.java.getDeclaredMethod(
                "tr",
                String::class.java,
                Array<Any?>::class.java,
            )
        tr.isAccessible = true
        val result = tr.invoke(controller, "hello", emptyArray<Any?>()) as String
        assertEquals("HELLO", result)
    }
}
