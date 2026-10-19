package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiDtosCoverageTest {
    @Test
    fun `GitlabConfigDto defaults are sensible`() {
        val d = GitlabConfigDto()
        assertNull(d.id)
        assertEquals("", d.name)
        assertEquals("", d.baseUrl)
        assertEquals("", d.accessToken)
        assertFalse(d.accessTokenSet)
        assertNull(d.groupId)
        assertNull(d.projectIds)
        assertNull(d.defaultBranch)
        assertTrue(d.active)
    }

    @Test
    fun `GitlabConfigDto stores provided values verbatim`() {
        val d =
            GitlabConfigDto(
                id = 7L,
                name = "n",
                baseUrl = "https://g",
                accessToken = "tok",
                accessTokenSet = true,
                groupId = "grp",
                projectIds = "1,2",
                defaultBranch = "main",
                active = false,
            )
        assertEquals(7L, d.id)
        assertEquals("tok", d.accessToken)
        assertTrue(d.accessTokenSet)
        assertEquals("grp", d.groupId)
        assertEquals("1,2", d.projectIds)
        assertEquals("main", d.defaultBranch)
        assertFalse(d.active)
    }

    @Test
    fun `DatabaseConfigDto defaults are sensible`() {
        val d = DatabaseConfigDto()
        assertEquals("POSTGRESQL", d.dbType)
        assertEquals(5432, d.port)
        assertFalse(d.passwordSet)
        assertNull(d.schemas)
    }

    @Test
    fun `DatabaseConfigDto stores provided values verbatim`() {
        val d =
            DatabaseConfigDto(
                id = 1L,
                dbType = "MYSQL",
                port = 3306,
                passwordSet = true,
                schemas = "public",
            )
        assertEquals("MYSQL", d.dbType)
        assertEquals(3306, d.port)
        assertTrue(d.passwordSet)
        assertEquals("public", d.schemas)
    }

    @Test
    fun `ConfluenceConfigDto defaults are sensible`() {
        val d = ConfluenceConfigDto()
        assertNull(d.id)
        assertTrue(d.cloudMode)
        assertTrue(d.active)
        assertNull(d.searchRootUrl)
    }

    @Test
    fun `ConfluenceConfigDto stores provided values verbatim`() {
        val d =
            ConfluenceConfigDto(
                id = 2L,
                spaceKeys = "DEV",
                cloudMode = false,
                searchRootUrl = "https://x",
                active = false,
            )
        assertEquals(2L, d.id)
        assertEquals("DEV", d.spaceKeys)
        assertFalse(d.cloudMode)
        assertEquals("https://x", d.searchRootUrl)
        assertFalse(d.active)
    }

    @Test
    fun `GrafanaConfigDto defaults are sensible`() {
        val d = GrafanaConfigDto()
        assertEquals(1L, d.orgId)
        assertTrue(d.scanPanelQueries)
        assertFalse(d.passwordSet)
        assertFalse(d.apiTokenSet)
    }

    @Test
    fun `GrafanaConfigDto stores provided values verbatim`() {
        val d =
            GrafanaConfigDto(
                id = 3L,
                orgId = 7L,
                scanPanelQueries = false,
                passwordSet = true,
                apiTokenSet = true,
            )
        assertEquals(7L, d.orgId)
        assertFalse(d.scanPanelQueries)
        assertTrue(d.passwordSet)
        assertTrue(d.apiTokenSet)
    }

    @Test
    fun `JiraConfigDto defaults are sensible`() {
        val d = JiraConfigDto()
        assertTrue(d.cloudMode)
        assertFalse(d.apiTokenSet)
        assertEquals("", d.commentVisibilityRole)
    }

    @Test
    fun `JiraConfigDto stores provided values verbatim`() {
        val d =
            JiraConfigDto(
                id = 4L,
                projectKey = "PROJ",
                cloudMode = false,
                apiTokenSet = true,
                commentVisibilityRole = "Developers",
            )
        assertEquals("PROJ", d.projectKey)
        assertFalse(d.cloudMode)
        assertTrue(d.apiTokenSet)
        assertEquals("Developers", d.commentVisibilityRole)
    }

    @Test
    fun `AiConfigDto defaults are sensible`() {
        val d = AiConfigDto()
        assertEquals(32768, d.ollamaNumCtx)
        assertTrue(d.availableModels.isEmpty())
    }

    @Test
    fun `AiConfigDto stores provided values verbatim`() {
        val d =
            AiConfigDto(
                githubToken = "tok",
                githubTokenSet = true,
                openAiApiKey = "k",
                openAiApiKeySet = true,
                openAiReasoningEffort = "low",
                model = "gpt-4",
                availableModels = listOf("gpt-4", "gpt-3.5"),
                aiProvider = "openai",
                ollamaUrl = "http://localhost:11434",
                ollamaNumCtx = 8192,
            )
        assertEquals("tok", d.githubToken)
        assertTrue(d.githubTokenSet)
        assertEquals("k", d.openAiApiKey)
        assertEquals("low", d.openAiReasoningEffort)
        assertEquals("gpt-4", d.model)
        assertEquals(listOf("gpt-4", "gpt-3.5"), d.availableModels)
        assertEquals("openai", d.aiProvider)
        assertEquals("http://localhost:11434", d.ollamaUrl)
        assertEquals(8192, d.ollamaNumCtx)
    }

    @Test
    fun `ConfigExportDto defaults are sensible`() {
        val d = ConfigExportDto()
        assertEquals(1, d.version)
        assertEquals("", d.exportedAt)
        assertTrue(d.gitlab.isEmpty())
        assertTrue(d.databases.isEmpty())
        assertTrue(d.confluence.isEmpty())
        assertTrue(d.grafana.isEmpty())
        assertTrue(d.jira.isEmpty())
        assertTrue(d.github.isEmpty())
        assertTrue(d.mcp.isEmpty())
        assertNull(d.ai)
    }

    @Test
    fun `ConfigExportDto stores provided values verbatim`() {
        val ai = AiExportDto(githubToken = "tok", model = "m")
        val d =
            ConfigExportDto(
                version = 2,
                exportedAt = "2025-01-01",
                gitlab = listOf(GitlabConfigDto(name = "g")),
                databases = listOf(DatabaseConfigDto(name = "db")),
                confluence = listOf(ConfluenceConfigDto(name = "c")),
                grafana = listOf(GrafanaConfigDto(name = "gr")),
                jira = listOf(JiraConfigDto(name = "j")),
                github = listOf(GithubRepoExportDto(name = "gh")),
                mcp = listOf(McpServerExportDto(name = "m")),
                ai = ai,
            )
        assertEquals(2, d.version)
        assertEquals("2025-01-01", d.exportedAt)
        assertEquals(1, d.gitlab.size)
        assertEquals(1, d.databases.size)
        assertEquals(ai, d.ai)
    }

    @Test
    fun `GithubRepoExportDto defaults are sensible`() {
        val d = GithubRepoExportDto()
        assertEquals("", d.name)
        assertEquals("", d.owner)
        assertEquals("", d.repo)
        assertNull(d.branch)
        assertNull(d.accessToken)
        assertNull(d.apiBase)
        assertTrue(d.active)
    }

    @Test
    fun `GithubRepoExportDto stores provided values verbatim`() {
        val d =
            GithubRepoExportDto(
                name = "n",
                owner = "o",
                repo = "r",
                branch = "main",
                accessToken = "tok",
                apiBase = "https://api.gh",
                active = false,
            )
        assertEquals("n", d.name)
        assertEquals("o", d.owner)
        assertEquals("r", d.repo)
        assertEquals("main", d.branch)
        assertEquals("tok", d.accessToken)
        assertEquals("https://api.gh", d.apiBase)
        assertFalse(d.active)
    }

    @Test
    fun `McpServerExportDto defaults are sensible`() {
        val d = McpServerExportDto()
        assertFalse(d.active)
        assertTrue(d.routeJira)
        assertTrue(d.routeConfluence)
        assertTrue(d.routeGitlab)
        assertTrue(d.routeGithub)
        assertTrue(d.routeGrafana)
        assertTrue(d.routeDatabase)
    }

    @Test
    fun `McpServerExportDto stores provided values verbatim`() {
        val d =
            McpServerExportDto(
                name = "m",
                baseUrl = "https://m",
                authToken = "tok",
                active = true,
                routeJira = false,
                routeConfluence = false,
                routeGitlab = false,
                routeGithub = false,
                routeGrafana = false,
                routeDatabase = false,
                jiraBaseUrl = "jbu",
                jiraUsername = "ju",
                jiraApiToken = "jt",
                jiraCloudMode = true,
                confluenceBaseUrl = "cbu",
                confluenceUsername = "cu",
                confluenceApiToken = "ct",
                confluenceCloudMode = true,
                gitlabBaseUrl = "gbu",
                gitlabAccessToken = "gat",
            )
        assertEquals("m", d.name)
        assertEquals("https://m", d.baseUrl)
        assertEquals("tok", d.authToken)
        assertTrue(d.active)
        assertFalse(d.routeJira)
        assertEquals("jbu", d.jiraBaseUrl)
        assertTrue(d.jiraCloudMode)
    }

    @Test
    fun `AiExportDto defaults are sensible`() {
        val d = AiExportDto()
        assertNull(d.githubToken)
        assertNull(d.model)
        assertNull(d.availableModels)
    }

    @Test
    fun `AiExportDto stores provided values verbatim`() {
        val d =
            AiExportDto(
                githubToken = "tok",
                model = "gpt-4",
                availableModels = listOf("gpt-4"),
                aiProvider = "openai",
                ollamaUrl = "http://o",
                ollamaNumCtx = 4096,
                lmStudioUrl = "http://lm",
                openAiApiKey = "k",
                openAiReasoningEffort = "low",
                openRouterApiKey = "rk",
            )
        assertEquals("tok", d.githubToken)
        assertEquals("gpt-4", d.model)
        assertEquals(listOf("gpt-4"), d.availableModels)
        assertEquals("openai", d.aiProvider)
        assertEquals("http://o", d.ollamaUrl)
        assertEquals(4096, d.ollamaNumCtx)
        assertEquals("http://lm", d.lmStudioUrl)
        assertEquals("k", d.openAiApiKey)
        assertEquals("low", d.openAiReasoningEffort)
        assertEquals("rk", d.openRouterApiKey)
    }
}
