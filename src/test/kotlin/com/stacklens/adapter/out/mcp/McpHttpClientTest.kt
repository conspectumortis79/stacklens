package com.stacklens.adapter.out.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
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
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.Base64

class McpHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var databaseConfigs: DatabaseConfigPort
    private val objectMapper = ObjectMapper()

    private lateinit var listAppender: ListAppender<ILoggingEvent>
    private lateinit var mcpLogger: Logger
    private val breaker = McpCircuitBreaker()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        databaseConfigs = mock()

        mcpLogger = LoggerFactory.getLogger(McpHttpClient::class.java) as Logger
        listAppender = ListAppender<ILoggingEvent>().also { it.start() }
        mcpLogger.addAppender(listAppender)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        mcpLogger.detachAppender(listAppender)
        listAppender.stop()
    }

    private fun fallbackHints(): List<ILoggingEvent> =
        listAppender.list.filter { event ->
            event.level == Level.WARN && event.formattedMessage.contains("Native fallback engaged")
        }

    private fun createClient(
        config: McpServerConfig = defaultConfig(),
        databases: List<DatabaseConfig> = emptyList(),
    ): McpHttpClient {
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(databases)
        return McpHttpClient(
            webClientBuilder = WebClient.builder(),
            objectMapper = objectMapper,
            databaseConfigs = databaseConfigs,
            circuitBreaker = breaker,
        ).also { }
    }

    private fun defaultConfig() =
        McpServerConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            authToken = "",
        )

    @Test
    fun `resolveBaseUrl normalizes localhost to IPv4 loopback when no docker host`() {
        val client = createClient()
        assertEquals("http://127.0.0.1:7456", client.resolveBaseUrl("http://localhost:7456"))
    }

    @Test
    fun `resolveBaseUrl normalizes IPv6 loopback to IPv4 loopback when no docker host`() {
        val client = createClient()
        assertEquals("http://127.0.0.1:7456", client.resolveBaseUrl("http://[::1]:7456"))
    }

    @Test
    fun `resolveBaseUrl keeps loopback as loopback even when docker host set`() {
        val client = createClient()
        val resolved = client.resolveBaseUrl("http://localhost:7456")
        assertEquals("http://127.0.0.1:7456", resolved)
    }

    @Test
    fun `resolveBaseUrl treats blank docker host as no docker host`() {
        val client = createClient()
        assertEquals("http://127.0.0.1:7456", client.resolveBaseUrl("http://localhost:7456"))
    }

    @Test
    fun `resolveBaseUrl leaves non-loopback URLs untouched when no docker host`() {
        val client = createClient()
        assertEquals("http://example.com:7456", client.resolveBaseUrl("http://example.com:7456"))
    }

    @Test
    fun `resolveBaseUrl leaves non-loopback URLs untouched even with docker host`() {
        val client = createClient()
        val resolved = client.resolveBaseUrl("http://example.com:7456")
        assertEquals("http://example.com:7456", resolved)
    }

    @Test
    fun `resolveBaseUrl preserves the docker-compose service name stacklens-mcp`() {
        val client = createClient()
        val resolved = client.resolveBaseUrl("http://stacklens-mcp:7456")
        assertEquals("http://stacklens-mcp:7456", resolved)
    }

    @Test
    fun `listTools returns empty when body is null`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val tools = createClient().listTools(defaultConfig())
        assertTrue(tools.isEmpty())
    }

    @Test
    fun `listTools returns empty when tools field is missing`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("{}").setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val tools = createClient().listTools(defaultConfig())
        assertTrue(tools.isEmpty())
    }

    @Test
    fun `listTools returns empty when tools field is not an array`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"tools":"not-an-array"}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val tools = createClient().listTools(defaultConfig())
        assertTrue(tools.isEmpty())
    }

    @Test
    fun `listTools parses tools from response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {"tools":[
                      {"name":"jira_create","description":"Create ticket"},
                      {"name":"gitlab_list","description":"List projects"}
                    ]}
                    """.trimIndent(),
                ).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val tools = createClient().listTools(defaultConfig())
        assertEquals(2, tools.size)
        assertEquals("jira_create", tools[0].name)
        assertEquals("Create ticket", tools[0].description)
        assertEquals("gitlab_list", tools[1].name)
    }

    @Test
    fun `listTools uses empty string defaults for missing fields`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"tools":[{"name":"only_name"}]}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val tools = createClient().listTools(defaultConfig())
        assertEquals(1, tools.size)
        assertEquals("only_name", tools[0].name)
        assertEquals("", tools[0].description)
    }

    @Test
    fun `callTool returns text from result content array`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"result":{"isError":false,"content":[{"text":"hello"}]}}""",
                ).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        assertEquals("hello", result.text)
        assertFalse(result.isError)
    }

    @Test
    fun `callTool joins multiple content items with newline`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"result":{"isError":false,"content":[{"text":"line1"},{"text":"line2"}]}}""",
                ).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertEquals("line1\nline2", result.text)
    }

    @Test
    fun `callTool propagates isError from result envelope`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"result":{"isError":true,"content":[{"text":"oops"}]}}""",
                ).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertTrue(result.isError)
        assertEquals("oops", result.text)
    }

    @Test
    fun `callTool returns empty-result error when body is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertTrue(result.isError)
        assertEquals("(empty)", result.text)
    }

    @Test
    fun `callTool wraps WebClientResponseException into error result`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("server error"),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertTrue(result.isError)
        assertTrue(result.text.contains("500"))
        assertTrue(result.text.contains("server error"))
    }

    @Test
    fun `callTool wraps generic exception into error result`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not-json")
                .setHeader(HttpHeaders.CONTENT_TYPE, "text/plain"),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertTrue(result.isError)
        assertTrue(result.text.contains("tool"))
    }

    @Test
    fun `callTool returns raw JSON text when result content is missing and no error envelope`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result":{"other":"data"}}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertFalse(result.isError)
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun `callTool returns error envelope text when content array is empty and isError true`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result":{"isError":true,"content":[]}}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertTrue(result.isError)
    }

    @Test
    fun `callTool returns direct error envelope text`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"error":"direct failure"}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "tool", emptyMap())
        assertTrue(result.isError)
        assertEquals("direct failure", result.text)
    }

    @Test
    fun `callTool emits a single fallback hint on the first failure of a circuit`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val result = createClient().callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        assertTrue(result.isError)
        val hints = fallbackHints()
        assertEquals(1, hints.size, "exactly one fallback hint must be emitted on first failure")
        val msg = hints.first().formattedMessage
        assertTrue(msg.contains("jira"), "hint should name the affected provider: $msg")
        assertTrue(msg.contains("1:jira"), "hint should name the affected circuit: $msg")
        assertTrue(msg.contains("jira_create"), "hint should name the affected tool: $msg")
    }

    @Test
    fun `callTool does NOT re-emit fallback hint on subsequent failures of the same circuit`() {
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        }
        val client = createClient()
        client.callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        client.callTool(defaultConfig(), "jira_create", mapOf("key" to "X-2"))
        assertEquals(1, fallbackHints().size, "the second failure must not re-emit the hint")
    }

    @Test
    fun `callTool re-emits fallback hint after the circuit recovers and a new failure occurs`() {
        val client = createClient()
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        client.callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        assertEquals(1, fallbackHints().size)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result":{"isError":false,"content":[{"text":"ok"}]}}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        client.callTool(defaultConfig(), "jira_create", mapOf("key" to "X-2"))
        assertEquals(1, fallbackHints().size, "success must not change the hint count")

        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        client.callTool(defaultConfig(), "jira_create", mapOf("key" to "X-3"))
        assertEquals(2, fallbackHints().size, "post-recovery failure must re-emit the hint")
    }

    @Test
    fun `callTool never emits fallback hint on a fully successful call`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result":{"isError":false,"content":[{"text":"ok"}]}}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        assertFalse(result.isError)
        assertTrue(fallbackHints().isEmpty())
    }

    @Test
    fun `callTool emits separate hints for separate provider circuits`() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(500).setBody("err")) }
        val client = createClient()
        client.callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        client.callTool(defaultConfig(), "gitlab_list", emptyMap())
        val hints = fallbackHints()
        assertEquals(2, hints.size, "different providers should produce two independent hints")
        assertTrue(hints.any { it.formattedMessage.contains("jira") })
        assertTrue(hints.any { it.formattedMessage.contains("gitlab") })
    }

    @Test
    fun `callTool emits hint when WebClientResponseException is thrown`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val result = createClient().callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        assertTrue(result.isError)
        assertEquals(1, fallbackHints().size)
    }

    @Test
    fun `callTool emits hint when the response body is empty`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = createClient().callTool(defaultConfig(), "jira_create", mapOf("key" to "X-1"))
        assertTrue(result.isError)
        assertEquals(1, fallbackHints().size)
    }

    @Test
    fun `ping returns ok when health endpoint reports ok`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"name":"stacklens-mcp"}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"tools":[{"name":"a"},{"name":"b"}]}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().ping(defaultConfig())
        assertTrue(result.ok)
        assertTrue(result.message.contains("OK"))
        assertEquals(2, result.toolCount)
    }

    @Test
    fun `ping returns unhealthy when ok is false`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":false}""")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        val result = createClient().ping(defaultConfig())
        assertFalse(result.ok)
        assertEquals("unhealthy", result.message)
        assertEquals(0, result.toolCount)
    }

    @Test
    fun `ping returns unhealthy when body is null`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = createClient().ping(defaultConfig())
        assertFalse(result.ok)
        assertEquals("unhealthy", result.message)
    }

    @Test
    fun `ping returns exception details on failure`() {
        server.shutdown()
        val fresh = MockWebServer()
        fresh.start()
        try {
            val config = defaultConfig().apply { baseUrl = "http://127.0.0.1:1" }
            val result = createClient().ping(config)
            assertFalse(result.ok)
            assertNotNull(result.message)
        } finally {
            fresh.shutdown()
        }
    }

    @Test
    fun `buildConfigHeader returns base64-encoded payload without databases when routeDatabase false`() {
        val client = createClient()
        val config = defaultConfig().apply { routeDatabase = false }
        val header = client.buildConfigHeader(config)
        val decoded = String(Base64.getDecoder().decode(header), Charsets.UTF_8)
        assertTrue(decoded.contains("\"databases\":[]"))
        assertTrue(decoded.contains("\"jira\""))
        assertTrue(decoded.contains("\"confluence\""))
        assertTrue(decoded.contains("\"gitlab\""))
        assertTrue(decoded.contains("\"github\""))
        assertTrue(decoded.contains("\"grafana\""))
    }

    @Test
    fun `buildConfigHeader includes databases when routeDatabase true`() {
        val db =
            DatabaseConfig(
                name = "pg",
                dbType = DatabaseType.POSTGRESQL,
                host = "h",
                port = 5432,
                databaseName = "n",
                username = "u",
                password = "p",
            )
        val client = createClient(databases = listOf(db))
        val header = client.buildConfigHeader(defaultConfig().apply { routeDatabase = true })
        val decoded = String(Base64.getDecoder().decode(header), Charsets.UTF_8)
        assertTrue(decoded.contains("\"databases\":[{"))
        assertTrue(decoded.contains("\"postgresql\""))
        assertTrue(decoded.contains("\"pg\""))
    }

    @Test
    fun `buildConfigHeader maps all database types correctly`() {
        val dbs =
            listOf(
                DatabaseConfig(
                    name = "m",
                    dbType = DatabaseType.MYSQL,
                    host = "h",
                    port = 3306,
                    databaseName = "n",
                    username = "u",
                    password = "p",
                ),
                DatabaseConfig(
                    name = "p",
                    dbType = DatabaseType.POSTGRESQL,
                    host = "h",
                    port = 5432,
                    databaseName = "n",
                    username = "u",
                    password = "p",
                ),
                DatabaseConfig(
                    name = "s",
                    dbType = DatabaseType.MSSQL,
                    host = "h",
                    port = 1433,
                    databaseName = "n",
                    username = "u",
                    password = "p",
                ),
            )
        val client = createClient(databases = dbs)
        val header = client.buildConfigHeader(defaultConfig())
        val decoded = String(Base64.getDecoder().decode(header), Charsets.UTF_8)
        assertTrue(decoded.contains("\"mysql\""))
        assertTrue(decoded.contains("\"postgresql\""))
        assertTrue(decoded.contains("\"mssql\""))
    }

    @Test
    fun `client adds Authorization header when authToken is set`() {
        val client = createClient()
        val config = defaultConfig().apply { authToken = "secret-token" }
        val webClient = client.client(config)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        webClient
            .get()
            .uri("/anything")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val received = server.takeRequest()
        assertTrue(received.getHeader("Authorization") == "Bearer secret-token")
    }

    @Test
    fun `client omits Authorization header when authToken is blank`() {
        val client = createClient()
        val webClient = client.client(defaultConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
        )
        webClient
            .get()
            .uri("/anything")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        val received = server.takeRequest()
        assertEquals(null, received.getHeader("Authorization"))
    }

    @Test
    fun `extractToolText returns raw json when content missing and no error envelope`() {
        val client = createClient()
        val node = objectMapper.readTree("""{"result":{"foo":"bar"}}""")
        val result = client.extractToolText(node)
        assertFalse(result.isError)
        assertTrue(result.text.contains("foo"))
    }

    @Test
    fun `extractToolText returns direct error text when error envelope present`() {
        val client = createClient()
        val node = objectMapper.readTree("""{"error":"something failed"}""")
        val result = client.extractToolText(node)
        assertTrue(result.isError)
        assertEquals("something failed", result.text)
    }

    @Test
    fun `extractToolText returns raw JSON when content array empty and not error`() {
        val client = createClient()
        val node = objectMapper.readTree("""{"result":{"isError":false,"content":[]}}""")
        val result = client.extractToolText(node)
        assertFalse(result.isError)
        assertTrue(result.text.contains("isError"))
    }

    @Test
    fun `extractToolText falls through to raw JSON when content text empty but isError true`() {
        val client = createClient()
        val node = objectMapper.readTree("""{"result":{"isError":true,"content":[{"text":""}]}}""")
        val result = client.extractToolText(node)
        assertTrue(result.isError)
        assertTrue(result.text.contains("isError"))
    }
}
