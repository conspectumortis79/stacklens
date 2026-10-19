package com.stacklens.adapter.out.confluence

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ConfluenceConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.ConfluenceConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer
import org.springframework.web.reactive.function.client.WebClient

class ConfluenceConnectionTestAdapterTest {
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

    private val adapter by lazy { ConfluenceConnectionTestAdapter(i18n, WebClient.builder()) }

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
        id: Long = 1L,
        cloudMode: Boolean = true,
    ) = ConfluenceConfig(
        id = id,
        name = "Test Confluence",
        baseUrl = mockServer.url("/").toString().trimEnd('/'),
        username = "user@example.com",
        apiToken = "mytoken",
        spaceKeys = null,
        cloudMode = cloudMode,
        active = true,
    )

    @Test
    fun `testConnection succeeds with displayName`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"Alice Smith","username":"alice"}"""),
        )

        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertTrue(result.payload["success"] as Boolean)
        assertEquals("Alice Smith", result.payload["user"])
        assertEquals("config.be_conn_success_user", result.payload["message"])
    }

    @Test
    fun `testConnection succeeds with blank displayName falls back to username`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"","username":"alice"}"""),
        )

        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals("alice", result.payload["user"])
    }

    @Test
    fun `testConnection succeeds with both blank falls back to unknown`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"","username":""}"""),
        )

        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Success)
        assertEquals("config.be_unknown", result.payload["user"])
    }

    @Test
    fun `testConnection succeeds with keyword search (totalSize)`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"Alice"}"""),
        )
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """{"totalSize":3,"results":[{"title":"Page A","space":{"key":"SP1"}}]}""",
                ),
        )

        val result = adapter.testConnection(serverConfig(), "some keyword")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(3, kw["count"])
        assertEquals(listOf("Page A (SP1)"), kw["examples"])
    }

    @Test
    fun `testConnection succeeds with keyword search (results array)`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"Alice"}"""),
        )
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """{"results":[{"title":"Page B","space":{"key":"SP2"}},{"title":"Page C","space":{"key":""}}]}""",
                ),
        )

        val result = adapter.testConnection(serverConfig(), "test")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(2, kw["count"])
        assertEquals(listOf("Page B (SP2)", "Page C (?)"), kw["examples"])
    }

    @Test
    fun `testConnection returns error on 401`() {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.AUTHENTICATION, (result as TestResult.Failure).kind)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals("config.be_confluence_auth_failed", result.payload["message"])
        assertEquals(401, result.payload["status"])
    }

    @Test
    fun `testConnection returns error on 403`() {
        mockServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals(ConnectionFailureKind.AUTHORIZATION, (result as TestResult.Failure).kind)
        assertEquals("config.be_confluence_access_denied", result.payload["message"])
    }

    @Test
    fun `testConnection returns error on 404`() {
        mockServer.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))

        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals(ConnectionFailureKind.NOT_FOUND, (result as TestResult.Failure).kind)
        assertEquals("config.be_endpoint_not_found_404", result.payload["message"])
    }

    @Test
    fun `testConnection returns generic error on other HTTP status`() {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val result = adapter.testConnection(serverConfig(), null)
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        val msg = result.payload["message"] as String
        assertEquals(ConnectionFailureKind.OTHER, (result as TestResult.Failure).kind)
        assertTrue(msg.startsWith("HTTP 500"))
    }

    @Test
    fun `testConnection returns error on connection refused`() {
        val closedUrl = mockServer.url("/").toString().trimEnd('/')
        mockServer.shutdown()
        val config =
            ConfluenceConfig(
                id = 1L,
                name = "T",
                baseUrl = closedUrl,
                username = "u",
                apiToken = "t",
                cloudMode = true,
                active = true,
            )

        val result = adapter.testConnection(config, null)
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.CONNECTION, (result as TestResult.Failure).kind)
        assertFalse(result.payload["success"] as Boolean)

        mockServer = MockWebServer()
        mockServer.start()
    }

    @Test
    fun `testConnection server mode uses non-wiki path`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"Bob","username":"bob"}"""),
        )

        val result = adapter.testConnection(serverConfig(cloudMode = false), null)
        assertTrue(result is TestResult.Success)
        assertTrue((result.payload["url"] as String).endsWith("/rest/api/user/current"))
        assertFalse((result.payload["url"] as String).contains("/wiki/"))
    }

    @Test
    fun `testConnection succeeds with null user body falls back to unknown`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"),
        )

        val result = adapter.testConnection(serverConfig(), null)
        assertNotNull(result.payload)
    }

    @Test
    fun `testConnection keyword search error triggers onStatus handler`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"Alice"}"""),
        )
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("Content-Type", "application/json")
                .setBody("Unauthorized keyword search"),
        )

        val result = adapter.testConnection(serverConfig(), "test-keyword")
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
    }

    @Test
    fun `testConnection keyword search null body covers emptyList fallback`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"Alice"}"""),
        )
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"),
        )

        val result = adapter.testConnection(serverConfig(), "test-keyword")
        assertTrue(result is TestResult.Success)
        val kw = result.payload["keywordSearch"] as Map<*, *>
        assertEquals(0, kw["count"])
        assertEquals(emptyList<String>(), kw["examples"])
    }

    @Test
    fun `testConnection blank keyword treated as no keyword`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"displayName":"Alice"}"""),
        )

        val result = adapter.testConnection(serverConfig(), "   ")
        assertTrue(result is TestResult.Success)
        assertFalse(result.payload.containsKey("keywordSearch"))
    }

    @Test
    fun `confluenceConnExceptionHint maps Connection refused to refused key`() {
        val e = RuntimeException("Connection refused: localhost:8090")
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("config.be_confluence_conn_refused", result)
    }

    @Test
    fun `confluenceConnExceptionHint maps SSL to ssl error key`() {
        val e = RuntimeException("SSL handshake failed")
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("config.be_confluence_ssl_error", result)
    }

    @Test
    fun `confluenceConnExceptionHint maps PKIX to ssl error key`() {
        val e = RuntimeException("PKIX path building failed")
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("config.be_confluence_ssl_error", result)
    }

    @Test
    fun `confluenceConnExceptionHint maps internal_error to tls error key`() {
        val e = RuntimeException("received fatal alert internal_error")
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("config.be_confluence_ssl_tls_error", result)
    }

    @Test
    fun `confluenceConnExceptionHint maps fatal alert to tls error key`() {
        val e = RuntimeException("received fatal alert: handshake_failure")
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("config.be_confluence_ssl_tls_error", result)
    }

    @Test
    fun `confluenceConnExceptionHint maps timeout to timeout key`() {
        val e = RuntimeException("Read timeout exceeded")
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("config.be_confluence_timeout", result)
    }

    @Test
    fun `confluenceConnExceptionHint returns raw message for unknown exception`() {
        val e = RuntimeException("some unexpected problem")
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("some unexpected problem", result)
    }

    @Test
    fun `confluenceConnExceptionHint returns i18n unknown key when message is null`() {
        val e = object : Exception(null as String?) {}
        val result = confluenceConnExceptionHint(e, i18n)
        assertEquals("config.be_unknown_error", result)
    }
}
