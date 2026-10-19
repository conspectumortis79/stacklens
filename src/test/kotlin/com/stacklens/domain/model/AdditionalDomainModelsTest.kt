package com.stacklens.domain.model

import com.stacklens.domain.enums.FileChangeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AdditionalDomainModelsTest {
    @Test
    fun `AiAnalysis default id is zero`() {
        val a = AiAnalysis(sessionId = 42L)
        assertEquals(0L, a.id)
    }

    @Test
    fun `AiAnalysis stores required sessionId`() {
        val a = AiAnalysis(sessionId = 99L)
        assertEquals(99L, a.sessionId)
    }

    @Test
    fun `AiAnalysis default status is PENDING`() {
        val a = AiAnalysis(sessionId = 1L)
        assertEquals("PENDING", a.status)
    }

    @Test
    fun `AiAnalysis optional text fields default to null`() {
        val a = AiAnalysis(sessionId = 1L)
        assertNull(a.analysisText)
        assertNull(a.drawioXml)
        assertNull(a.mermaid)
        assertNull(a.modelUsed)
        assertNull(a.inputTokens)
        assertNull(a.outputTokens)
        assertNull(a.errorMessage)
        assertNull(a.completedAt)
    }

    @Test
    fun `AiAnalysis createdAt defaults to current time`() {
        val before = LocalDateTime.now().minusSeconds(1)
        val a = AiAnalysis(sessionId = 1L)
        val after = LocalDateTime.now().plusSeconds(1)
        assertNotNull(a.createdAt)
        assertTrue(a.createdAt.isAfter(before) && a.createdAt.isBefore(after))
    }

    @Test
    fun `AiAnalysis can store all fields`() {
        val now = LocalDateTime.now()
        val a =
            AiAnalysis(
                id = 7L,
                sessionId = 11L,
                status = "COMPLETED",
                analysisText = "summary",
                drawioXml = "<xml/>",
                mermaid = "graph TD",
                modelUsed = "gpt-4",
                inputTokens = 100,
                outputTokens = 200,
                errorMessage = null,
                createdAt = now,
                completedAt = now,
            )
        assertEquals(7L, a.id)
        assertEquals(11L, a.sessionId)
        assertEquals("COMPLETED", a.status)
        assertEquals("summary", a.analysisText)
        assertEquals("<xml/>", a.drawioXml)
        assertEquals("graph TD", a.mermaid)
        assertEquals("gpt-4", a.modelUsed)
        assertEquals(100, a.inputTokens)
        assertEquals(200, a.outputTokens)
        assertEquals(now, a.createdAt)
        assertEquals(now, a.completedAt)
    }

    @Test
    fun `AiConfig default id is 1 (singleton)`() {
        val c = AiConfig()
        assertEquals(1L, c.id)
    }

    @Test
    fun `AiConfig default language is en`() {
        val c = AiConfig()
        assertEquals("en", c.language)
    }

    @Test
    fun `AiConfig default aiProvider is copilot`() {
        val c = AiConfig()
        assertEquals("copilot", c.aiProvider)
    }

    @Test
    fun `AiConfig default ollamaNumCtx is 32768`() {
        val c = AiConfig()
        assertEquals(32768, c.ollamaNumCtx)
    }

    @Test
    fun `AiConfig optional credential fields default to null`() {
        val c = AiConfig()
        assertNull(c.githubToken)
        assertNull(c.model)
        assertNull(c.availableModels)
        assertNull(c.ollamaUrl)
        assertNull(c.lmStudioUrl)
        assertNull(c.openAiApiKey)
        assertNull(c.openAiReasoningEffort)
        assertNull(c.openRouterApiKey)
    }

    @Test
    fun `AiConfig can store all credential fields`() {
        val c =
            AiConfig(
                id = 1L,
                githubToken = "ghp_xxx",
                model = "gpt-4",
                language = "de",
                availableModels = "gpt-4,gpt-5",
                aiProvider = "openai",
                ollamaUrl = "http://localhost:11434",
                ollamaNumCtx = 65536,
                lmStudioUrl = "http://localhost:1234",
                openAiApiKey = "sk-xxx",
                openAiReasoningEffort = "high",
                openRouterApiKey = "sk-or-xxx",
            )
        assertEquals("ghp_xxx", c.githubToken)
        assertEquals("gpt-4", c.model)
        assertEquals("de", c.language)
        assertEquals("gpt-4,gpt-5", c.availableModels)
        assertEquals("openai", c.aiProvider)
        assertEquals("http://localhost:11434", c.ollamaUrl)
        assertEquals(65536, c.ollamaNumCtx)
        assertEquals("http://localhost:1234", c.lmStudioUrl)
        assertEquals("sk-xxx", c.openAiApiKey)
        assertEquals("high", c.openAiReasoningEffort)
        assertEquals("sk-or-xxx", c.openRouterApiKey)
    }

    @Test
    fun `CodeFileChange default values are UPDATE`() {
        val c = CodeFileChange()
        assertEquals("", c.path)
        assertEquals(FileChangeType.UPDATE, c.changeType)
        assertEquals("", c.code)
    }

    @Test
    fun `CodeFileChange supports CREATE changeType`() {
        val c = CodeFileChange(path = "src/Foo.kt", changeType = FileChangeType.CREATE, code = "class Foo {}")
        assertEquals("src/Foo.kt", c.path)
        assertEquals(FileChangeType.CREATE, c.changeType)
        assertEquals("class Foo {}", c.code)
    }

    @Test
    fun `CodeFinding default severity is MEDIUM`() {
        val f = CodeFinding()
        assertEquals("MEDIUM", f.severity)
    }

    @Test
    fun `CodeFinding default id file type description suggestion are empty`() {
        val f = CodeFinding()
        assertEquals("", f.id)
        assertEquals("", f.file)
        assertEquals("", f.type)
        assertEquals("", f.description)
        assertEquals("", f.suggestion)
    }

    @Test
    fun `CodeFinding optional fields default to null`() {
        val f = CodeFinding()
        assertNull(f.line)
        assertNull(f.snippet)
        assertNull(f.suggestedCode)
    }

    @Test
    fun `CodeFinding can store all fields including suggestedCode`() {
        val f =
            CodeFinding(
                id = "FND-1",
                file = "src/A.kt",
                type = "DUPLICATE_CODE",
                severity = "HIGH",
                line = 42,
                snippet = "fun foo() {}",
                description = "Duplicates bar()",
                suggestion = "Extract to top-level function",
                suggestedCode = "fun fooBar() {}",
            )
        assertEquals("FND-1", f.id)
        assertEquals("src/A.kt", f.file)
        assertEquals("DUPLICATE_CODE", f.type)
        assertEquals("HIGH", f.severity)
        assertEquals(42, f.line)
        assertEquals("fun foo() {}", f.snippet)
        assertEquals("Extract to top-level function", f.suggestion)
        assertEquals("fun fooBar() {}", f.suggestedCode)
    }

    @Test
    fun `CreatedJiraTicket stores key id and url`() {
        val t = CreatedJiraTicket(key = "PROJ-1", id = "10001", url = "https://jira/PROJ-1")
        assertEquals("PROJ-1", t.key)
        assertEquals("10001", t.id)
        assertEquals("https://jira/PROJ-1", t.url)
    }

    @Test
    fun `GithubRepoConfig default id is zero and active is true`() {
        val c = GithubRepoConfig(name = "n", owner = "o", repo = "r")
        assertEquals(0L, c.id)
        assertTrue(c.active)
    }

    @Test
    fun `GithubRepoConfig default optional fields are null`() {
        val c = GithubRepoConfig(name = "n", owner = "o", repo = "r")
        assertNull(c.branch)
        assertNull(c.accessToken)
        assertNull(c.apiBase)
    }

    @Test
    fun `GithubRepoConfig stores required fields`() {
        val c = GithubRepoConfig(name = "my-repo", owner = "acme", repo = "service")
        assertEquals("my-repo", c.name)
        assertEquals("acme", c.owner)
        assertEquals("service", c.repo)
    }

    @Test
    fun `GithubRepoConfig can store all fields`() {
        val c =
            GithubRepoConfig(
                id = 5L,
                name = "n",
                owner = "o",
                repo = "r",
                branch = "develop",
                accessToken = "ghp_xxx",
                apiBase = "https://github.example.com/api/v3",
                active = false,
            )
        assertEquals(5L, c.id)
        assertEquals("develop", c.branch)
        assertEquals("ghp_xxx", c.accessToken)
        assertEquals("https://github.example.com/api/v3", c.apiBase)
        assertFalse(c.active)
    }

    @Test
    fun `GrafanaConfig default id is zero and active is true`() {
        val c = GrafanaConfig(name = "g", baseUrl = "https://g", username = "u", password = "p")
        assertEquals(0L, c.id)
        assertTrue(c.active)
    }

    @Test
    fun `GrafanaConfig default orgId is 1 and scanPanelQueries is true`() {
        val c = GrafanaConfig(name = "g", baseUrl = "https://g", username = "u", password = "p")
        assertEquals(1L, c.orgId)
        assertTrue(c.scanPanelQueries)
    }

    @Test
    fun `GrafanaConfig default apiToken is empty string`() {
        val c = GrafanaConfig(name = "g", baseUrl = "https://g", username = "u", password = "p")
        assertEquals("", c.apiToken)
    }

    @Test
    fun `GrafanaConfig stores required fields`() {
        val c =
            GrafanaConfig(
                name = "prod",
                baseUrl = "https://grafana.example.com",
                username = "admin",
                password = "secret",
            )
        assertEquals("prod", c.name)
        assertEquals("https://grafana.example.com", c.baseUrl)
        assertEquals("admin", c.username)
        assertEquals("secret", c.password)
    }

    @Test
    fun `JiraTicketSummary default id is zero and status is PENDING`() {
        val s = JiraTicketSummary(ticketKey = "PROJ-1")
        assertEquals(0L, s.id)
        assertEquals("PENDING", s.status)
    }

    @Test
    fun `JiraTicketSummary stores required ticketKey`() {
        val s = JiraTicketSummary(ticketKey = "PROJ-42")
        assertEquals("PROJ-42", s.ticketKey)
    }

    @Test
    fun `JiraTicketSummary optional fields default to null`() {
        val s = JiraTicketSummary(ticketKey = "PROJ-1")
        assertNull(s.jiraConfigId)
        assertNull(s.templateOverride)
        assertNull(s.currentStep)
        assertNull(s.errorMessage)
        assertNull(s.originalText)
        assertNull(s.summaryMarkdown)
        assertNull(s.summaryHtml)
        assertNull(s.completedAt)
    }

    @Test
    fun `JiraTicketSummary createdAt defaults to current time`() {
        val before = LocalDateTime.now().minusSeconds(1)
        val s = JiraTicketSummary(ticketKey = "PROJ-1")
        val after = LocalDateTime.now().plusSeconds(1)
        assertNotNull(s.createdAt)
        assertTrue(s.createdAt.isAfter(before) && s.createdAt.isBefore(after))
    }

    @Test
    fun `JiraTicketSummary can store all fields`() {
        val now = LocalDateTime.now()
        val s =
            JiraTicketSummary(
                id = 1L,
                ticketKey = "PROJ-1",
                jiraConfigId = 2L,
                templateOverride = "tmpl",
                status = "COMPLETED",
                currentStep = "summarised",
                errorMessage = null,
                originalText = "orig",
                summaryMarkdown = "# Summary",
                summaryHtml = "<h1>Summary</h1>",
                createdAt = now,
                completedAt = now,
            )
        assertEquals(1L, s.id)
        assertEquals(2L, s.jiraConfigId)
        assertEquals("tmpl", s.templateOverride)
        assertEquals("COMPLETED", s.status)
        assertEquals("summarised", s.currentStep)
        assertEquals("orig", s.originalText)
        assertEquals("# Summary", s.summaryMarkdown)
        assertEquals("<h1>Summary</h1>", s.summaryHtml)
        assertEquals(now, s.completedAt)
    }

    @Test
    fun `JwtAnalysisRecord default id is zero`() {
        val r = JwtAnalysisRecord(token = "tok", status = "VALID")
        assertEquals(0L, r.id)
    }

    @Test
    fun `JwtAnalysisRecord stores required token and status`() {
        val r = JwtAnalysisRecord(token = "abc.def.ghi", status = "VALID")
        assertEquals("abc.def.ghi", r.token)
        assertEquals("VALID", r.status)
    }

    @Test
    fun `JwtAnalysisRecord optional claim fields default to null`() {
        val r = JwtAnalysisRecord(token = "tok", status = "VALID")
        assertNull(r.jwtId)
        assertNull(r.audience)
        assertNull(r.issuer)
        assertNull(r.subject)
    }

    @Test
    fun `JwtAnalysisRecord createdAt defaults to current time`() {
        val before = LocalDateTime.now().minusSeconds(1)
        val r = JwtAnalysisRecord(token = "tok", status = "VALID")
        val after = LocalDateTime.now().plusSeconds(1)
        assertNotNull(r.createdAt)
        assertTrue(r.createdAt.isAfter(before) && r.createdAt.isBefore(after))
    }

    @Test
    fun `JwtAnalysisRecord supports status EXPIRED and UNLIMITED`() {
        val expired = JwtAnalysisRecord(token = "t", status = "EXPIRED")
        assertEquals("EXPIRED", expired.status)
        val unlimited = JwtAnalysisRecord(token = "t", status = "UNLIMITED")
        assertEquals("UNLIMITED", unlimited.status)
    }

    @Test
    fun `JwtAnalysisRecord can store all claim fields`() {
        val r =
            JwtAnalysisRecord(
                id = 1L,
                token = "abc.def.ghi",
                jwtId = "jti-123",
                audience = "my-app,other-app",
                issuer = "https://issuer.example.com",
                subject = "user-42",
                status = "VALID",
            )
        assertEquals(1L, r.id)
        assertEquals("jti-123", r.jwtId)
        assertEquals("my-app,other-app", r.audience)
        assertEquals("https://issuer.example.com", r.issuer)
        assertEquals("user-42", r.subject)
    }

    @Test
    fun `LocalRepoConfig default id is zero, active is true, scope is REPO_ANALYSIS`() {
        val c = LocalRepoConfig(name = "n", path = "/p")
        assertEquals(0L, c.id)
        assertTrue(c.active)
        assertEquals("REPO_ANALYSIS", c.scope)
    }

    @Test
    fun `LocalRepoConfig stores required name and path`() {
        val c = LocalRepoConfig(name = "my-repo", path = "/home/user/repo")
        assertEquals("my-repo", c.name)
        assertEquals("/home/user/repo", c.path)
    }

    @Test
    fun `LocalRepoConfig supports CODE_ANALYSIS scope`() {
        val c = LocalRepoConfig(name = "n", path = "/p", scope = "CODE_ANALYSIS")
        assertEquals("CODE_ANALYSIS", c.scope)
    }

    @Test
    fun `LocalRepoConfig can store all fields`() {
        val c =
            LocalRepoConfig(
                id = 7L,
                name = "n",
                path = "/p",
                active = false,
                scope = "CODE_ANALYSIS",
            )
        assertEquals(7L, c.id)
        assertEquals("n", c.name)
        assertEquals("/p", c.path)
        assertFalse(c.active)
        assertEquals("CODE_ANALYSIS", c.scope)
    }

    @Test
    fun `ReviewAnalysis default id is zero and status is PENDING`() {
        val r = ReviewAnalysis(jiraSprintUrl = "https://jira/board/1")
        assertEquals(0L, r.id)
        assertEquals("PENDING", r.status)
    }

    @Test
    fun `ReviewAnalysis default analysisInstructions is empty string`() {
        val r = ReviewAnalysis(jiraSprintUrl = "https://jira/board/1")
        assertEquals("", r.analysisInstructions)
    }

    @Test
    fun `ReviewAnalysis stores required jiraSprintUrl`() {
        val r = ReviewAnalysis(jiraSprintUrl = "https://jira.example.com/secure/RapidBoard.jspa?rapidView=1")
        assertEquals("https://jira.example.com/secure/RapidBoard.jspa?rapidView=1", r.jiraSprintUrl)
    }

    @Test
    fun `ReviewAnalysis optional fields default to null`() {
        val r = ReviewAnalysis(jiraSprintUrl = "https://jira/board/1")
        assertNull(r.jiraConfigId)
        assertNull(r.currentStep)
        assertNull(r.errorMessage)
        assertNull(r.sprintInfoJson)
        assertNull(r.sprintIssuesJson)
        assertNull(r.resultMarkdown)
        assertNull(r.processLogJson)
        assertNull(r.completedAt)
    }

    @Test
    fun `ReviewAnalysis can store all fields`() {
        val now = LocalDateTime.now()
        val r =
            ReviewAnalysis(
                id = 5L,
                jiraSprintUrl = "https://jira/board/1",
                jiraConfigId = 2L,
                analysisInstructions = "Focus on the blockers",
                status = "COMPLETED",
                currentStep = "done",
                errorMessage = null,
                sprintInfoJson = "{}",
                sprintIssuesJson = "[]",
                resultMarkdown = "# Review",
                processLogJson = "[\"step 1\", \"step 2\"]",
                createdAt = now,
                completedAt = now,
            )
        assertEquals(5L, r.id)
        assertEquals(2L, r.jiraConfigId)
        assertEquals("Focus on the blockers", r.analysisInstructions)
        assertEquals("COMPLETED", r.status)
        assertEquals("done", r.currentStep)
        assertEquals("{}", r.sprintInfoJson)
        assertEquals("[]", r.sprintIssuesJson)
        assertEquals("# Review", r.resultMarkdown)
        assertEquals("[\"step 1\", \"step 2\"]", r.processLogJson)
        assertEquals(now, r.completedAt)
    }

    @Test
    fun `ReviewAnalysisConfig default id is 1 (singleton)`() {
        val c = ReviewAnalysisConfig()
        assertEquals(1L, c.id)
    }

    @Test
    fun `ReviewAnalysisConfig default text fields are empty strings`() {
        val c = ReviewAnalysisConfig()
        assertEquals("", c.jiraSprintUrl)
        assertEquals("", c.analysisInstructions)
    }

    @Test
    fun `ReviewAnalysisConfig default jiraConfigId is null`() {
        val c = ReviewAnalysisConfig()
        assertNull(c.jiraConfigId)
    }

    @Test
    fun `ReviewAnalysisConfig can store all fields`() {
        val now = LocalDateTime.now()
        val c =
            ReviewAnalysisConfig(
                id = 1L,
                jiraSprintUrl = "https://jira/board/1",
                jiraConfigId = 7L,
                analysisInstructions = "Be thorough",
                updatedAt = now,
            )
        assertEquals(1L, c.id)
        assertEquals("https://jira/board/1", c.jiraSprintUrl)
        assertEquals(7L, c.jiraConfigId)
        assertEquals("Be thorough", c.analysisInstructions)
        assertEquals(now, c.updatedAt)
    }

    @Test
    fun `SqlAnalysisRecord default id is zero and status is RUNNING`() {
        val r = SqlAnalysisRecord(sql = "SELECT 1")
        assertEquals(0L, r.id)
        assertEquals("RUNNING", r.status)
    }

    @Test
    fun `SqlAnalysisRecord stores required sql`() {
        val r = SqlAnalysisRecord(sql = "SELECT id FROM users")
        assertEquals("SELECT id FROM users", r.sql)
    }

    @Test
    fun `SqlAnalysisRecord optional fields default to null`() {
        val r = SqlAnalysisRecord(sql = "SELECT 1")
        assertNull(r.ddl)
        assertNull(r.testData)
        assertNull(r.analysisResult)
    }

    @Test
    fun `SqlAnalysisRecord createdAt defaults to current time`() {
        val before = LocalDateTime.now().minusSeconds(1)
        val r = SqlAnalysisRecord(sql = "SELECT 1")
        val after = LocalDateTime.now().plusSeconds(1)
        assertNotNull(r.createdAt)
        assertTrue(r.createdAt.isAfter(before) && r.createdAt.isBefore(after))
    }

    @Test
    fun `SqlAnalysisRecord supports status COMPLETED and FAILED`() {
        val completed = SqlAnalysisRecord(sql = "SELECT 1", status = "COMPLETED")
        assertEquals("COMPLETED", completed.status)
        val failed = SqlAnalysisRecord(sql = "SELECT 1", status = "FAILED")
        assertEquals("FAILED", failed.status)
    }

    @Test
    fun `SqlAnalysisRecord can store all fields`() {
        val now = LocalDateTime.now()
        val r =
            SqlAnalysisRecord(
                id = 9L,
                sql = "SELECT * FROM t",
                ddl = "CREATE TABLE t (id INT)",
                testData = "INSERT INTO t VALUES (1)",
                analysisResult = "{}",
                status = "COMPLETED",
                createdAt = now,
            )
        assertEquals(9L, r.id)
        assertEquals("SELECT * FROM t", r.sql)
        assertEquals("CREATE TABLE t (id INT)", r.ddl)
        assertEquals("INSERT INTO t VALUES (1)", r.testData)
        assertEquals("{}", r.analysisResult)
        assertEquals("COMPLETED", r.status)
        assertEquals(now, r.createdAt)
    }
}
