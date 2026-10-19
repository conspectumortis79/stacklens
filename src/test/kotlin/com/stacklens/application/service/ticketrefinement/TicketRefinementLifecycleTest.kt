package com.stacklens.application.service.ticketrefinement

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.inbound.RepoSelection
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.TicketRefinement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class TicketRefinementLifecycleTest {
    @Mock private lateinit var repo: TicketRefinementPort

    @Mock private lateinit var jiraConfigRepo: JiraConfigPort

    @Mock private lateinit var i18n: I18nPort

    @Mock private lateinit var copilot: CopilotPort

    @Mock private lateinit var promptLoader: PromptReaderPort

    private val mapper = jacksonObjectMapper()
    private val jsonCodec: JsonCodecPort = JacksonJsonCodecAdapter(mapper)

    private val lifecycle by lazy {
        TicketRefinementLifecycle(
            ticketRefinementRepo = repo,
            jiraConfigRepo = jiraConfigRepo,
            jsonCodec = jsonCodec,
            i18n = i18n,
            copilot = copilot,
            promptLoader = promptLoader,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    @Test
    fun `persistNewPreparation creates pending entity with trimmed key`() {
        val saved = TicketRefinement(id = 1L, parentTicketKey = "PROJ-1", jiraConfigId = 5L)
        whenever(repo.saveTicketRefinement(any())).thenReturn(saved)

        val result =
            lifecycle.persistNewPreparation(
                parentTicketKey = "  PROJ-1  ",
                jiraConfigId = 5L,
                templateOverride = null,
                repoSelections = emptyList(),
                singleTicketOnly = true,
                dbConfigIds = emptyList(),
            )

        assertEquals(1L, result.id)
        assertEquals("PROJ-1", result.parentTicketKey)
        val captor = argumentCaptor<TicketRefinement>()
        verify(repo).saveTicketRefinement(captor.capture())
        assertEquals("PENDING", captor.firstValue.status)
        assertEquals(true, captor.firstValue.singleTicketOnly)
    }

    @Test
    fun `persistNewPreparation falls back to active jira config when id is null`() {
        val activeConfig = JiraConfig(id = 99L, name = "default", baseUrl = "u", apiToken = "t", active = true)
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(activeConfig)
        val saved = TicketRefinement(id = 2L, parentTicketKey = "PROJ-2", jiraConfigId = 99L)
        whenever(repo.saveTicketRefinement(any())).thenReturn(saved)

        val result =
            lifecycle.persistNewPreparation(
                parentTicketKey = "PROJ-2",
                jiraConfigId = null,
                templateOverride = null,
                repoSelections = emptyList(),
                singleTicketOnly = true,
                dbConfigIds = emptyList(),
            )
        assertEquals(99L, result.jiraConfigId)
    }

    @Test
    fun `persistNewPreparation rejects blank key`() {
        assertThrows(IllegalArgumentException::class.java) {
            lifecycle.persistNewPreparation(
                parentTicketKey = "   ",
                jiraConfigId = null,
                templateOverride = null,
                repoSelections = emptyList(),
                singleTicketOnly = true,
                dbConfigIds = emptyList(),
            )
        }
    }

    @Test
    fun `persistNewPreparation encodes repoSelections when non-empty`() {
        val captor = argumentCaptor<TicketRefinement>()
        whenever(repo.saveTicketRefinement(captor.capture())).thenAnswer { it.arguments[0] as TicketRefinement }

        val selections = listOf(RepoSelection(type = "local", id = 7L, projectId = null))

        lifecycle.persistNewPreparation(
            parentTicketKey = "PROJ-3",
            jiraConfigId = 1L,
            templateOverride = null,
            repoSelections = selections,
            singleTicketOnly = false,
            dbConfigIds = listOf(11L, 22L),
        )
        val saved = captor.firstValue
        assertNotNull(saved.repoSelectionsJson)
        assertTrue(saved.repoSelectionsJson!!.contains("\"type\":\"local\""))
        assertEquals("11,22", saved.databaseConfigIds)
    }

    @Test
    fun `persistNewPreparation leaves repoSelectionsJson null when empty`() {
        val captor = argumentCaptor<TicketRefinement>()
        whenever(repo.saveTicketRefinement(captor.capture())).thenAnswer { it.arguments[0] as TicketRefinement }

        lifecycle.persistNewPreparation(
            parentTicketKey = "PROJ-4",
            jiraConfigId = 1L,
            templateOverride = "   ",
            repoSelections = emptyList(),
            singleTicketOnly = true,
            dbConfigIds = emptyList(),
        )
        val saved = captor.firstValue
        assertNull(saved.repoSelectionsJson)
        assertNull(saved.databaseConfigIds)
        assertNull(saved.templateOverride)
    }

    @Test
    fun `getPreparation delegates to repo`() {
        val entity = TicketRefinement(id = 5L, parentTicketKey = "PROJ-1")
        whenever(repo.findTicketRefinementById(5L)).thenReturn(entity)
        assertEquals(entity, lifecycle.getPreparation(5L))
    }

    @Test
    fun `getAllPreparations delegates to repo`() {
        val list = listOf(TicketRefinement(id = 1L, parentTicketKey = "A"))
        whenever(repo.findRecentTicketRefinements(200)).thenReturn(list)
        assertEquals(list, lifecycle.getAllPreparations())
    }

    @Test
    fun `cancelPreparation noop when entity missing`() {
        whenever(repo.findTicketRefinementById(99L)).thenReturn(null)
        lifecycle.cancelPreparation(99L)
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `cancelPreparation marks running run as cancelled with i18n error message`() {
        val entity = TicketRefinement(id = 1L, parentTicketKey = "PROJ-1")
        entity.status = "RUNNING"
        whenever(repo.findTicketRefinementById(1L)).thenReturn(entity)
        whenever(i18n.t("ticket_refinement.be_cancelled")).thenReturn("Abgebrochen")

        lifecycle.cancelPreparation(1L)

        assertEquals("CANCELLED", entity.status)
        assertEquals("Abgebrochen", entity.errorMessage)
        assertNotNull(entity.completedAt)
        verify(repo).saveTicketRefinement(entity)
    }

    @Test
    fun `cancelPreparation marks pending run as cancelled`() {
        val entity = TicketRefinement(id = 1L, parentTicketKey = "PROJ-1")
        whenever(repo.findTicketRefinementById(1L)).thenReturn(entity)
        whenever(i18n.t("ticket_refinement.be_cancelled")).thenReturn("Abgebrochen")

        lifecycle.cancelPreparation(1L)

        assertEquals("CANCELLED", entity.status)
        verify(repo).saveTicketRefinement(entity)
    }

    @Test
    fun `cancelPreparation leaves completed runs untouched`() {
        val entity = TicketRefinement(id = 1L, parentTicketKey = "PROJ-1")
        entity.status = "COMPLETED"
        whenever(repo.findTicketRefinementById(1L)).thenReturn(entity)

        lifecycle.cancelPreparation(1L)

        assertEquals("COMPLETED", entity.status)
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `deletePreparation delegates`() {
        lifecycle.deletePreparation(7L)
        verify(repo).deleteTicketRefinementById(7L)
    }

    @Test
    fun `deleteAllPreparations delegates`() {
        lifecycle.deleteAllPreparations()
        verify(repo).deleteAllTicketRefinements()
    }

    @Test
    fun `firstActiveJiraConfigId returns id when active config exists`() {
        whenever(jiraConfigRepo.findFirstByActiveTrue())
            .thenReturn(JiraConfig(id = 42L, name = "p", baseUrl = "u", apiToken = "t", active = true))
        assertEquals(42L, lifecycle.firstActiveJiraConfigId())
    }

    @Test
    fun `firstActiveJiraConfigId returns null when no active config`() {
        whenever(jiraConfigRepo.findFirstByActiveTrue()).thenReturn(null)
        assertNull(lifecycle.firstActiveJiraConfigId())
    }

    @Test
    fun `jiraConfigExists delegates to port`() {
        whenever(jiraConfigRepo.jiraConfigExistsById(11L)).thenReturn(true)
        assertTrue(lifecycle.jiraConfigExists(11L))
    }

    @Test
    fun `defaultPromptTemplate uses prompt loader`() {
        whenever(copilot.resolveLanguage()).thenReturn("de")
        whenever(promptLoader.get(eq("de"), eq("ticketrefinement.improve_ticket"), eq(emptyMap()))).thenReturn("TPL")
        assertEquals("TPL", lifecycle.defaultPromptTemplate())
    }

    @Test
    fun `save delegates to repo and returns persisted entity`() {
        val e = TicketRefinement(id = 3L, parentTicketKey = "X")
        whenever(repo.saveTicketRefinement(e)).thenReturn(e)
        assertEquals(e, lifecycle.save(e))
    }

    @Test
    fun `markFailed noop when entity missing`() {
        whenever(repo.findTicketRefinementById(1L)).thenReturn(null)
        lifecycle.markFailed(1L, "boom")
        verify(repo, never()).saveTicketRefinement(any())
    }

    @Test
    fun `markFailed persists failure state with error message`() {
        val e = TicketRefinement(id = 1L, parentTicketKey = "X")
        whenever(repo.findTicketRefinementById(1L)).thenReturn(e)
        lifecycle.markFailed(1L, "Something exploded")
        assertEquals("FAILED", e.status)
        assertEquals("Something exploded", e.errorMessage)
        assertNotNull(e.completedAt)
        verify(repo).saveTicketRefinement(e)
    }

    @Test
    fun `markFailed swallows save exceptions silently`() {
        val e = TicketRefinement(id = 1L, parentTicketKey = "X")
        whenever(repo.findTicketRefinementById(1L)).thenReturn(e)
        whenever(repo.saveTicketRefinement(e)).thenThrow(RuntimeException("db is down"))
        lifecycle.markFailed(1L, "error")
        verify(repo).saveTicketRefinement(e)
    }

    @Test
    fun `currentState delegates to repo`() {
        val e = TicketRefinement(id = 4L, parentTicketKey = "Q")
        whenever(repo.findTicketRefinementById(4L)).thenReturn(e)
        assertEquals(e, lifecycle.currentState(4L))
    }
}
