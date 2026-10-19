package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.JiraConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer

class JiraConnectionTestAdapterTest {
    private val i18n: I18nPort =
        mock(
            defaultAnswer =
                Answer { inv ->
                    when {
                        inv.method.returnType == String::class.java && inv.arguments.isNotEmpty() ->
                            inv.arguments[0] as? String ?: ""
                        else -> null
                    }
                },
        )

    private val adapter by lazy {
        JiraConnectionTestAdapter(
            ObjectMapper(),
            i18n,
            org.springframework.web.reactive.function.client.WebClient
                .builder(),
        )
    }

    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun serverConfig(
        projectKey: String = "PROJ",
        cloudMode: Boolean = true,
        username: String = "user@example.com",
    ) = JiraConfig(
        id = 1L,
        name = "Test Jira",
        baseUrl = mockServer.url("/").toString().trimEnd('/'),
        projectKey = projectKey,
        username = username,
        apiToken = "mytoken",
        cloudMode = cloudMode,
        active = true,
    )

    private fun enqueueJson(body: String) {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun issueSearchBody(
        total: Int,
        issueCount: Int = 1,
        isLast: Boolean = true,
    ) = """{"total":$total,"isLast":$isLast,"issues":[${(1..issueCount).joinToString(
        ",",
    ) { "{\"key\":\"PROJ-$it\",\"fields\":{\"summary\":\"Issue $it\"}}" }}]}"""

    @Test
    fun `cloud mode single project with total gt 0 returns Success`() {
        enqueueJson(issueSearchBody(5, 5))
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(true, result.payload["success"])
        assertEquals("3", result.payload["apiVersion"])
        assertEquals(5, result.payload["totalAccessibleIssues"])
        assertEquals("config.be_jira_conn_success", result.payload["message"])
    }

    @Test
    fun `cloud mode single project with zero total returns Success with 0 issues`() {
        enqueueJson("""{"total":0,"issues":[]}""")
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `cloud mode no total field isLast false with issues uses issue count`() {
        enqueueJson("""{"issues":[{"key":"P-1"},{"key":"P-2"}],"isLast":false}""")
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(2, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `cloud mode no total field isLast true with issues uses issue count`() {
        enqueueJson("""{"issues":[{"key":"P-1"}],"isLast":true}""")
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(1, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `cloud mode no total field and no issues returns 0`() {
        enqueueJson("""{"issues":[]}""")
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `cloud mode explicit null total uses issue count`() {
        enqueueJson("""{"total":null,"issues":[{"key":"P-1"}],"isLast":true}""")
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(1, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `cloud mode multiple project keys builds IN clause`() {
        enqueueJson(issueSearchBody(3, 3))
        val result = adapter.testConnection(serverConfig(projectKey = "PROJ, CORE"), null)
        assertTrue(result is TestResult.Success)
        assertEquals(3, result.payload["totalAccessibleIssues"])
        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("IN"))
    }

    @Test
    fun `first search endpoint invalid falls back to second`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html")
                .setBody("<html><body>Login</body></html>"),
        )
        enqueueJson(issueSearchBody(2, 2))
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(2, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `first search endpoint 404 falls back to second`() {
        mockServer.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        enqueueJson(issueSearchBody(5, 1))
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(5, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `HTML response from first endpoint falls back to second`() {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("<html>login page</html>"))
        enqueueJson(issueSearchBody(3, 1))
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(3, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `successful response with JSON issues triggers early return`() {
        enqueueJson(issueSearchBody(7, 1))
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(7, result.payload["totalAccessibleIssues"])
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun `error response that returns no body falls through to null`() {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody(""))
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody(""))
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `json without issues field falls through to next endpoint`() {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"total":0,"data":"x"}"""))
        enqueueJson(issueSearchBody(2, 1))
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(2, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `redirect status is skipped and second endpoint used`() {
        mockServer.enqueue(MockResponse().setResponseCode(302))
        enqueueJson("""{"total":0,"issues":[]}""")
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `all search endpoints fail returns Success with 0 issues`() {
        enqueueJson("""{"data":"nope"}""")
        enqueueJson("""{"data":"nope"}""")
        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `server mode uses api v2 only`() {
        enqueueJson(issueSearchBody(1))
        val result = adapter.testConnection(serverConfig(cloudMode = false), null)
        assertTrue(result is TestResult.Success)
        assertEquals("2", result.payload["apiVersion"])
        val request = mockServer.takeRequest()
        assertTrue(request.path!!.startsWith("/rest/api/2/search"))
    }

    @Test
    fun `cloud mode tries the centralized v3 jql endpoint before the v2 fallback`() {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody(""))
        enqueueJson(issueSearchBody(1))

        adapter.testConnection(serverConfig(), null)

        val first = mockServer.takeRequest()
        val second = mockServer.takeRequest()
        assertEquals(JiraEndpoints.SEARCH_PATH_V3, first.path!!.substringBefore('?'))
        assertEquals(JiraEndpoints.SEARCH_PATH_V2, second.path!!.substringBefore('?'))
    }

    @Test
    fun `cloud mode without project key probes the centralized v3 project endpoint`() {
        enqueueJson("[]")

        adapter.testConnection(serverConfig(projectKey = ""), null)

        assertEquals(JiraEndpoints.PROJECT_PATH_V3 + "?maxResults=1", mockServer.takeRequest().path)
    }

    @Test
    fun `main search invalid JSON with issues key returns 0 issues`() {
        enqueueJson("{\"issues\"")
        val result = adapter.testConnection(serverConfig(cloudMode = false), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `PAT auth with blank username works`() {
        enqueueJson(issueSearchBody(1))
        val result = adapter.testConnection(serverConfig(username = ""), null)
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `cloud mode no project key reachable array response`() {
        enqueueJson("""[{"key":"PROJ","name":"My Project"}]""")
        val result = adapter.testConnection(serverConfig(projectKey = ""), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `cloud mode no project key reachable object with key`() {
        enqueueJson("""{"key":"PROJ","name":"My Project"}""")
        val result = adapter.testConnection(serverConfig(projectKey = ""), null)
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `cloud mode no project key empty body not reachable`() {
        enqueueJson("")
        val result = adapter.testConnection(serverConfig(projectKey = ""), null)
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `cloud mode no project key blank body not reachable`() {
        enqueueJson(" ")
        val result = adapter.testConnection(serverConfig(projectKey = ""), null)
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `cloud mode no project key html body not reachable`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html")
                .setBody("<html>login</html>"),
        )
        val result = adapter.testConnection(serverConfig(projectKey = ""), null)
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `server mode no project key reachable`() {
        enqueueJson("""{"issues":[{"key":"P-1"}]}""")
        val result = adapter.testConnection(serverConfig(projectKey = "", cloudMode = false), null)
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `server mode no project key not reachable without issues key`() {
        enqueueJson("""{"data":"no-issues-key"}""")
        val result = adapter.testConnection(serverConfig(projectKey = "", cloudMode = false), null)
        assertTrue(result is TestResult.Success)
    }

    @Test
    fun `server mode no project key empty body covers defaultIfEmpty branch`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"),
        )
        val result = adapter.testConnection(serverConfig(projectKey = "", cloudMode = false), null)
        assertTrue(result is TestResult.Success)
        assertEquals(0, result.payload["totalAccessibleIssues"])
    }

    @Test
    fun `keyword as issue key pattern builds key jql`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("""{"total":1,"issues":[{"key":"PROJ-42","fields":{"summary":"Test issue"}}]}""")
        val result = adapter.testConnection(serverConfig(), "PROJ-42")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(1, kw["count"])
        assertTrue((kw["jql"] as String).startsWith("key = \"PROJ-42\""))
        assertEquals(listOf("PROJ-42: Test issue"), kw["examples"])
    }

    @Test
    fun `text keyword uses single project prefix`() {
        enqueueJson(issueSearchBody(2, 2))
        enqueueJson("""{"total":2,"issues":[{"key":"PROJ-1","fields":{"summary":"Bug fix"}},{"key":"PROJ-2","fields":{"summary":"Feature"}}]}""")
        val result = adapter.testConnection(serverConfig(), "login error")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(2, kw["count"])
        assertTrue((kw["jql"] as String).startsWith("project = \"PROJ\" AND "))
    }

    @Test
    fun `text keyword with multiple project keys uses IN prefix`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("""{"total":1,"issues":[]}""")
        val result = adapter.testConnection(serverConfig(projectKey = "PROJ, CORE"), "login")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertTrue((kw["jql"] as String).startsWith("project IN ("))
    }

    @Test
    fun `keyword with empty project key uses empty prefix`() {
        enqueueJson("""[{"key":"PROJ","name":"My Project"}]""")
        enqueueJson("""{"total":1,"issues":[{"key":"PROJ-1","fields":{"summary":"Test issue"}}]}""")
        val result = adapter.testConnection(serverConfig(projectKey = ""), "test issue")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(1, kw["count"])
        assertTrue((kw["jql"] as String).startsWith("(summary ~ "))
    }

    @Test
    fun `keyword escapes double quotes`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("""{"total":0,"issues":[]}""")
        val result = adapter.testConnection(serverConfig(), """say "hi"""")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertTrue((kw["jql"] as String).contains("\\\"hi\\\""))
    }

    @Test
    fun `keyword result without total uses issue count`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("""{"issues":[{"key":"PROJ-5","fields":{"summary":"Test"}}]}""")
        val result = adapter.testConnection(serverConfig(), "search term")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(1, kw["count"])
    }

    @Test
    fun `keyword result with explicit null total uses issue count`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("""{"total":null,"issues":[]}""")
        val result = adapter.testConnection(serverConfig(), "search term")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(0, kw["count"])
    }

    @Test
    fun `keyword search null result yields zero count and empty examples`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("""{"noIssuesField":true}""")
        enqueueJson("""{"noIssuesField":true}""")
        val result = adapter.testConnection(serverConfig(), "keyword-no-results")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(0, kw["count"])
        assertEquals(emptyList<String>(), kw["examples"])
    }

    @Test
    fun `keyword invalid JSON covers decode failure path`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("{\"issues\"")
        val result = adapter.testConnection(serverConfig(cloudMode = false), "test-keyword")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(0, kw["count"])
    }

    @Test
    fun `blank keyword omits keywordSearch`() {
        enqueueJson(issueSearchBody(1))
        val result = adapter.testConnection(serverConfig(), "  ")
        assertTrue(result is TestResult.Success)
        assertNull(result.payload["keywordSearch"])
    }

    private fun failureConfig() = serverConfig(projectKey = "")

    @Test
    fun `401 returns Failure with auth hint`() {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val result = adapter.testConnection(failureConfig(), null)
        assertTrue(result is TestResult.Failure)
        result as TestResult.Failure
        assertEquals(ConnectionFailureKind.AUTHENTICATION, result.kind)
        assertEquals(false, result.payload["success"])
        assertEquals(401, result.payload["status"])
        assertEquals("config.be_jira_auth_failed", result.payload["message"])
    }

    @Test
    fun `403 returns Failure with access denied hint`() {
        mockServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        val result = adapter.testConnection(failureConfig(), null)
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.AUTHORIZATION, (result as TestResult.Failure).kind)
        assertEquals("config.be_jira_access_denied", result.payload["message"])
    }

    @Test
    fun `404 returns Failure with endpoint not found hint`() {
        mockServer.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))
        val result = adapter.testConnection(failureConfig(), null)
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.NOT_FOUND, (result as TestResult.Failure).kind)
        assertEquals("config.be_jira_endpoint_not_found", result.payload["message"])
    }

    @Test
    fun `410 returns Failure with api removed hint`() {
        mockServer.enqueue(MockResponse().setResponseCode(410).setBody("Gone"))
        val result = adapter.testConnection(failureConfig(), null)
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.OTHER, (result as TestResult.Failure).kind)
        assertEquals("config.be_jira_api_removed", result.payload["message"])
    }

    @Test
    fun `other HTTP status returns Failure with generic hint`() {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val result = adapter.testConnection(failureConfig(), null)
        assertTrue(result is TestResult.Failure)
        result as TestResult.Failure
        assertEquals(ConnectionFailureKind.OTHER, result.kind)
        assertTrue((result.payload["message"] as String).startsWith("HTTP 500"))
    }

    @Test
    fun `connection refused returns Failure with null status`() {
        val closedUrl = mockServer.url("/").toString().trimEnd('/')
        mockServer.shutdown()
        val config =
            JiraConfig(
                id = 1L,
                name = "T",
                baseUrl = closedUrl,
                projectKey = "PROJ",
                username = "u",
                apiToken = "t",
                cloudMode = true,
                active = true,
            )
        val result = adapter.testConnection(config, null)
        assertTrue(result is TestResult.Failure)
        result as TestResult.Failure
        assertEquals(ConnectionFailureKind.CONNECTION, result.kind)
        assertEquals(false, result.payload["success"])
        assertFalse((result.payload["message"] as String).isBlank())

        mockServer = MockWebServer()
        mockServer.start()
    }

    @Test
    fun `jiraConnExceptionHint maps Connection refused`() {
        val result = jiraConnExceptionHint(RuntimeException("Connection refused to jira.example.com"), i18n)
        assertEquals("config.be_jira_conn_refused", result)
    }

    @Test
    fun `jiraConnExceptionHint maps SSL error`() {
        val result = jiraConnExceptionHint(RuntimeException("SSL handshake error"), i18n)
        assertEquals("config.be_jira_ssl_error", result)
    }

    @Test
    fun `jiraConnExceptionHint maps PKIX error`() {
        val result = jiraConnExceptionHint(RuntimeException("PKIX path building failed"), i18n)
        assertEquals("config.be_jira_ssl_error", result)
    }

    @Test
    fun `jiraConnExceptionHint maps lowercase timeout`() {
        val result = jiraConnExceptionHint(RuntimeException("connection timeout"), i18n)
        assertEquals("config.be_jira_timeout", result)
    }

    @Test
    fun `jiraConnExceptionHint maps uppercase Timeout`() {
        val result = jiraConnExceptionHint(RuntimeException("Request Timeout"), i18n)
        assertEquals("config.be_jira_timeout", result)
    }

    @Test
    fun `jiraConnExceptionHint returns raw message for unknown error`() {
        val result = jiraConnExceptionHint(RuntimeException("generic jira error"), i18n)
        assertEquals("generic jira error", result)
    }

    @Test
    fun `jiraConnExceptionHint returns i18n unknown key when message is null`() {
        val result = jiraConnExceptionHint(RuntimeException(null as String?), i18n)
        assertEquals("config.be_unknown_error", result)
    }

    @Test
    fun `keyword search response is delivered before result map assembled`() {
        enqueueJson(issueSearchBody(1))
        enqueueJson("""{"total":3,"issues":[{"key":"A-1","fields":{"summary":"S1"}},{"key":"A-2","fields":{"summary":"S2"}}]}""")
        val result = adapter.testConnection(serverConfig(), "text")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(3, kw["count"])
        assertNotNull(kw["examples"])
    }
}
