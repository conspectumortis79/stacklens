package com.stacklens.adapter.out.grafana

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.GrafanaConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.GrafanaConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer
import org.springframework.web.reactive.function.client.WebClient

class GrafanaConnectionTestAdapterTest {
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

    private val httpErrorMessageResolver: com.stacklens.application.port.out.HttpErrorMessageResolverPort =
        com.stacklens.adapter.out.http
            .DefaultHttpErrorMessageResolver(i18n)

    private val adapter by lazy { GrafanaConnectionTestAdapter(i18n, WebClient.builder(), httpErrorMessageResolver) }

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
        useToken: Boolean = false,
    ) = GrafanaConfig(
        id = id,
        name = "Test Grafana",
        baseUrl = mockServer.url("/").toString().trimEnd('/'),
        username = if (useToken) "" else "admin",
        password = if (useToken) "" else "secret",
        apiToken = if (useToken) "myapitoken" else "",
        orgId = 1L,
        scanPanelQueries = true,
        active = true,
    )

    @Test
    fun `testConnection succeeds with basic auth and org name`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":"Main Org"}"""),
        )

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Success)
        assertTrue(result.payload["success"] as Boolean)
        assertEquals("config.be_grafana_conn_success", result.payload["message"])
        val authHeader = mockServer.takeRequest().getHeader("Authorization")
        assertTrue(authHeader!!.startsWith("Basic "))
    }

    @Test
    fun `testConnection succeeds with api token auth`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":"Token Org"}"""),
        )

        val result = adapter.testConnection(serverConfig(useToken = true))
        assertTrue(result is TestResult.Success)
        assertTrue(result.payload["success"] as Boolean)
        val authHeader = mockServer.takeRequest().getHeader("Authorization")
        assertEquals("Bearer myapitoken", authHeader)
    }

    @Test
    fun `testConnection succeeds with blank org name falls back to unknown`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":""}"""),
        )

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Success)
        assertTrue(result.payload["success"] as Boolean)
    }

    @Test
    fun `testConnection returns error on 401`() {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.AUTHENTICATION, (result as TestResult.Failure).kind)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals("config.be_grafana_auth_failed", result.payload["message"])
    }

    @Test
    fun `testConnection returns error on 403`() {
        mockServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals(ConnectionFailureKind.AUTHORIZATION, (result as TestResult.Failure).kind)
        assertEquals("config.be_grafana_access_denied", result.payload["message"])
    }

    @Test
    fun `testConnection returns error on 404`() {
        mockServer.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals(ConnectionFailureKind.NOT_FOUND, (result as TestResult.Failure).kind)
        assertEquals("config.be_endpoint_not_found_404", result.payload["message"])
    }

    @Test
    fun `testConnection returns generic error on other HTTP status`() {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        val msg = result.payload["message"] as String
        assertEquals(ConnectionFailureKind.OTHER, (result as TestResult.Failure).kind)
        assertTrue(msg.startsWith("HTTP 500"))
    }

    @Test
    fun `testConnection returns error on connection refused`() {
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        val config =
            GrafanaConfig(
                id = 1L,
                name = "T",
                baseUrl = "http://127.0.0.1:$closedPort",
                username = "admin",
                password = "secret",
                active = true,
            )

        val result = adapter.testConnection(config)
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.CONNECTION, (result as TestResult.Failure).kind)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals("config.be_conn_error", result.payload["message"])
    }

    @Test
    fun `exceptionDisplayMessage returns message when present`() {
        assertEquals("boom", adapter.exceptionDisplayMessage(RuntimeException("boom")))
    }

    @Test
    fun `exceptionDisplayMessage falls back to class name when message is null`() {
        assertEquals("RuntimeException", adapter.exceptionDisplayMessage(RuntimeException()))
    }
}
