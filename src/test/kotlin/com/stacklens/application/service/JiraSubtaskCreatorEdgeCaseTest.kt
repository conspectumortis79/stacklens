package com.stacklens.application.service

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.testsupport.NoDemoTicketCreation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class JiraSubtaskCreatorEdgeCaseTest {
    private val jiraPort: TicketCreationJiraPort = mock()
    private val creator = JiraSubtaskCreator(jiraPort, NoDemoTicketCreation)
    private val config = mock<JiraConfig>()

    @Test
    fun `a blank parent key skips the creation entirely`() {
        val result =
            creator.createAll(
                config,
                "   ",
                "PROJ",
                listOf(SubtaskDraft(summary = "child", description = "body")),
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a subtask failure is propagated as a typed exception`() {
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalStateException("jira down"))

        val thrown =
            assertThrows(SubtaskCreationException::class.java) {
                creator.createAll(
                    config,
                    "PROJ-1",
                    "PROJ",
                    listOf(SubtaskDraft(summary = "  ", description = "body")),
                )
            }

        assertEquals("Subtask 1", thrown.subtaskSummary)
        assertEquals("PROJ-1", thrown.parentKey)
        assertTrue(thrown.message!!.contains("jira down"))
    }

    @Test
    fun `a subtask failure without a message reports the placeholder`() {
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalStateException())

        val thrown =
            assertThrows(SubtaskCreationException::class.java) {
                creator.createAll(
                    config,
                    "PROJ-1",
                    "PROJ",
                    listOf(SubtaskDraft(summary = "  ", description = "body")),
                )
            }

        assertTrue(thrown.message!!.contains("unknown"))
    }

    @Test
    fun `subtask bodies are normalised before they reach Jira`() {
        val captor = argumentCaptor<String>()
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), captor.capture(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        JiraSubtaskCreator(jiraPort, NoDemoTicketCreation, languageProvider = { "de" }).createAll(
            config,
            "PROJ-1",
            "PROJ",
            listOf(
                SubtaskDraft(
                    summary = "Child",
                    description = "## User Story\n\nAls Team wollen wir X.\n\n## AC\n\n- criterion",
                ),
            ),
        )

        assertEquals(
            "Als Team wollen wir X.\n\n✅ AK:\n\n- criterion\n\n---",
            captor.firstValue,
        )
    }

    @Test
    fun `the acceptance criteria marker follows the resolved language`() {
        val captor = argumentCaptor<String>()
        whenever(jiraPort.createSubtask(any(), any(), any(), any(), captor.capture(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        JiraSubtaskCreator(jiraPort, NoDemoTicketCreation, languageProvider = { "en" }).createAll(
            config,
            "PROJ-1",
            "PROJ",
            listOf(SubtaskDraft(summary = "Child", description = "As a team we want X.\n\nAC\n\n- criterion")),
        )

        assertEquals("As a team we want X.\n\n✅ AC:\n\n- criterion\n\n---", captor.firstValue)
    }
}
