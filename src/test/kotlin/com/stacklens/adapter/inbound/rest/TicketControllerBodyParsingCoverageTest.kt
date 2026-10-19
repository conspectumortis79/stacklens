package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.application.port.inbound.TicketRefinementUseCase
import com.stacklens.application.port.inbound.ticket.TicketAttachmentReadUseCase
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketControllerBodyParsingCoverageTest {
    private val creationUseCase = mock<TicketCreationUseCase>()
    private val refinementUseCase = mock<TicketRefinementUseCase>()
    private val creation =
        TicketCreationController(
            creationUseCase,
            com.stacklens.application.service
                .FakeI18n(),
            StaleAnalysisStatusFixtures.withRealClock(),
            mock<TicketAttachmentReadUseCase>(),
        )
    private val refinement =
        TicketRefinementController(
            useCase = refinementUseCase,
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
        )

    @Test
    fun `create jira accepts a numeric config id and defaults every optional field`() {
        whenever(creationUseCase.jiraConfigExists(7L)).thenReturn(true)
        whenever(
            creationUseCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()),
        ).thenReturn(mapOf("issueKey" to "PROJ-9"))

        val response =
            creation.createJira(
                mapOf(
                    "jiraConfigId" to 7,
                    "projectKey" to " proj ",
                    "summary" to " Sum ",
                    "description" to "body",
                    "issueType" to "  ",
                    "priority" to "  ",
                    "labels" to listOf("a", 3, "b"),
                ),
            )

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `create jira reads a string config id and inherits the shared defaults for subtasks`() {
        whenever(creationUseCase.jiraConfigExists(8L)).thenReturn(true)
        whenever(
            creationUseCase.createJiraTicket(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any(), any()),
        ).thenReturn(mapOf("issueKey" to "PROJ-10"))

        val response =
            creation.createJira(
                mapOf(
                    "jiraConfigId" to " 8 ",
                    "projectKey" to "PROJ",
                    "summary" to "Sum",
                    "priority" to "Major",
                    "labels" to listOf("shared"),
                    "subtasks" to
                        listOf(
                            mapOf("summary" to "  ", "description" to "skipped"),
                            mapOf("summary" to " child ", "issueType" to "  ", "priority" to "  ", "labels" to listOf("own", 5)),
                            "not-a-map",
                        ),
                    "historyId" to "not-a-number",
                ),
            )

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `create jira rejects a missing, unknown or unparsable configuration reference`() {
        assertEquals(400, creation.createJira(mapOf("jiraConfigId" to "abc")).statusCode.value())
        assertEquals(400, creation.createJira(mapOf("projectKey" to "P")).statusCode.value())
        whenever(creationUseCase.jiraConfigExists(3L)).thenReturn(false)
        assertEquals(400, creation.createJira(mapOf("jiraConfigId" to 3)).statusCode.value())
    }

    @Test
    fun `create jira rejects a blank project key or summary`() {
        whenever(creationUseCase.jiraConfigExists(1L)).thenReturn(true)
        assertEquals(400, creation.createJira(mapOf("jiraConfigId" to 1, "projectKey" to "  ", "summary" to "s")).statusCode.value())
        assertEquals(400, creation.createJira(mapOf("jiraConfigId" to 1, "projectKey" to "P", "summary" to " ")).statusCode.value())
    }

    @Test
    fun `repo selections keep only local and gitlab entries with a parsable id`() {
        val parsed =
            refinement.parseRepoSelections(
                listOf(
                    mapOf("type" to " GITLAB ", "id" to 4, "projectId" to 9),
                    mapOf("type" to "local", "id" to "12"),
                    mapOf("type" to "  "),
                    mapOf("type" to "github", "id" to 1),
                    mapOf("type" to "local"),
                    mapOf("type" to "local", "id" to "x"),
                ),
            )

        assertEquals(2, parsed.size)
        assertEquals("gitlab", parsed[0].type)
        assertEquals(9L, parsed[0].projectId)
        assertEquals(12L, parsed[1].id)
        assertEquals(emptyList<Any>(), refinement.parseRepoSelections("nope"))
    }

    @Test
    fun `subtask drafts fall back to defaults and drop entries without a summary`() {
        val drafts =
            refinement.parseSubtaskDrafts(
                mapOf(
                    "subtasks" to
                        listOf(
                            mapOf(
                                "summary" to " child ",
                                "issueType" to "  ",
                                "priority" to "  ",
                                "labels" to listOf(" a ", "", 7),
                            ),
                            mapOf("summary" to "  "),
                            "junk",
                        ),
                ),
            )

        assertEquals(1, drafts.size)
        assertEquals("Subtask", drafts.single().issueType)
        assertEquals("Minor", drafts.single().priority)
        assertEquals(listOf("a"), drafts.single().labels)
        assertEquals(1, refinement.parseSubtaskDrafts(drafts.map { mapOf("summary" to it.summary) }).size)
        assertEquals(emptyList<Any>(), refinement.parseSubtaskDrafts(mapOf("other" to 1)))
        assertEquals(emptyList<Any>(), refinement.parseSubtaskDrafts(42))
    }

    @Test
    fun `database config ids accept lists, numbers and strings`() {
        assertEquals(listOf(1L, 2L), refinement.parseDatabaseConfigIds(listOf(1, 2), null))
        assertEquals(listOf(1L, 3L), refinement.parseDatabaseConfigIds(listOf(1, "x", 3), null))
        assertEquals(emptyList<Any>(), refinement.parseDatabaseConfigIds("5", null))
        assertEquals(listOf(6L), refinement.parseDatabaseConfigIds(null, 6))
        assertEquals(listOf(7L), refinement.parseDatabaseConfigIds(null, " 7 "))
        assertEquals(emptyList<Any>(), refinement.parseDatabaseConfigIds(null, "seven"))
        assertEquals(emptyList<Any>(), refinement.parseDatabaseConfigIds(emptyList<Any>(), null))
    }
}
