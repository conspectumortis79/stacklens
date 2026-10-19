package com.stacklens.application.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.StartupHealer
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.InlineAsyncExecutor
import com.stacklens.testsupport.testCollaborators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketRefinementSubtaskCreationTest {
    private val repo = mock<TicketRefinementPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val refinementJira = mock<TicketRefinementJiraPort>()
    private val creationJira = mock<TicketCreationJiraPort>()
    private val copilot = mock<CopilotPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private val i18n = mock<I18nPort>()
    private val localRepoConfigRepo = mock<LocalRepoConfigPort>()
    private val gitlabConfigRepo = mock<GitlabConfigPort>()
    private val localRepoScan = mock<LocalRepoScanPort>()
    private val gitlab = mock<GitlabPort>()
    private val dbConfigRepo = mock<DatabaseConfigPort>()
    private val dbScanPort = mock<DatabaseScanPort>()
    private val asyncExecutor = InlineAsyncExecutor()
    private val aiJsonRepairer = mock<com.stacklens.application.port.out.AiJsonRepairerPort>()
    private val aiJsonExtractor = mock<com.stacklens.application.port.out.AiResponseJsonExtractorPort>()
    private val codec: JsonCodecPort = JacksonJsonCodecAdapter(jacksonObjectMapper())

    private lateinit var service: TicketRefinementService

    private fun jiraConfig(id: Long = 7L) =
        JiraConfig(
            id = id,
            name = "Test Jira",
            baseUrl = "https://jira.example.com",
            username = "user",
            apiToken = "token",
            cloudMode = true,
        )

    @BeforeEach
    fun setUp() {
        val deps =
            TicketRefinementDeps(
                ticketRefinementRepo = repo,
                jiraConfigRepo = jiraConfigRepo,
                jira = refinementJira,
                copilot = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                promptLoader = promptLoader,
                jsonCodec = codec,
                i18n = i18n,
                dbConfigRepo = dbConfigRepo,
                dbSchemaPort = dbScanPort,
                asyncExecutor = asyncExecutor,
                startupHealer = StartupHealer(),
            )
        service =
            TicketRefinementService(
                deps,
                knowledgePublication = FakeKnowledgePublicationPort(),
                knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
                collaborators = deps.testCollaborators(localRepoConfigRepo, gitlabConfigRepo, localRepoScan, gitlab, aiJsonRepairer, aiJsonExtractor, creationJira),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        whenever(copilot.resolveLanguage()).thenReturn("en")
        whenever(i18n.t(any(), any<String>())).thenAnswer { it.getArgument(1, String::class.java) }
        whenever(repo.saveTicketRefinement(any())).thenAnswer { it.getArgument(0, TicketRefinement::class.java) }
    }

    private fun savedEntity(
        id: Long,
        jiraConfigId: Long? = 7L,
    ): TicketRefinement {
        val e = TicketRefinement(id = id, parentTicketKey = "PROJ-1", jiraConfigId = jiraConfigId)
        whenever(repo.findTicketRefinementById(id)).thenReturn(e)
        return e
    }

    @Test
    fun `createSuggestedSubtasks creates each draft under the parent via the port`() {
        val entity = savedEntity(42L)
        val config = jiraConfig()
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(config)
        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer {
                CreatedJiraTicket(
                    key = "PROJ-101",
                    id = "101",
                    url = "https://jira/browse/PROJ-101",
                )
            }

        val drafts =
            listOf(
                SubtaskDraft(summary = "Sub A", description = "Body A"),
                SubtaskDraft(summary = "Sub B", description = "Body B"),
            )
        val result = service.createSuggestedSubtasks(42L, "PROJ-1", drafts)

        verify(creationJira, org.mockito.kotlin.times(2))
            .createSubtask(
                eq(config),
                eq("PROJ-1"),
                eq("PROJ"),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
            )
        @Suppress("UNCHECKED_CAST")
        val subs = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subs.size)
        assertEquals("Sub A", subs[0]["summary"])
        assertEquals("PROJ-101", subs[0]["key"])
        assertEquals("Sub B", subs[1]["summary"])
        assertEquals("PROJ-101", subs[1]["key"])
        assertEquals("PROJ-1", result["key"])

        assertNotNull(entity.subtasksCreatedJson)
        assertTrue(entity.subtasksCreatedJson!!.contains("Sub A"))
        assertTrue(entity.subtasksCreatedJson!!.contains("Sub B"))
    }

    @Test
    fun `createSuggestedSubtasks derives projectKey from ticket key`() {
        val entity = savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())
        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "MY_PROJECT-2", id = "2", url = ""))

        service.createSuggestedSubtasks(
            42L,
            "MY_PROJECT-42",
            listOf(SubtaskDraft(summary = "Sub", description = "")),
        )

        verify(creationJira).createSubtask(
            any(),
            eq("MY_PROJECT-42"),
            eq("MY_PROJECT"),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `createSuggestedSubtasks ensures description ends with separator`() {
        savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())
        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createSuggestedSubtasks(
            42L,
            "PROJ-1",
            listOf(SubtaskDraft(summary = "Sub", description = "Body without separator")),
        )

        verify(creationJira).createSubtask(
            any(),
            any(),
            any(),
            any(),
            org.mockito.kotlin.check { desc ->
                assertTrue(
                    desc.trimEnd().endsWith("---"),
                    "Description should end with '---' separator but was:\n$desc",
                )
            },
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `createSuggestedSubtasks leaves description unchanged when it already ends with separator`() {
        savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())
        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createSuggestedSubtasks(
            42L,
            "PROJ-1",
            listOf(SubtaskDraft(summary = "Sub", description = "Body ending in ---")),
        )

        verify(creationJira).createSubtask(
            any(),
            any(),
            any(),
            any(),
            org.mockito.kotlin.check { desc ->

                val sepCount = desc.split("---").size - 1
                assertEquals(1, sepCount, "Expected exactly one '---' separator, got $sepCount in:\n$desc")
            },
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `createSuggestedSubtasks propagates the typed failure when a subtask fails`() {
        savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())

        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("transient"))

        assertThrows(SubtaskCreationException::class.java) {
            service.createSuggestedSubtasks(
                42L,
                "PROJ-1",
                listOf(
                    SubtaskDraft(summary = "Will fail", description = ""),
                    SubtaskDraft(summary = "Will succeed", description = ""),
                ),
            )
        }

        verify(creationJira, org.mockito.kotlin.times(1)).createSubtask(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `createSuggestedSubtasks substitutes fallback summary when draft summary is blank`() {
        savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())
        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createSuggestedSubtasks(
            42L,
            "PROJ-1",
            listOf(SubtaskDraft(summary = "   ", description = "Body")),
        )

        verify(creationJira).createSubtask(
            any(),
            any(),
            any(),
            eq("Subtask 1"),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `createSuggestedSubtasks returns empty list when drafts is empty without calling port`() {
        savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())

        val result = service.createSuggestedSubtasks(42L, "PROJ-1", emptyList())

        @Suppress("UNCHECKED_CAST")
        val subs = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(0, subs.size)
        verify(creationJira, never()).createSubtask(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createSuggestedSubtasks accumulates the created-subtasks ledger across calls`() {
        val entity = savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())
        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createSuggestedSubtasks(42L, "PROJ-1", listOf(SubtaskDraft(summary = "First", description = "")))

        service.createSuggestedSubtasks(
            42L,
            "PROJ-1",
            listOf(SubtaskDraft(summary = "Second", description = "")),
        )

        assertNotNull(entity.subtasksCreatedJson)
        val map = codec.decodeMap(entity.subtasksCreatedJson!!)

        @Suppress("UNCHECKED_CAST")
        val list = map["PROJ-1"] as List<String>

        assertTrue(list.contains("First"))
        assertTrue(list.contains("Second"))
    }

    @Test
    fun `createSuggestedSubtasks rejects unknown preparation`() {
        whenever(repo.findTicketRefinementById(999L)).thenReturn(null)

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.createSuggestedSubtasks(
                    999L,
                    "PROJ-1",
                    listOf(SubtaskDraft(summary = "S", description = "")),
                )
            }
        assertTrue(ex.message!!.contains("999"))
    }

    @Test
    fun `createSuggestedSubtasks rejects preparation without jira config`() {
        savedEntity(42L, jiraConfigId = null)

        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())

        assertThrows(IllegalStateException::class.java) {
            service.createSuggestedSubtasks(
                42L,
                "PROJ-1",
                listOf(SubtaskDraft(summary = "S", description = "")),
            )
        }
    }

    @Test
    fun `createSuggestedSubtasks rejects ticket key without project delimiter`() {
        savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.createSuggestedSubtasks(
                    42L,
                    "INVALID_KEY",
                    listOf(SubtaskDraft(summary = "S", description = "")),
                )
            }
        assertTrue(ex.message!!.contains("INVALID_KEY"))
    }

    @Test
    fun `createSuggestedSubtasks passes labels through to the port`() {
        savedEntity(42L)
        whenever(jiraConfigRepo.findJiraConfigById(7L)).thenReturn(jiraConfig())
        whenever(creationJira.createSubtask(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CreatedJiraTicket(key = "PROJ-2", id = "2", url = ""))

        service.createSuggestedSubtasks(
            42L,
            "PROJ-1",
            listOf(SubtaskDraft(summary = "Sub", description = "Body", labels = listOf("backend", "api"))),
        )

        verify(creationJira).createSubtask(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(listOf("backend", "api")),
            any(),
        )
    }
}
