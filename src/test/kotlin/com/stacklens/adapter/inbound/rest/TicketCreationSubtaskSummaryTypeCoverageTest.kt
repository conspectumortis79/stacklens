package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketCreationSubtaskSummaryTypeCoverageTest {
    private val useCase: TicketCreationUseCase = mock()
    private val controller =
        TicketCreationController(
            useCase,
            staleAnalysis = StaleAnalysisStatusFixtures.withRealClock(),
            attachmentRead = mock(),
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
        )

    @Test
    fun `non textual subtask summaries are treated as blank`() {
        whenever(useCase.jiraConfigExists(1L)).thenReturn(true)
        whenever(useCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(mapOf("issueKey" to "PROJ-3"))

        val response =
            controller.createJira(
                mapOf(
                    "jiraConfigId" to 1L,
                    "projectKey" to "PROJ",
                    "summary" to "Parent",
                    "subtasks" to
                        listOf(
                            mapOf("summary" to 42),
                            mapOf("summary" to true),
                            mapOf("summary" to "real child"),
                        ),
                ),
            )

        assertEquals(200, response.statusCode.value())
    }
}
