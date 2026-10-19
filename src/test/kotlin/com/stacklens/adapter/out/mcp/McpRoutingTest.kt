package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpRoutingTest {
    private val repo = mock<McpServerConfigPort>()

    private fun cfg(
        active: Boolean = true,
        routeJira: Boolean = false,
        routeConfluence: Boolean = false,
        routeGitlab: Boolean = false,
        routeGithub: Boolean = false,
        routeGrafana: Boolean = false,
        jiraBaseUrl: String = "",
        jiraApiToken: String = "",
        confluenceBaseUrl: String = "",
        confluenceApiToken: String = "",
        gitlabAccessToken: String = "",
        githubToken: String = "",
        grafanaBaseUrl: String = "",
    ) = McpServerConfig(
        name = "test",
        active = active,
        routeJira = routeJira,
        routeConfluence = routeConfluence,
        routeGitlab = routeGitlab,
        routeGithub = routeGithub,
        routeGrafana = routeGrafana,
        jiraBaseUrl = jiraBaseUrl,
        jiraApiToken = jiraApiToken,
        confluenceBaseUrl = confluenceBaseUrl,
        confluenceApiToken = confluenceApiToken,
        gitlabAccessToken = gitlabAccessToken,
        githubToken = githubToken,
        grafanaBaseUrl = grafanaBaseUrl,
    )

    @Test
    fun `activeIfRouted returns null when no active config exists`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(null)
        assertNull(McpRouting.activeIfRouted(repo) { true })
    }

    @Test
    fun `activeIfRouted returns null when repo throws`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenThrow(RuntimeException("db down"))
        assertNull(McpRouting.activeIfRouted(repo) { true })
    }

    @Test
    fun `activeIfRouted returns null when config is inactive`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg(active = false))
        assertNull(McpRouting.activeIfRouted(repo) { true })
    }

    @Test
    fun `activeIfRouted returns null when predicate returns false`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg())
        assertNull(McpRouting.activeIfRouted(repo) { false })
    }

    @Test
    fun `activeIfRouted returns config when active and predicate true`() {
        val c = cfg()
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(c)
        assertEquals(c, McpRouting.activeIfRouted(repo) { true })
    }

    @Test
    fun `jiraReady returns false when routeJira is off`() {
        assertFalse(McpRouting.jiraReady(cfg(jiraBaseUrl = "u", jiraApiToken = "t")))
    }

    @Test
    fun `jiraReady returns false when url blank`() {
        assertFalse(McpRouting.jiraReady(cfg(routeJira = true, jiraApiToken = "t")))
    }

    @Test
    fun `jiraReady returns false when token blank`() {
        assertFalse(McpRouting.jiraReady(cfg(routeJira = true, jiraBaseUrl = "u")))
    }

    @Test
    fun `jiraReady returns true when route on and creds present`() {
        assertTrue(
            McpRouting.jiraReady(cfg(routeJira = true, jiraBaseUrl = "u", jiraApiToken = "t")),
        )
    }

    @Test
    fun `confluenceReady returns false when route off`() {
        assertFalse(McpRouting.confluenceReady(cfg(confluenceBaseUrl = "u", confluenceApiToken = "t")))
    }

    @Test
    fun `confluenceReady returns false when url blank`() {
        assertFalse(McpRouting.confluenceReady(cfg(routeConfluence = true, confluenceApiToken = "t")))
    }

    @Test
    fun `confluenceReady returns false when token blank`() {
        assertFalse(McpRouting.confluenceReady(cfg(routeConfluence = true, confluenceBaseUrl = "u")))
    }

    @Test
    fun `confluenceReady returns true when fully configured`() {
        assertTrue(
            McpRouting.confluenceReady(
                cfg(routeConfluence = true, confluenceBaseUrl = "u", confluenceApiToken = "t"),
            ),
        )
    }

    @Test
    fun `gitlabReady returns false when route off`() {
        assertFalse(McpRouting.gitlabReady(cfg(gitlabAccessToken = "t")))
    }

    @Test
    fun `gitlabReady returns false when token blank`() {
        assertFalse(McpRouting.gitlabReady(cfg(routeGitlab = true)))
    }

    @Test
    fun `gitlabReady returns true when configured`() {
        assertTrue(McpRouting.gitlabReady(cfg(routeGitlab = true, gitlabAccessToken = "t")))
    }

    @Test
    fun `githubReady returns false when route off`() {
        assertFalse(McpRouting.githubReady(cfg(githubToken = "t")))
    }

    @Test
    fun `githubReady returns false when token blank`() {
        assertFalse(McpRouting.githubReady(cfg(routeGithub = true)))
    }

    @Test
    fun `githubReady returns true when configured`() {
        assertTrue(McpRouting.githubReady(cfg(routeGithub = true, githubToken = "t")))
    }

    @Test
    fun `grafanaReady returns false when route off`() {
        assertFalse(McpRouting.grafanaReady(cfg(grafanaBaseUrl = "u")))
    }

    @Test
    fun `grafanaReady returns false when base url blank`() {
        assertFalse(McpRouting.grafanaReady(cfg(routeGrafana = true)))
    }

    @Test
    fun `grafanaReady returns true when route on and base url present (api key optional)`() {
        assertTrue(McpRouting.grafanaReady(cfg(routeGrafana = true, grafanaBaseUrl = "u")))
    }

    @Test
    fun `parseJsonOrNull returns null for invalid json`() {
        assertNull(McpRouting.parseJsonOrNull(ObjectMapper(), "not json"))
    }

    @Test
    fun `parseJsonOrNull returns node for valid json`() {
        val node = McpRouting.parseJsonOrNull(ObjectMapper(), """{"a":1}""")
        assertEquals(1, node!!.get("a").asInt())
    }
}
