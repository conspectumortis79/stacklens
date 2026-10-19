package com.stacklens.infrastructure.config.deps

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.ticket.TicketCreationDemoPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketCreationCollaboratorsConfigTest {
    private val jiraPort: TicketCreationJiraPort = mock()
    private val copilot: CopilotPort = mock()
    private val demoSimulation: TicketCreationDemoPort = mock()
    private val jiraConfig: JiraConfig = mock()

    @Test
    fun `the subtask creator resolves the language from the copilot port`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        val captor = argumentCaptor<String>()
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), captor.capture(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        val creator = TicketCreationCollaboratorsConfig().jiraSubtaskCreator(jiraPort, demoSimulation, copilot)
        creator.createAll(
            jiraConfig,
            "PROJ-1",
            "PROJ",
            listOf(
                SubtaskDraft(
                    summary = "Child",
                    description = "## User Story\n\nAls Team wollen wir X.\n\n## AC\n\n- criterion",
                ),
            ),
        )

        verify(copilot).resolveLanguage()
        assertEquals("Als Team wollen wir X.\n\n✅ AK:\n\n- criterion\n\n---", captor.firstValue)
    }
}
