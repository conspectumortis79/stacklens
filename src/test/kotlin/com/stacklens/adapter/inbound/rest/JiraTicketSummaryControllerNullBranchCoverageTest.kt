package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.JiraTicketSummaryUseCase
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class JiraTicketSummaryControllerNullBranchCoverageTest {
    private val useCase: JiraTicketSummaryUseCase = mock()
    private val controllerWithKeyI18n =
        JiraTicketSummaryController(
            useCase,
            com.stacklens.application.service
                .FakeI18n(),
        )
    private val controllerWithI18n = JiraTicketSummaryController(useCase, FakeI18n())

    private fun summary(): JiraTicketSummary =
        JiraTicketSummary(
            id = 3L,
            ticketKey = "PROJ-1",
            status = "DONE",
            createdAt = LocalDateTime.of(2025, 6, 7, 8, 9),
            completedAt = null,
        )

    @Test
    fun `start answers the raw key when the exception carries no message`() {
        whenever(useCase.startSummary(any(), anyOrNull(), anyOrNull())).thenThrow(IllegalArgumentException())

        val resp = controllerWithKeyI18n.start(mapOf("ticketKey" to "PROJ-1"))

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        assertEquals("ticket_summary.be_invalid_input", resp.body?.get("error"))
    }

    @Test
    fun `start translates the fallback message through i18n when it is available`() {
        whenever(useCase.startSummary(any(), anyOrNull(), anyOrNull())).thenThrow(IllegalStateException())

        val resp = controllerWithI18n.start(mapOf("ticketKey" to "PROJ-1"))

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        assertEquals("i18n:ticket_summary.be_invalid_input", resp.body?.get("error"))
    }

    @Test
    fun `list and get render a running summary without a completion timestamp`() {
        whenever(useCase.getAllSummaries()).thenReturn(listOf(summary()))
        whenever(useCase.getSummary(eq(3L))).thenReturn(summary())

        val row = controllerWithI18n.list().first()
        assertNull(row["completedAt"])
        assertEquals("2025-06-07T08:09", row["createdAt"])

        val detail =
            controllerWithKeyI18n
                .get(3L)
                .body
        assertEquals(HttpStatus.OK, controllerWithKeyI18n.get(3L).statusCode)
        assertNull(detail?.get("completedAt"))
    }

    @Test
    fun `start resolves the config id from the active jira configuration when the body has none`() {
        whenever(useCase.firstActiveJiraConfigId()).thenReturn(42L)
        whenever(useCase.startSummary(any(), anyOrNull(), anyOrNull())).thenReturn(summary())

        val resp = controllerWithI18n.start(mapOf("ticketKey" to " PROJ-2 ", "templateOverride" to "  "))

        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(3L, resp.body?.get("id"))
        assertEquals("DONE", resp.body?.get("status"))
    }
}
