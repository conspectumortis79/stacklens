package com.stacklens.domain.model

import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainModelDefaultsTest {
    @Test
    fun `AiAnalysis default status is PENDING`() {
        val entity = AiAnalysis(sessionId = 1L)
        assertEquals("PENDING", entity.status)
    }

    @Test
    fun `AiAnalysis optional fields default to null`() {
        val entity = AiAnalysis(sessionId = 1L)
        assertNull(entity.analysisText)
        assertNull(entity.drawioXml)
        assertNull(entity.mermaid)
        assertNull(entity.modelUsed)
        assertNull(entity.inputTokens)
        assertNull(entity.outputTokens)
        assertNull(entity.errorMessage)
        assertNull(entity.completedAt)
    }

    @Test
    fun `AiAnalysis id defaults to 0`() {
        assertEquals(0L, AiAnalysis(sessionId = 5L).id)
    }

    @Test
    fun `JwtAnalysisRecord stores required fields`() {
        val record = JwtAnalysisRecord(token = "tok.tok.tok", status = "VALID")
        assertEquals("tok.tok.tok", record.token)
        assertEquals("VALID", record.status)
    }

    @Test
    fun `JwtAnalysisRecord optional fields default to null`() {
        val record = JwtAnalysisRecord(token = "a.b.c", status = "EXPIRED")
        assertNull(record.jwtId)
        assertNull(record.audience)
        assertNull(record.issuer)
        assertNull(record.subject)
    }

    @Test
    fun `JwtAnalysisRecord can store optional fields`() {
        val record =
            JwtAnalysisRecord(
                token = "x.y.z",
                status = "VALID",
                jwtId = "jwt-id-1",
                audience = "aud1,aud2",
                issuer = "https://issuer.example.com",
                subject = "user@example.com",
            )
        assertEquals("jwt-id-1", record.jwtId)
        assertEquals("aud1,aud2", record.audience)
        assertEquals("https://issuer.example.com", record.issuer)
        assertEquals("user@example.com", record.subject)
    }

    @Test
    fun `LocalRepoConfig default active is true`() {
        val config = LocalRepoConfig(name = "repo", path = "/home/dev/repo")
        assertTrue(config.active)
    }

    @Test
    fun `LocalRepoConfig default scope is REPO_ANALYSIS`() {
        val config = LocalRepoConfig(name = "repo", path = "/home/dev/repo")
        assertEquals("REPO_ANALYSIS", config.scope)
    }

    @Test
    fun `LocalRepoConfig stores name and path`() {
        val config = LocalRepoConfig(name = "my-repo", path = "/opt/repos/my-repo")
        assertEquals("my-repo", config.name)
        assertEquals("/opt/repos/my-repo", config.path)
    }

    @Test
    fun `SqlAnalysisRecord default status is RUNNING`() {
        val record = SqlAnalysisRecord(sql = "SELECT 1")
        assertEquals("RUNNING", record.status)
    }

    @Test
    fun `SqlAnalysisRecord optional fields default to null`() {
        val record = SqlAnalysisRecord(sql = "SELECT 1")
        assertNull(record.ddl)
        assertNull(record.testData)
        assertNull(record.analysisResult)
    }

    @Test
    fun `SqlAnalysisRecord stores sql and optional ddl`() {
        val record = SqlAnalysisRecord(sql = "SELECT * FROM users", ddl = "CREATE TABLE users(id INT)")
        assertEquals("SELECT * FROM users", record.sql)
        assertEquals("CREATE TABLE users(id INT)", record.ddl)
    }

    @Test
    fun `ReviewAnalysis default status is PENDING`() {
        val analysis = ReviewAnalysis(jiraSprintUrl = "https://jira.example.com/board/1")
        assertEquals("PENDING", analysis.status)
    }

    @Test
    fun `ReviewAnalysis optional fields default to null`() {
        val analysis = ReviewAnalysis(jiraSprintUrl = "https://jira.example.com/board/1")
        assertNull(analysis.jiraConfigId)
        assertNull(analysis.currentStep)
        assertNull(analysis.errorMessage)
        assertNull(analysis.sprintInfoJson)
        assertNull(analysis.sprintIssuesJson)
        assertNull(analysis.resultMarkdown)
        assertNull(analysis.processLogJson)
        assertNull(analysis.completedAt)
    }

    @Test
    fun `AiConfig id defaults to 1`() {
        assertEquals(1L, AiConfig().id)
    }

    @Test
    fun `AiConfig default language is en`() {
        assertEquals("en", AiConfig().language)
    }

    @Test
    fun `AiConfig optional fields default to null`() {
        val config = AiConfig()
        assertNull(config.githubToken)
        assertNull(config.model)
        assertNull(config.availableModels)
    }

    @Test
    fun `GithubRepoConfig default active is true`() {
        val config = GithubRepoConfig(name = "repo", owner = "acme", repo = "service")
        assertTrue(config.active)
    }

    @Test
    fun `GithubRepoConfig optional fields default to null`() {
        val config = GithubRepoConfig(name = "repo", owner = "acme", repo = "service")
        assertNull(config.branch)
        assertNull(config.accessToken)
    }

    @Test
    fun `GrafanaConfig default apiToken is empty string`() {
        val config =
            GrafanaConfig(
                name = "grafana",
                baseUrl = "https://grafana.example.com",
                username = "admin",
                password = "pass",
            )
        assertEquals("", config.apiToken)
    }

    @Test
    fun `GrafanaConfig default orgId is 1`() {
        val config =
            GrafanaConfig(
                name = "grafana",
                baseUrl = "https://grafana.example.com",
                username = "admin",
                password = "pass",
            )
        assertEquals(1L, config.orgId)
    }

    @Test
    fun `GrafanaConfig default scanPanelQueries is true`() {
        val config =
            GrafanaConfig(
                name = "grafana",
                baseUrl = "https://grafana.example.com",
                username = "admin",
                password = "pass",
            )
        assertTrue(config.scanPanelQueries)
    }

    @Test
    fun `GrafanaConfig default active is true`() {
        val config =
            GrafanaConfig(
                name = "grafana",
                baseUrl = "https://grafana.example.com",
                username = "admin",
                password = "pass",
            )
        assertTrue(config.active)
    }

    @Test
    fun `ReviewAnalysisConfig id defaults to 1`() {
        assertEquals(1L, ReviewAnalysisConfig().id)
    }

    @Test
    fun `ReviewAnalysisConfig jiraSprintUrl defaults to empty string`() {
        assertEquals("", ReviewAnalysisConfig().jiraSprintUrl)
    }

    @Test
    fun `ReviewAnalysisConfig optional fields default to null`() {
        val config = ReviewAnalysisConfig()
        assertNull(config.jiraConfigId)
    }

    @Test
    fun `SearchResult stores required fields`() {
        val result =
            SearchResult(
                sessionId = 10L,
                keyword = "password",
                sourceType = SourceType.GITLAB,
            )
        assertEquals(10L, result.sessionId)
        assertEquals("password", result.keyword)
        assertEquals(SourceType.GITLAB, result.sourceType)
    }

    @Test
    fun `SearchResult optional fields default to null`() {
        val result =
            SearchResult(
                sessionId = 10L,
                keyword = "secret",
                sourceType = SourceType.DATABASE,
            )
        assertNull(result.resultType)
        assertNull(result.repoName)
        assertNull(result.repoUrl)
        assertNull(result.projectId)
        assertNull(result.filePath)
        assertNull(result.branch)
        assertNull(result.lineNumber)
        assertNull(result.className)
        assertNull(result.functionName)
        assertNull(result.codeSnippet)
        assertNull(result.configName)
        assertNull(result.dbHost)
        assertNull(result.databaseName)
        assertNull(result.schemaName)
        assertNull(result.tableName)
        assertNull(result.columnName)
        assertNull(result.dataType)
        assertNull(result.ddlContext)
        assertNull(result.crossRefParentId)
    }

    @Test
    fun `SearchResult can store resultType`() {
        val result =
            SearchResult(
                sessionId = 1L,
                keyword = "key",
                sourceType = SourceType.GITLAB,
                resultType = ResultType.CLASS,
            )
        assertEquals(ResultType.CLASS, result.resultType)
    }
}
