package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiDtosTest {
    @Test
    fun `SearchRequestDto defaults`() {
        val dto = SearchRequestDto(keywords = listOf("foo"))
        assertEquals(listOf("foo"), dto.keywords)
        assertEquals(emptyList<String>(), dto.gitlabRepositories)
        assertEquals(emptyList<String>(), dto.jiraProjects)
        assertEquals(emptyList<String>(), dto.jiraComponents)
        assertEquals(emptyList<String>(), dto.jiraLabels)
        assertEquals(emptyList<String>(), dto.confluenceWebsites)
        assertEquals(emptyList<String>(), dto.grafanaPages)
        assertEquals(emptyList<String>(), dto.pipelineFilters)
        assertTrue(dto.includeGitlab)
        assertTrue(dto.includeDatabase)
        assertFalse(dto.includePipeline)
        assertFalse(dto.includeConfluence)
        assertFalse(dto.includeGrafana)
        assertFalse(dto.includeJira)
        assertFalse(dto.includeContextMap)
        assertFalse(dto.crossReferenceEnabled)
        assertEquals(emptyList<Long>(), dto.gitlabConfigIds)
        assertEquals(emptyList<Long>(), dto.databaseConfigIds)
        assertEquals(emptyList<Long>(), dto.confluenceConfigIds)
        assertEquals(emptyList<Long>(), dto.grafanaConfigIds)
        assertEquals(emptyList<Long>(), dto.jiraConfigIds)
    }

    @Test
    fun `SearchRequestDto equality and copy`() {
        val a = SearchRequestDto(keywords = listOf("a"), includeGitlab = false, includeDatabase = false)
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotNull(a.toString())
    }

    @Test
    fun `SessionDto properties`() {
        val dto =
            SessionDto(
                id = 1L,
                applicationName = "App",
                keywords = "foo",
                status = "DONE",
                resultCount = 5,
                createdAt = "2024",
                finishedAt = "2024",
            )
        assertEquals(1L, dto.id)
        assertEquals("App", dto.applicationName)
        assertEquals("foo", dto.keywords)
        assertEquals("DONE", dto.status)
        assertEquals(5, dto.resultCount)
        assertEquals("2024", dto.createdAt)
        assertEquals("2024", dto.finishedAt)
    }

    @Test
    fun `SessionDto with null fields`() {
        val dto =
            SessionDto(
                id = 2L,
                applicationName = null,
                keywords = "",
                status = "RUNNING",
                resultCount = 0,
                createdAt = "",
                finishedAt = null,
            )
        assertNull(dto.applicationName)
        assertNull(dto.finishedAt)
    }

    @Test
    fun `SessionDto equality`() {
        val a = SessionDto(1L, "A", "k", "S", 0, "t", null)
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `GitlabConfigDto defaults`() {
        val dto = GitlabConfigDto()
        assertNull(dto.id)
        assertEquals("", dto.name)
        assertEquals("", dto.baseUrl)
        assertEquals("", dto.accessToken)
        assertFalse(dto.accessTokenSet)
        assertNull(dto.groupId)
        assertNull(dto.projectIds)
        assertNull(dto.defaultBranch)
        assertTrue(dto.active)
    }

    @Test
    fun `GitlabConfigDto with values and equality`() {
        val a =
            GitlabConfigDto(
                id = 1L,
                name = "GL",
                baseUrl = "https://gl.example.com",
                accessToken = "tok",
                accessTokenSet = true,
                groupId = "g",
                projectIds = "1,2",
                defaultBranch = "main",
                active = false,
            )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotNull(a.toString())
    }

    @Test
    fun `DatabaseConfigDto defaults`() {
        val dto = DatabaseConfigDto()
        assertNull(dto.id)
        assertEquals("", dto.name)
        assertEquals("POSTGRESQL", dto.dbType)
        assertEquals("", dto.host)
        assertEquals(5432, dto.port)
        assertEquals("", dto.databaseName)
        assertEquals("", dto.username)
        assertEquals("", dto.password)
        assertFalse(dto.passwordSet)
        assertNull(dto.schemas)
        assertTrue(dto.active)
    }

    @Test
    fun `DatabaseConfigDto equality`() {
        val a =
            DatabaseConfigDto(
                id = 1L,
                name = "DB",
                dbType = "MYSQL",
                host = "localhost",
                port = 3306,
                databaseName = "mydb",
                username = "user",
                password = "pass",
                passwordSet = true,
                schemas = "public",
                active = false,
            )
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `ConfluenceConfigDto defaults`() {
        val dto = ConfluenceConfigDto()
        assertNull(dto.id)
        assertEquals("", dto.name)
        assertEquals("", dto.baseUrl)
        assertEquals("", dto.username)
        assertEquals("", dto.apiToken)
        assertFalse(dto.apiTokenSet)
        assertNull(dto.spaceKeys)
        assertTrue(dto.cloudMode)
        assertNull(dto.searchRootUrl)
        assertTrue(dto.active)
    }

    @Test
    fun `ConfluenceConfigDto equality`() {
        val a =
            ConfluenceConfigDto(
                id = 1L,
                name = "CF",
                baseUrl = "https://cf.example.com",
                username = "u",
                apiToken = "t",
                apiTokenSet = true,
                spaceKeys = "SPACE",
                cloudMode = false,
                searchRootUrl = "/root",
                active = false,
            )
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `GrafanaConfigDto defaults`() {
        val dto = GrafanaConfigDto()
        assertNull(dto.id)
        assertEquals("", dto.name)
        assertEquals("", dto.baseUrl)
        assertEquals("", dto.username)
        assertEquals("", dto.password)
        assertFalse(dto.passwordSet)
        assertEquals("", dto.apiToken)
        assertFalse(dto.apiTokenSet)
        assertEquals(1L, dto.orgId)
        assertTrue(dto.scanPanelQueries)
        assertTrue(dto.active)
    }

    @Test
    fun `GrafanaConfigDto equality`() {
        val a =
            GrafanaConfigDto(
                id = 1L,
                name = "GR",
                baseUrl = "https://gr.example.com",
                username = "u",
                password = "p",
                passwordSet = true,
                apiToken = "t",
                apiTokenSet = true,
                orgId = 2L,
                scanPanelQueries = false,
                active = false,
            )
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `JiraConfigDto defaults`() {
        val dto = JiraConfigDto()
        assertNull(dto.id)
        assertEquals("", dto.name)
        assertEquals("", dto.baseUrl)
        assertEquals("", dto.projectKey)
        assertEquals("", dto.username)
        assertEquals("", dto.apiToken)
        assertFalse(dto.apiTokenSet)
        assertTrue(dto.cloudMode)
        assertEquals("", dto.commentVisibilityRole)
        assertTrue(dto.active)
    }

    @Test
    fun `JiraConfigDto equality`() {
        val a =
            JiraConfigDto(
                id = 1L,
                name = "JI",
                baseUrl = "https://jira.example.com",
                projectKey = "PROJ",
                username = "u",
                apiToken = "t",
                apiTokenSet = true,
                cloudMode = false,
                active = false,
            )
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `AiConfigDto defaults`() {
        val dto = AiConfigDto()
        assertNull(dto.githubToken)
        assertFalse(dto.githubTokenSet)
        assertNull(dto.model)
        assertEquals(emptyList<String>(), dto.availableModels)
    }

    @Test
    fun `AiConfigDto equality`() {
        val a = AiConfigDto(githubToken = "tok", githubTokenSet = true, model = "gpt-4", availableModels = listOf("gpt-4", "gpt-3.5"))
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `LanguageDto default is en`() {
        val dto = LanguageDto()
        assertEquals("en", dto.language)
    }

    @Test
    fun `LanguageDto equality`() {
        val a = LanguageDto(language = "de")
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `ConfigExportDto defaults`() {
        val dto = ConfigExportDto()
        assertEquals(1, dto.version)
        assertEquals("", dto.exportedAt)
        assertEquals(emptyList<GitlabConfigDto>(), dto.gitlab)
        assertEquals(emptyList<DatabaseConfigDto>(), dto.databases)
        assertEquals(emptyList<ConfluenceConfigDto>(), dto.confluence)
        assertEquals(emptyList<GrafanaConfigDto>(), dto.grafana)
        assertEquals(emptyList<JiraConfigDto>(), dto.jira)
        assertEquals(emptyList<GithubRepoExportDto>(), dto.github)
        assertEquals(emptyList<McpServerExportDto>(), dto.mcp)
        assertNull(dto.ai)
    }

    @Test
    fun `ConfigExportDto equality`() {
        val a =
            ConfigExportDto(version = 2, exportedAt = "2024", gitlab = listOf(GitlabConfigDto(id = 1L)), ai = AiExportDto(model = "gpt-4"))
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `GithubRepoExportDto defaults`() {
        val dto = GithubRepoExportDto()
        assertEquals("", dto.name)
        assertEquals("", dto.owner)
        assertEquals("", dto.repo)
        assertNull(dto.branch)
        assertNull(dto.accessToken)
        assertNull(dto.apiBase)
        assertTrue(dto.active)
    }

    @Test
    fun `GithubRepoExportDto equality`() {
        val a = GithubRepoExportDto(name = "svc", owner = "acme", repo = "my-repo", branch = "main", active = false)
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `McpServerExportDto defaults`() {
        val dto = McpServerExportDto()
        assertEquals("", dto.name)
        assertEquals("", dto.baseUrl)
        assertEquals("", dto.authToken)
        assertFalse(dto.active)
        assertTrue(dto.routeJira)
        assertTrue(dto.routeConfluence)
        assertTrue(dto.routeGitlab)
        assertTrue(dto.routeGithub)
        assertTrue(dto.routeGrafana)
        assertTrue(dto.routeDatabase)
        assertEquals("", dto.jiraBaseUrl)
        assertEquals("", dto.jiraUsername)
        assertEquals("", dto.jiraApiToken)
        assertFalse(dto.jiraCloudMode)
        assertEquals("", dto.confluenceBaseUrl)
        assertEquals("", dto.confluenceUsername)
        assertEquals("", dto.confluenceApiToken)
        assertFalse(dto.confluenceCloudMode)
        assertEquals("", dto.gitlabBaseUrl)
        assertEquals("", dto.gitlabAccessToken)
        assertEquals("https://api.github.com", dto.githubApiBase)
        assertEquals("", dto.githubToken)
        assertEquals("", dto.grafanaBaseUrl)
        assertEquals("", dto.grafanaApiKey)
    }

    @Test
    fun `McpServerExportDto equality`() {
        val a = McpServerExportDto(name = "mcp1", baseUrl = "http://mcp", authToken = "tok", active = true)
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `McpServerExportDto explicit grafanaApiKey and autoStartManaged`() {
        val dto = McpServerExportDto(grafanaApiKey = "my-grafana-key", autoStartManaged = true)
        assertEquals("my-grafana-key", dto.grafanaApiKey)
        assertTrue(dto.autoStartManaged)
    }

    @Test
    fun `AiExportDto defaults`() {
        val dto = AiExportDto()
        assertNull(dto.githubToken)
        assertNull(dto.model)
    }

    @Test
    fun `AiExportDto equality`() {
        val a = AiExportDto(githubToken = "tok", model = "gpt-4")
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `ExportRequestDto properties`() {
        val dto = ExportRequestDto(password = "secret")
        assertEquals("secret", dto.password)
    }

    @Test
    fun `ExportRequestDto equality`() {
        val a = ExportRequestDto(password = "pass")
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `SaveConfigToPathRequest properties`() {
        val dto = SaveConfigToPathRequest(path = "/tmp", filename = "export.json", content = "{}")
        assertEquals("/tmp", dto.path)
        assertEquals("export.json", dto.filename)
        assertEquals("{}", dto.content)
    }

    @Test
    fun `SaveConfigToPathRequest equality`() {
        val a = SaveConfigToPathRequest(path = "/a", filename = "f", content = "c")
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `ImportFromPathRequest properties`() {
        val dto = ImportFromPathRequest(path = "/tmp/export.json", password = "pass")
        assertEquals("/tmp/export.json", dto.path)
        assertEquals("pass", dto.password)
    }

    @Test
    fun `ImportFromPathRequest password can be null`() {
        val dto = ImportFromPathRequest(path = "/tmp/export.json", password = null)
        assertNull(dto.password)
    }

    @Test
    fun `ImportFromPathRequest equality`() {
        val a = ImportFromPathRequest(path = "/a", password = "p")
        val b = a.copy()
        assertEquals(a, b)
    }
}
