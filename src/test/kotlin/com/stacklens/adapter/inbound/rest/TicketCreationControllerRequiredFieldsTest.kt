package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.domain.model.JiraCreateField
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class TicketCreationControllerRequiredFieldsTest {
    private val useCase: TicketCreationUseCase = mock()
    private val controller =
        TicketCreationController(
            useCase,
            com.stacklens.application.service
                .FakeI18n(),
            StaleAnalysisStatusFixtures.withRealClock(),
            mock(),
        )

    @Test
    fun `requiredFields returns the use-case fields for an uppercased project key`() {
        val fields = listOf(JiraCreateField("customfield_10120", "Department", multiple = false, allowedValues = listOf("Tax")))
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.requiredJiraFields(1L, "PROJ", "Bug")).thenReturn(fields)

        val resp = controller.requiredFields(1L, " proj ", "Bug")

        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(fields, resp.body)
    }

    @Test
    fun `requiredFields rejects a blank project key without calling Jira`() {
        val resp = controller.requiredFields(1L, " ", "Bug")

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        verify(useCase, never()).requiredJiraFields(any(), any(), any())
    }

    @Test
    fun `requiredFields rejects an unknown Jira configuration`() {
        whenever(useCase.jiraConfigExists(9L)).thenReturn(false)

        assertEquals(HttpStatus.BAD_REQUEST, controller.requiredFields(9L, "PROJ", "Bug").statusCode)
    }

    @Test
    fun `createJira forwards only well-formed custom fields with non-blank values`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("key" to "PROJ-1"))

        controller.createJira(
            mapOf(
                "jiraConfigId" to 1,
                "projectKey" to "PROJ",
                "summary" to "S",
                "issueType" to "Bug",
                "customFields" to
                    mapOf(
                        "customfield_10108" to listOf("Live", " ", 3),
                        "customfield_10120" to " Tax ",
                        "customfield_1" to "",
                        "summary" to "overridden",
                        "project" to mapOf("key" to "OTHER"),
                    ),
            ),
        )

        val captor = argumentCaptor<Map<String, Any>>()
        verify(useCase).createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), captor.capture())
        assertEquals(mapOf("customfield_10108" to listOf("Live"), "customfield_10120" to "Tax"), captor.firstValue)
    }
}
