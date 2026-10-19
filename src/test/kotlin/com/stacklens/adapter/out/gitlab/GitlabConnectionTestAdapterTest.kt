package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.GitlabConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.GitlabConfig
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

class GitlabConnectionTestAdapterTest {
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

    private val adapter by lazy { GitlabConnectionTestAdapter(i18n, webClientBuilder(), httpErrorMessageResolver) }

    private fun webClientBuilder(): org.springframework.web.reactive.function.client.WebClient.Builder =
        org.springframework.web.reactive.function.client.WebClient
            .builder()

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

    private fun serverConfig(id: Long = 1L) =
        GitlabConfig(
            id = id,
            name = "Test GitLab",
            baseUrl = mockServer.url("/").toString().trimEnd('/'),
            accessToken = "mytoken",
        )

    @Test
    fun `testConnection succeeds with username and name`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"username":"jdoe","name":"John Doe"}"""),
        )

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Success)
        assertEquals(true, result.payload["success"])
        assertEquals("config.be_conn_success_user", result.payload["message"])
    }

    @Test
    fun `testConnection succeeds with blank name falls back to username`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"username":"jdoe","name":""}"""),
        )

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Success)
        assertTrue(result.payload["success"] as Boolean)
    }

    @Test
    fun `testConnection succeeds with blank username falls back to i18n unknown`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"username":"","name":""}"""),
        )

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Success)
        assertTrue(result.payload["success"] as Boolean)
    }

    @Test
    fun `testConnection returns 401 message on unauthorized`() {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.AUTHENTICATION, (result as TestResult.Failure).kind)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals("config.be_auth_failed_401", result.payload["message"])
    }

    @Test
    fun `testConnection returns 403 message on forbidden`() {
        mockServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals(ConnectionFailureKind.AUTHORIZATION, (result as TestResult.Failure).kind)
        assertEquals("config.be_access_denied_403", result.payload["message"])
    }

    @Test
    fun `testConnection returns 404 message on not found endpoint`() {
        mockServer.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))

        val result = adapter.testConnection(serverConfig())
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals(ConnectionFailureKind.NOT_FOUND, (result as TestResult.Failure).kind)
        assertEquals("config.be_endpoint_not_found_404", result.payload["message"])
    }

    @Test
    fun `testConnection returns generic error message on other HTTP error`() {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val result = adapter.testConnection(serverConfig())
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
        val config = GitlabConfig(id = 1L, name = "T", baseUrl = closedUrl, accessToken = "tok")

        val result = adapter.testConnection(config)
        assertTrue(result is TestResult.Failure)
        assertFalse(result.payload["success"] as Boolean)
        assertEquals(ConnectionFailureKind.CONNECTION, (result as TestResult.Failure).kind)
        assertEquals("config.be_conn_error", result.payload["message"])

        mockServer = MockWebServer()
        mockServer.start()
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
