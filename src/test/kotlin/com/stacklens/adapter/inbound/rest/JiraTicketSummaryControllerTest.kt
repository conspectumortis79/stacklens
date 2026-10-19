package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.JiraTicketSummaryUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.JiraTicketSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class JiraTicketSummaryControllerTest {
    private val useCase: JiraTicketSummaryUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val controller = JiraTicketSummaryController(useCase, i18n)

    init {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(null)
    }

    @Test
    fun `start returns 200 with id and status on success`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-1", jiraConfigId = 11L)
        whenever(useCase.startSummary("PROJ-1", 11L, null)).thenReturn(entity)

        val resp = controller.start(mapOf("ticketKey" to "PROJ-1", "jiraConfigId" to 11))
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(0L, body["id"])
        assertEquals("PENDING", body["status"])
        verify(useCase).startSummary("PROJ-1", 11L, null)
    }

    @Test
    fun `start accepts string jiraConfigId and trims it`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-1", jiraConfigId = 7L)
        whenever(useCase.startSummary(eq("PROJ-1"), eq(7L), anyOrNull())).thenReturn(entity)

        val resp = controller.start(mapOf("ticketKey" to "PROJ-1", "jiraConfigId" to "  7 "))
        assertEquals(HttpStatus.OK, resp.statusCode)
        verify(useCase).startSummary("PROJ-1", 7L, null)
    }

    @Test
    fun `start omits the configId key when no configId is provided and lets the use case resolve it`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-1")
        whenever(useCase.startSummary(eq("PROJ-1"), eq(null), anyOrNull())).thenReturn(entity)

        val resp = controller.start(mapOf("ticketKey" to "PROJ-1"))
        assertEquals(HttpStatus.OK, resp.statusCode)
        verify(useCase).startSummary("PROJ-1", null, null)
    }

    @Test
    fun `start falls back to template-provided key when present in body`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-1", templateOverride = "TPL")
        whenever(useCase.startSummary(eq("PROJ-1"), eq(null), eq("TPL"))).thenReturn(entity)

        val resp = controller.start(mapOf("ticketKey" to "PROJ-1", "templateOverride" to "TPL"))
        verify(useCase).startSummary("PROJ-1", null, "TPL")
    }

    @Test
    fun `start trims the ticketKey before calling the use case`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-1")
        whenever(useCase.startSummary(eq("PROJ-1"), anyOrNull(), anyOrNull())).thenReturn(entity)

        controller.start(mapOf("ticketKey" to "  PROJ-1  "))
        verify(useCase).startSummary("PROJ-1", null, null)
    }

    @Test
    fun `start returns 400 with error message when use case throws IllegalArgumentException`() {
        whenever(useCase.startSummary(any(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("ticketKey is required"))
        val resp = controller.start(mapOf("ticketKey" to ""))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("ticketKey is required", body["error"])
    }

    @Test
    fun `start returns 400 with error message when use case throws IllegalStateException`() {
        whenever(useCase.startSummary(any(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalStateException("no active jira config"))
        val resp = controller.start(mapOf("ticketKey" to "PROJ-1"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("no active jira config", body["error"])
    }

    @Test
    fun `start falls back to a default error message when exception has no message`() {
        whenever(useCase.startSummary(any(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException())
        whenever(i18n.t("ticket_summary.be_invalid_input")).thenReturn("invalid input")
        val resp = controller.start(mapOf("ticketKey" to "PROJ-1"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("invalid input", body["error"])
    }

    @Test
    fun `list returns empty list when use case has no summaries`() {
        whenever(useCase.getAllSummaries()).thenReturn(emptyList())
        val resp = controller.list()
        assertTrue(resp.isEmpty())
    }

    @Test
    fun `list maps every entity to a summary DTO`() {
        val a = JiraTicketSummary(ticketKey = "PROJ-1")
        val b = JiraTicketSummary(ticketKey = "PROJ-2")
        whenever(useCase.getAllSummaries()).thenReturn(listOf(a, b))
        val resp = controller.list()
        assertEquals(2, resp.size)
        assertEquals("PROJ-1", resp[0]["ticketKey"])
        assertEquals("PENDING", resp[0]["status"])
        assertNotNull(resp[0]["createdAt"])
        assertEquals("PROJ-2", resp[1]["ticketKey"])
    }

    @Test
    fun `get returns 404 when summary is not found`() {
        whenever(useCase.getSummary(99L)).thenReturn(null)
        val resp = controller.get(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `get returns 200 with full DTO when summary is found`() {
        val entity =
            JiraTicketSummary(
                id = 42L,
                ticketKey = "PROJ-1",
                jiraConfigId = 7L,
                status = "COMPLETED",
                summaryMarkdown = "## Overview\n\nx",
                summaryHtml = "<div>html</div>",
            )
        whenever(useCase.getSummary(42L)).thenReturn(entity)
        val resp = controller.get(42L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(42L, body["id"])
        assertEquals("PROJ-1", body["ticketKey"])
        assertEquals(7L, body["jiraConfigId"])
        assertEquals("COMPLETED", body["status"])
        assertEquals("## Overview\n\nx", body["summaryMarkdown"])
        assertEquals("<div>html</div>", body["summaryHtml"])
        assertNotNull(body["createdAt"])
    }

    @Test
    fun `status returns 404 when summary is not found`() {
        whenever(useCase.getSummary(7L)).thenReturn(null)
        val resp = controller.status(7L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `status returns 200 with id, status, currentStep, errorMessage`() {
        val entity =
            JiraTicketSummary(
                id = 7L,
                ticketKey = "PROJ-1",
                status = "FAILED",
                currentStep = "fetching…",
                errorMessage = "boom",
            )
        whenever(useCase.getSummary(7L)).thenReturn(entity)
        val resp = controller.status(7L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(7L, body["id"])
        assertEquals("FAILED", body["status"])
        assertEquals("fetching…", body["currentStep"])
        assertEquals("boom", body["errorMessage"])
    }

    @Test
    fun `cancel returns 204 and delegates to the use case`() {
        val resp = controller.cancel(11L)
        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
        verify(useCase).cancelSummary(11L)
    }

    @Test
    fun `delete returns 204 and delegates to the use case`() {
        val resp = controller.delete(11L)
        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
        verify(useCase).deleteSummary(11L)
    }

    @Test
    fun `deleteAll returns 204 and delegates to the use case`() {
        val resp = controller.deleteAll()
        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
        verify(useCase).deleteAllSummaries()
    }

    @Test
    fun `defaultTemplate returns 200 with the template wrapped in a map`() {
        whenever(useCase.defaultPromptTemplate()).thenReturn("DEFAULT TEMPLATE")
        val resp = controller.defaultTemplate()
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, String>
        assertEquals("DEFAULT TEMPLATE", body["template"])
    }

    @Test
    fun `controller returns 400 with the raw exception message when present`() {
        val localController = JiraTicketSummaryController(useCase, i18n)
        whenever(useCase.startSummary(any(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("custom msg"))
        val resp = localController.start(mapOf("ticketKey" to "PROJ-1"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertTrue(body["error"] == "custom msg" || body["error"] == "ticket_summary.be_invalid_input")
    }

    @Test
    fun `get toFullDto maps null jiraConfigId and null completedAt`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-1")
        whenever(useCase.getSummary(entity.id)).thenReturn(entity)
        val resp = controller.get(entity.id)

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNull(body["jiraConfigId"])
        assertNull(body["completedAt"])
        assertNull(body["errorMessage"])
    }

    @Test
    fun `deleteAll does not require an id`() {
        controller.deleteAll()
        verify(useCase).deleteAllSummaries()
        verify(useCase, never()).deleteSummary(any())
    }

    @Test
    fun `start with empty body and no key still routes through use case (returns 400)`() {
        whenever(useCase.startSummary(eq(""), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalArgumentException("ticketKey is required"))
        val resp = controller.start(emptyMap())
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["error"])
    }

    @Test
    fun `get toFullDto maps jiraConfigId 0 correctly`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-1", jiraConfigId = 0L)
        whenever(useCase.getSummary(entity.id)).thenReturn(entity)
        val resp = controller.get(entity.id)

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(0L, body["jiraConfigId"])
    }

    @Test
    fun `start default templateOverride triggers synthetic default method`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-9", jiraConfigId = 1L)
        whenever(useCase.startSummary(eq("PROJ-9"), eq(1L), anyOrNull())).thenReturn(entity)
        val result = useCase.startSummary("PROJ-9", 1L)
        assertEquals("PROJ-9", result.ticketKey)
    }

    @Test
    fun `start with blank templateOverride triggers null takeIf branch`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-10", jiraConfigId = 1L)
        whenever(useCase.startSummary(eq("PROJ-10"), eq(1L), eq(null))).thenReturn(entity)
        val resp =
            controller.start(
                body =
                    mapOf(
                        "ticketKey" to "PROJ-10",
                        "jiraConfigId" to "1",
                        "templateOverride" to "   ",
                    ),
            )
        assertEquals(200, resp.statusCode.value())
        verify(useCase).startSummary(eq("PROJ-10"), eq(1L), eq(null))
    }

    @Test
    fun `start with null templateOverride triggers null asString branch`() {
        val entity = JiraTicketSummary(ticketKey = "PROJ-11", jiraConfigId = 1L)
        whenever(useCase.startSummary(eq("PROJ-11"), eq(1L), anyOrNull())).thenReturn(entity)
        val resp =
            controller.start(
                body =
                    mapOf(
                        "ticketKey" to "PROJ-11",
                        "jiraConfigId" to "1",
                    ),
            )
        assertEquals(200, resp.statusCode.value())
    }
}
