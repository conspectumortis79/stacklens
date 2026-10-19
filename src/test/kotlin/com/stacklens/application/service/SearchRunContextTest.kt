package com.stacklens.application.service

import com.stacklens.application.service.search.SearchRunContext
import com.stacklens.domain.model.SearchSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SearchRunContextTest {
    @Test
    fun `constructor populates all fields`() {
        val session = SearchSession(applicationName = "app", keywords = "kw")
        val ctx =
            SearchRunContext(
                sessionId = 1L,
                session = session,
                keywords = listOf("foo"),
                gitlabRepos = listOf("g/r"),
                confluenceSites = listOf("c"),
                jiraProjects = listOf("PROJ"),
                jiraComponents = listOf("comp"),
                jiraLabels = listOf("label"),
                grafanaPages = listOf("p"),
                pipelineFilters = listOf("f"),
                gitlabConfigs = emptyList(),
                confluenceConfigs = emptyList(),
                jiraConfigs = emptyList(),
                grafanaConfigs = emptyList(),
                dbConfigs = emptyList(),
                githubConfigs = emptyList(),
                hitProjectIds = mutableSetOf(),
                dbResults = java.util.concurrent.CopyOnWriteArrayList(),
                databaseConnectionErrors = java.util.concurrent.CopyOnWriteArrayList(),
            )
        assertEquals(1L, ctx.sessionId)
        assertSame(session, ctx.session)
        assertEquals(listOf("foo"), ctx.keywords)
        assertEquals(listOf("g/r"), ctx.gitlabRepos)
        assertEquals(listOf("c"), ctx.confluenceSites)
        assertEquals(listOf("PROJ"), ctx.jiraProjects)
        assertEquals(listOf("comp"), ctx.jiraComponents)
        assertEquals(listOf("label"), ctx.jiraLabels)
        assertEquals(listOf("p"), ctx.grafanaPages)
        assertEquals(listOf("f"), ctx.pipelineFilters)
    }

    @Test
    fun `hitProjectIds is mutable across contexts`() {
        val a =
            SearchRunContext(
                sessionId = 1L,
                session = SearchSession(applicationName = "a", keywords = "k"),
                keywords = emptyList(),
                gitlabRepos = emptyList(),
                confluenceSites = emptyList(),
                jiraProjects = emptyList(),
                jiraComponents = emptyList(),
                jiraLabels = emptyList(),
                grafanaPages = emptyList(),
                pipelineFilters = emptyList(),
                gitlabConfigs = emptyList(),
                confluenceConfigs = emptyList(),
                jiraConfigs = emptyList(),
                grafanaConfigs = emptyList(),
                dbConfigs = emptyList(),
                githubConfigs = emptyList(),
                hitProjectIds = mutableSetOf(),
                dbResults = java.util.concurrent.CopyOnWriteArrayList(),
                databaseConnectionErrors = java.util.concurrent.CopyOnWriteArrayList(),
            )
        a.hitProjectIds.add(1L)
        a.hitProjectIds.add(2L)
        assertEquals(setOf(1L, 2L), a.hitProjectIds)
    }
}
