package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ConfigEntitiesCoverageTest {
    @Test
    fun `GithubRepoConfig defaults are sensible`() {
        val c =
            GithubRepoConfig(
                name = "n",
                owner = "owner",
                repo = "repo",
            )
        assertEquals(0L, c.id)
        assertEquals("n", c.name)
        assertEquals("owner", c.owner)
        assertEquals("repo", c.repo)
        assertNull(c.branch)
        assertNull(c.accessToken)
        assertNull(c.apiBase)
        assertTrue(c.active)
        assertEquals(0, c.consecutiveFailures)
        assertNull(c.lastErrorAt)
        assertNull(c.lastErrorMessage)
    }

    @Test
    fun `GithubRepoConfig stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val c =
            GithubRepoConfig(
                id = 7L,
                name = "n",
                owner = "o",
                repo = "r",
                branch = "feat",
                accessToken = "tok",
                apiBase = "https://api.example",
                active = false,
                consecutiveFailures = 5,
                lastErrorAt = now,
                lastErrorMessage = "boom",
            )
        assertEquals(7L, c.id)
        assertEquals("feat", c.branch)
        assertEquals("tok", c.accessToken)
        assertEquals("https://api.example", c.apiBase)
        assertFalse(c.active)
        assertEquals(5, c.consecutiveFailures)
        assertEquals(now, c.lastErrorAt)
        assertEquals("boom", c.lastErrorMessage)
    }

    @Test
    fun `GrafanaConfig defaults are sensible`() {
        val c =
            GrafanaConfig(
                name = "n",
                baseUrl = "https://g",
                username = "u",
                password = "p",
            )
        assertEquals(0L, c.id)
        assertEquals("n", c.name)
        assertEquals("https://g", c.baseUrl)
        assertEquals("u", c.username)
        assertEquals("p", c.password)
        assertEquals("", c.apiToken)
        assertEquals(1L, c.orgId)
        assertTrue(c.scanPanelQueries)
        assertTrue(c.active)
        assertEquals(0, c.consecutiveFailures)
        assertNull(c.lastErrorAt)
        assertNull(c.lastErrorMessage)
    }

    @Test
    fun `GrafanaConfig stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val c =
            GrafanaConfig(
                id = 11L,
                name = "n",
                baseUrl = "https://g",
                username = "u",
                password = "p",
                apiToken = "tok",
                orgId = 9L,
                scanPanelQueries = false,
                active = false,
                consecutiveFailures = 3,
                lastErrorAt = now,
                lastErrorMessage = "boom",
            )
        assertEquals(11L, c.id)
        assertEquals("tok", c.apiToken)
        assertEquals(9L, c.orgId)
        assertFalse(c.scanPanelQueries)
        assertFalse(c.active)
        assertEquals(3, c.consecutiveFailures)
        assertEquals(now, c.lastErrorAt)
        assertEquals("boom", c.lastErrorMessage)
    }

    @Test
    fun `ConfluenceConfig defaults are sensible`() {
        val c =
            ConfluenceConfig(
                name = "n",
                baseUrl = "https://c",
                username = "u",
                apiToken = "t",
            )
        assertEquals(0L, c.id)
        assertEquals("n", c.name)
        assertEquals("https://c", c.baseUrl)
        assertEquals("u", c.username)
        assertEquals("t", c.apiToken)
        assertNull(c.spaceKeys)
        assertTrue(c.cloudMode)
        assertTrue(c.spaceKeyList().isEmpty())
        assertTrue(c.active)
        assertEquals(0, c.consecutiveFailures)
        assertNull(c.lastErrorAt)
        assertNull(c.lastErrorMessage)
    }

    @Test
    fun `ConfluenceConfig stores provided values verbatim`() {
        val c =
            ConfluenceConfig(
                id = 99L,
                name = "n",
                baseUrl = "https://company.atlassian.net",
                username = "u",
                apiToken = "t",
                spaceKeys = "DEV,QA",
                active = false,
                consecutiveFailures = 2,
                lastErrorAt = LocalDateTime.of(2025, 1, 1, 0, 0),
                lastErrorMessage = "boom",
            )
        assertEquals(99L, c.id)
        assertEquals("DEV,QA", c.spaceKeys)
        assertEquals(listOf("DEV", "QA"), c.spaceKeyList())
        assertTrue(c.cloudMode)
        assertFalse(c.active)
        assertEquals(2, c.consecutiveFailures)
    }

    @Test
    fun `DatabaseConfig stores provided values verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val c =
            DatabaseConfig(
                id = 11L,
                name = "n",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "h",
                port = 5432,
                databaseName = "db",
                username = "u",
                password = "p",
                schemas = "public,private",
                active = false,
                consecutiveFailures = 3,
                lastErrorAt = now,
                lastErrorMessage = "boom",
            )
        assertEquals(11L, c.id)
        assertEquals(com.stacklens.domain.enums.DatabaseType.POSTGRESQL, c.dbType)
        assertEquals(5432, c.port)
        assertEquals("public,private", c.schemas)
        assertFalse(c.active)
        assertEquals(3, c.consecutiveFailures)
        assertEquals(now, c.lastErrorAt)
        assertEquals("boom", c.lastErrorMessage)
    }

    @Test
    fun `JiraConfig defaults are sensible`() {
        val c = JiraConfig(name = "n", baseUrl = "https://jira.example", apiToken = "t")
        assertEquals(0L, c.id)
        assertEquals("n", c.name)
        assertEquals("https://jira.example", c.baseUrl)
        assertEquals("", c.projectKey)
        assertEquals("", c.username)
        assertEquals("t", c.apiToken)
        assertTrue(c.cloudMode)
        assertEquals("", c.commentVisibilityRole)
        assertTrue(c.active)
        assertEquals(0, c.consecutiveFailures)
        assertNull(c.lastErrorAt)
        assertNull(c.lastErrorMessage)
    }

    @Test
    fun `JiraConfig stores provided values verbatim`() {
        val c =
            JiraConfig(
                id = 5L,
                name = "n",
                baseUrl = "https://jira.example",
                projectKey = "PROJ",
                username = "u",
                apiToken = "t",
                cloudMode = false,
                commentVisibilityRole = "Developers",
                active = false,
                consecutiveFailures = 1,
                lastErrorAt = LocalDateTime.of(2025, 1, 1, 0, 0),
                lastErrorMessage = "boom",
            )
        assertEquals(5L, c.id)
        assertEquals("https://jira.example", c.baseUrl)
        assertEquals("PROJ", c.projectKey)
        assertEquals("u", c.username)
        assertFalse(c.cloudMode)
        assertEquals("Developers", c.commentVisibilityRole)
        assertFalse(c.active)
        assertEquals(1, c.consecutiveFailures)
    }
}
