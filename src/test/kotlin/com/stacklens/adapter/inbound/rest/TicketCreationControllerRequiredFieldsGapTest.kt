package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

class TicketCreationControllerRequiredFieldsGapTest {
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
    fun `requiredFields rejects a blank issue type even when the project key is present`() {
        val resp = controller.requiredFields(1L, "PROJ", " ")

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        verify(useCase, org.mockito.kotlin.never()).requiredJiraFields(any(), any(), any())
    }

    @Test
    fun `requiredFields mirrors the jira status when createmeta is rejected`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.requiredJiraFields(1L, "PROJ", "Bug"))
            .thenThrow(
                WebClientResponseException(
                    400,
                    "Bad Request",
                    HttpHeaders(),
                    "createmeta is disabled".toByteArray(),
                    Charsets.UTF_8,
                ),
            )

        val resp = controller.requiredFields(1L, " proj ", "Bug ")

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("Jira responded with HTTP 400: createmeta is disabled", body["error"])
    }

    @Test
    fun `createJira keeps only custom fields whose key and value are usable`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(
            useCase.createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(mapOf("key" to "PROJ-1"))

        controller.createJira(
            mapOf(
                "jiraConfigId" to 1,
                "projectKey" to "PROJ",
                "summary" to "S",
                "issueType" to "Bug",
                "customFields" to
                    mapOf<Any?, Any?>(
                        42 to "key is not a string",
                        "summary" to "not a custom field",
                        "customfield_1" to listOf(3, "  ", null),
                        "customfield_2" to emptyList<String>(),
                        "customfield_3" to listOf("ok", 7, mapOf("value" to 5), listOf("nested"), ""),
                        "customfield_4" to 42,
                        "customfield_5" to "   ",
                        "customfield_6" to mapOf("a" to "b"),
                    ),
            ),
        )

        val captor = argumentCaptor<Map<String, Any>>()
        verify(useCase)
            .createJiraTicket(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                any(),
                any(),
                captor.capture(),
            )
        assertEquals(mapOf("customfield_3" to listOf("ok")), captor.firstValue)
    }
}
