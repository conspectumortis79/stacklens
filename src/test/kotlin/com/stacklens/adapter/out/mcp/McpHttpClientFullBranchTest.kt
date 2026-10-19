package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.domain.model.McpServerConfig
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
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.time.Duration

class McpHttpClientFullBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var webClientBuilder: WebClient.Builder
    private lateinit var client: McpHttpClient
    private val breaker = McpCircuitBreaker()
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("").toString().removeSuffix("/")
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        webClientBuilder = WebClient.builder().clientConnector(connector)
        val dbConfigs = mock<DatabaseConfigPort>()
        client = McpHttpClient(webClientBuilder, ObjectMapper(), dbConfigs, breaker)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(
        id: Long = 1L,
        baseUrl: String = this.baseUrl,
    ): McpServerConfig =
        McpServerConfig(
            id = id,
            name = "test",
            baseUrl = baseUrl,
            authToken = "",
        )

    @Test
    fun `callTool returns error when circuit is open`() {
        val circuitKey = breaker.keyFor(1L, "tool")
        repeat(10) { breaker.recordFailure(circuitKey) }
        val result = client.callTool(config(), "tool", emptyMap())
        assertTrue(result.isError)
        assertTrue(result.text.contains("circuit open"))
    }

    @Test
    fun `callTool succeeds when circuit is allowed`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"result":{"isError":false,"content":[{"text":"ok"}]}}"""),
        )
        val result = client.callTool(config(), "tool", emptyMap())
        assertFalse(result.isError)
        assertEquals("ok", result.text)
    }

    @Test
    fun `ping returns exception message when server throws with message`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.ping(config())
        assertFalse(result.ok)
        assertNotNull(result.message)
    }

    @Test
    fun `ping returns class simpleName when client throws exception without message`() {
        val failingBuilder =
            WebClient
                .builder()
                .filter { _, _ -> Mono.error<ClientResponse>(IllegalStateException()) }
        val exMsgLess = McpHttpClient(failingBuilder, ObjectMapper(), mock<DatabaseConfigPort>(), breaker)
        val result = exMsgLess.ping(config())
        assertFalse(result.ok)
        assertEquals("IllegalStateException", result.message)
    }

    @Test
    fun `extractToolText returns joined text when content is non-blank and not error`() {
        val node =
            ObjectMapper().readTree(
                """{"result":{"isError":false,"content":[{"text":"hello"},{"text":"world"}]}}""",
            )
        val result = client.extractToolText(node)
        assertEquals("hello\nworld", result.text)
        assertFalse(result.isError)
    }

    @Test
    fun `extractToolText returns empty text when content is blank and isError is true`() {
        val node =
            ObjectMapper().readTree(
                """{"result":{"isError":true,"content":[{"text":""}]}}""",
            )
        val result = client.extractToolText(node)
        assertTrue(result.isError)
    }

    @Test
    fun `extractToolText returns error envelope when content is empty and isError is true`() {
        val node =
            ObjectMapper().readTree(
                """{"error":"something went wrong"}""",
            )
        val result = client.extractToolText(node)
        assertTrue(result.isError)
        assertEquals("something went wrong", result.text)
    }

    @Test
    fun `extractToolText returns full node when no content and no error`() {
        val node = ObjectMapper().readTree("""{"result":{"isError":false}}""")
        val result = client.extractToolText(node)
        assertFalse(result.isError)
        assertTrue(result.text.contains("isError"))
    }

    @Test
    fun `extractToolText returns blank text when isError false`() {
        val node =
            ObjectMapper().readTree(
                """{"result":{"isError":false,"content":[{"text":""}]}}""",
            )
        val result = client.extractToolText(node)
        assertFalse(result.isError)
        assertEquals("", result.text)
    }
}
