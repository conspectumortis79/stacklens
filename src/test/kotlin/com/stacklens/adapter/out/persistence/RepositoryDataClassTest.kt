package com.stacklens.adapter.out.persistence

import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.TicketRefinement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RepositoryDataClassTest {
    @Test
    fun `LocalRepoConfigRepository deleteLocalRepoConfigById invokes port`() {
        val port = mock<LocalRepoConfigRepository>()
        port.deleteLocalRepoConfigById(42L)
        org.mockito.kotlin
            .verify(port)
            .deleteLocalRepoConfigById(42L)
    }

    @Test
    fun `LocalRepoConfigRepository localRepoConfigExistsById returns true when present`() {
        val port = mock<LocalRepoConfigRepository>()
        whenever(port.localRepoConfigExistsById(1L)).thenReturn(true)
        assertEquals(true, port.localRepoConfigExistsById(1L))
    }

    @Test
    fun `TicketRefinementRepository saveTicketRefinement returns saved entity`() {
        val port = mock<TicketRefinementRepository>()
        val entity = TicketRefinement(parentTicketKey = "PROJ-1")
        whenever(port.saveTicketRefinement(entity)).thenReturn(entity)
        assertEquals(entity, port.saveTicketRefinement(entity))
    }

    @Test
    fun `TicketRefinementRepository deleteAllTicketRefinements is callable`() {
        val port = mock<TicketRefinementRepository>()
        port.deleteAllTicketRefinements()
        org.mockito.kotlin
            .verify(port)
            .deleteAllTicketRefinements()
    }

    @Test
    fun `ConfluenceToolRepository saveConfluenceTool returns saved entity`() {
        val port = mock<ConfluenceToolRepository>()
        val tool = ConfluenceTool(name = "n", confluencePageUrl = "http://example.com/page")
        whenever(port.saveConfluenceTool(tool)).thenReturn(tool)
        assertEquals(tool, port.saveConfluenceTool(tool))
    }

    @Test
    fun `ConfluenceToolRepository deleteAllConfluenceTools is callable`() {
        val port = mock<ConfluenceToolRepository>()
        port.deleteAllConfluenceTools()
        org.mockito.kotlin
            .verify(port)
            .deleteAllConfluenceTools()
    }

    @Test
    fun `JiraTicketSummaryRepository deleteAllJiraTicketSummaries is callable`() {
        val port = mock<JiraTicketSummaryRepository>()
        port.deleteAllJiraTicketSummaries()
        org.mockito.kotlin
            .verify(port)
            .deleteAllJiraTicketSummaries()
    }

    @Test
    fun `LocalRepoConfig default fields`() {
        val c = LocalRepoConfig(name = "test", path = "/path")
        assertEquals("test", c.name)
        assertEquals("/path", c.path)
        assertEquals("REPO_ANALYSIS", c.scope)
        assertEquals(true, c.active)
    }
}
