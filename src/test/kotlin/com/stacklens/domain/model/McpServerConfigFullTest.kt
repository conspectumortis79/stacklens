package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpServerConfigFullTest {
    @Test
    fun `McpServerConfig default values match expected defaults`() {
        val cfg = McpServerConfig()
        assertEquals(0L, cfg.id)
        assertEquals("", cfg.name)
        assertEquals("", cfg.baseUrl)
        assertEquals("", cfg.authToken)
        assertFalse(cfg.active)
        assertTrue(cfg.routeJira)
        assertTrue(cfg.routeConfluence)
        assertTrue(cfg.routeGitlab)
        assertTrue(cfg.routeGithub)
        assertTrue(cfg.routeGrafana)
        assertTrue(cfg.routeDatabase)
        assertEquals("", cfg.jiraBaseUrl)
        assertEquals("", cfg.jiraUsername)
        assertEquals("", cfg.jiraApiToken)
        assertTrue(cfg.jiraCloudMode)
        assertEquals("", cfg.confluenceBaseUrl)
        assertEquals("", cfg.confluenceUsername)
        assertEquals("", cfg.confluenceApiToken)
        assertTrue(cfg.confluenceCloudMode)
        assertEquals("https://gitlab.com", cfg.gitlabBaseUrl)
        assertEquals("", cfg.gitlabAccessToken)
        assertEquals("https://api.github.com", cfg.githubApiBase)
        assertEquals("", cfg.githubToken)
        assertEquals("", cfg.grafanaBaseUrl)
        assertEquals("", cfg.grafanaApiKey)
        assertFalse(cfg.autoStartManaged)
        assertNotNull(cfg.createdAt)
    }

    @Test
    fun `McpServerConfig setter roundtrip preserves all fields`() {
        val cfg =
            McpServerConfig(
                id = 7L,
                name = "prod-mcp",
                baseUrl = "http://localhost:7456",
                authToken = "tok",
                active = true,
                routeJira = false,
                routeConfluence = false,
                routeGitlab = false,
                routeGithub = false,
                routeGrafana = false,
                routeDatabase = false,
                jiraBaseUrl = "https://jira.example.com",
                jiraUsername = "alice",
                jiraApiToken = "jt",
                jiraCloudMode = false,
                confluenceBaseUrl = "https://conf.example.com",
                confluenceUsername = "bob",
                confluenceApiToken = "ct",
                confluenceCloudMode = false,
                gitlabBaseUrl = "https://gl.example.com",
                gitlabAccessToken = "gt",
                githubApiBase = "https://gh.example.com",
                githubToken = "ght",
                grafanaBaseUrl = "https://grafana.example.com",
                grafanaApiKey = "gak",
                autoStartManaged = true,
            )
        assertEquals(7L, cfg.id)
        assertEquals("prod-mcp", cfg.name)
        assertEquals("http://localhost:7456", cfg.baseUrl)
        assertEquals("tok", cfg.authToken)
        assertTrue(cfg.active)
        assertFalse(cfg.routeJira)
        assertFalse(cfg.routeConfluence)
        assertFalse(cfg.routeGitlab)
        assertFalse(cfg.routeGithub)
        assertFalse(cfg.routeGrafana)
        assertFalse(cfg.routeDatabase)
        assertEquals("https://jira.example.com", cfg.jiraBaseUrl)
        assertEquals("alice", cfg.jiraUsername)
        assertEquals("jt", cfg.jiraApiToken)
        assertFalse(cfg.jiraCloudMode)
        assertEquals("https://conf.example.com", cfg.confluenceBaseUrl)
        assertEquals("bob", cfg.confluenceUsername)
        assertEquals("ct", cfg.confluenceApiToken)
        assertFalse(cfg.confluenceCloudMode)
        assertEquals("https://gl.example.com", cfg.gitlabBaseUrl)
        assertEquals("gt", cfg.gitlabAccessToken)
        assertEquals("https://gh.example.com", cfg.githubApiBase)
        assertEquals("ght", cfg.githubToken)
        assertEquals("https://grafana.example.com", cfg.grafanaBaseUrl)
        assertEquals("gak", cfg.grafanaApiKey)
        assertTrue(cfg.autoStartManaged)
    }

    @Test
    fun `McpServerConfig supports mutating all var fields`() {
        val cfg = McpServerConfig()
        cfg.name = "new-name"
        cfg.baseUrl = "http://new"
        cfg.authToken = "new-tok"
        cfg.active = true
        cfg.routeJira = false
        cfg.routeConfluence = false
        cfg.routeGitlab = false
        cfg.routeGithub = false
        cfg.routeGrafana = false
        cfg.routeDatabase = false
        cfg.jiraBaseUrl = "https://j"
        cfg.jiraUsername = "u"
        cfg.jiraApiToken = "t"
        cfg.jiraCloudMode = false
        cfg.confluenceBaseUrl = "https://c"
        cfg.confluenceUsername = "u"
        cfg.confluenceApiToken = "t"
        cfg.confluenceCloudMode = false
        cfg.gitlabBaseUrl = "https://gl"
        cfg.gitlabAccessToken = "t"
        cfg.githubApiBase = "https://gh"
        cfg.githubToken = "t"
        cfg.grafanaBaseUrl = "https://gr"
        cfg.grafanaApiKey = "k"
        cfg.autoStartManaged = true

        assertEquals("new-name", cfg.name)
        assertEquals("http://new", cfg.baseUrl)
        assertEquals("new-tok", cfg.authToken)
        assertTrue(cfg.active)
        assertFalse(cfg.routeJira)
        assertFalse(cfg.routeGrafana)
        assertTrue(cfg.autoStartManaged)
    }
}
