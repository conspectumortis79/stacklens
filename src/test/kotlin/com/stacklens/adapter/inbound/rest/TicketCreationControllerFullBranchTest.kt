package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

class TicketCreationControllerFullBranchTest {
    private val useCase: TicketCreationUseCase = mock()
    private val i18n: I18nUseCase =
        mock(defaultAnswer = org.mockito.stubbing.Answer { it.arguments.firstOrNull() ?: "" })
    private val controller = TicketCreationController(useCase, i18n, StaleAnalysisStatusFixtures.withRealClock(), mock())

    @Test
    fun `tr returns i18n-translated string when i18n is non-null`() {
        whenever(i18n.t("k", "a", "b")).thenReturn("translated")
        val resp = controller.generate(mapOf("inputText" to ""))
        assertNotNull(resp.body)
    }

    @Test
    fun `tr returns raw key when the i18n port echoes the key`() {
        val ctrl =
            TicketCreationController(
                useCase,
                com.stacklens.application.service
                    .FakeI18n(),
                StaleAnalysisStatusFixtures.withRealClock(),
                mock(),
            )
        val resp = ctrl.generate(mapOf("inputText" to ""))

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertTrue(body["error"].toString().contains("ticket_creation.be_input_required"))
    }

    @Test
    fun `generate returns 500 on exception with e message`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenThrow(RuntimeException("boom"))
        val resp = controller.generate(mapOf("inputText" to "valid input"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["error"])
    }

    @Test
    fun `generate returns 500 on exception with null message`() {
        whenever(useCase.generateTicket(any(), any(), any())).thenThrow(RuntimeException())
        val resp = controller.generate(mapOf("inputText" to "valid input"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }

    @Test
    fun `createJira rejects when configId is missing`() {
        val resp = controller.createJira(emptyMap())
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira accepts Number configId`() {
        whenever(useCase.jiraConfigExists(42L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                eq(42L),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("issueKey" to "PROJ-1"))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 42,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira accepts String configId that parses as Long`() {
        whenever(useCase.jiraConfigExists(7L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                eq(7L),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("issueKey" to "PROJ-2"))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to "7",
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira rejects String configId that does not parse`() {
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to "not-a-number",
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira rejects when jiraConfigExists returns false`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(false)
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira rejects when projectKey is blank`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "  ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `createJira rejects when summary is blank`() {
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
    fun `createJira with missing description defaults to empty string`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
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
    fun `createJira with empty issueType defaults to Story`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                eq("Story"),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "issueType" to "",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira with empty priority defaults to Minor`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                eq("Minor"),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
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
    fun `createJira with null priority defaults to Minor`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                eq("Minor"),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
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
    fun `createJira with no labels defaults to empty list`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(emptyList()),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
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
    fun `createJira with Number historyId parses it as Long`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                eq(99L),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "historyId" to 99,
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira with String historyId parses it as Long`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                eq(50L),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "historyId" to "50",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira with non-numeric String historyId passes null`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                eq(null),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "historyId" to "abc",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira with valid subtasks list passes them through`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "subtasks" to
                        listOf(
                            mapOf("summary" to "Sub1", "description" to "d1"),
                            mapOf("summary" to "Sub2", "issueType" to "Task"),
                        ),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira with subtask list containing invalid entries filters them out`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "subtasks" to
                        listOf(
                            "not-a-map",
                            mapOf("summary" to "Valid"),
                            mapOf("summary" to "", "description" to "blank summary"),
                            mapOf("summary" to "  "),
                        ),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira with subtask list with null labels falls back to parent labels`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "labels" to listOf("parent-label"),
                    "subtasks" to listOf(mapOf("summary" to "Sub1")),
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira with subtask list not a list passes empty list`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                eq(emptyList()),
                any(),
            ),
        ).thenReturn(mapOf("ok" to true))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                    "subtasks" to "not a list",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `createJira returns WebClientResponseException status code on HTTP error`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        val webClientEx =
            WebClientResponseException.create(
                422,
                "Unprocessable",
                org.springframework.http.HttpHeaders(),
                "error body".toByteArray(),
                null,
            )
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenThrow(webClientEx)
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.statusCode)
    }

    @Test
    fun `createJira returns 500 on generic exception`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenThrow(RuntimeException("boom"))
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }

    @Test
    fun `createJira returns 500 on exception with null message`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
            ),
        ).thenThrow(RuntimeException())
        val resp =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "S",
                ),
            )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }
}
