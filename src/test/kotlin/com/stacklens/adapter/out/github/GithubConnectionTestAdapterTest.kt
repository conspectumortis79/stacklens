package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GithubConnectionTestAdapterTest {
    @Mock private lateinit var i18n: I18nPort

    @BeforeEach
    fun setup() {
        whenever(i18n.t(any<String>())).thenAnswer { it.arguments[0] as String }
        whenever(i18n.t(any<String>(), any())).thenAnswer { it.arguments[0] as String }
        whenever(i18n.t(any<String>(), any(), any())).thenAnswer { it.arguments[0] as String }
        whenever(i18n.t(any<String>(), any(), any(), any())).thenAnswer { it.arguments[0] as String }
    }

    private fun makeAdapter(port: Int): GithubConnectionTestAdapter {
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create(ConnectionProvider.newConnection()),
            )
        val builder =
            WebClient
                .builder()
                .clientConnector(connector)
                .baseUrl("http://localhost:$port")
        return GithubConnectionTestAdapter(builder, i18n)
    }

    private fun startServer(
        userCode: Int,
        userBody: String = """{"login":"testuser"}""",
        repoCode: Int = 200,
        repoBody: String = """{"full_name":"myorg/my-service","private":false}""",
    ): com.sun.net.httpserver.HttpServer {
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress(0), 0)
        server.createContext("/user") { ex ->
            val bytes = userBody.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(userCode, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/repos/") { ex ->
            val bytes = repoBody.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(repoCode, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    private fun config(
        owner: String = "myorg",
        repo: String = "my-service",
    ) = GithubRepoConfig(id = 1L, name = "n", owner = owner, repo = repo, active = true)

    @Test
    fun `testConnection returns Success on valid response`() {
        val server = startServer(userCode = 200, repoCode = 200)
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            val result = makeAdapter(port).testConnection(config(), "token", "http://localhost:$port")
            assertTrue(result is TestResult.Success, "was: $result")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection uses private visibility when repo is private`() {
        val server =
            startServer(
                userCode = 200,
                repoCode = 200,
                repoBody = """{"full_name":"myorg/my-service","private":true}""",
            )
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            val result = makeAdapter(port).testConnection(config(), "token", "http://localhost:$port")
            assertTrue(result is TestResult.Success, "was: $result")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection uses unknown fallback when login is blank`() {
        val server = startServer(userCode = 200, userBody = """{"login":""}""")
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            val result = makeAdapter(port).testConnection(config(), "token", "http://localhost:$port")
            assertTrue(result is TestResult.Success, "was: $result")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection uses owner-slash-repo fallback when full_name absent`() {
        val server = startServer(userCode = 200, repoCode = 200, repoBody = """{}""")
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            val result = makeAdapter(port).testConnection(config(), "token", "http://localhost:$port")
            assertTrue(result is TestResult.Success, "was: $result")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection returns Failure with 401 hint`() {
        val server = startServer(userCode = 401, userBody = """{"message":"Bad credentials"}""")
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            whenever(i18n.t("config.be_auth_failed_401")).thenReturn("auth failed 401")
            val result = makeAdapter(port).testConnection(config(), "bad-token", "http://localhost:$port")
            assertTrue(result is TestResult.Failure, "was: $result")
            assertEquals("auth failed 401", (result as TestResult.Failure).message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection returns Failure with 403 hint`() {
        val server = startServer(userCode = 403, userBody = """{"message":"Forbidden"}""")
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            whenever(i18n.t("config.be_github_access_denied")).thenReturn("access denied")
            val result = makeAdapter(port).testConnection(config(), "bad-token", "http://localhost:$port")
            assertTrue(result is TestResult.Failure, "was: $result")
            assertEquals("access denied", (result as TestResult.Failure).message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection returns Failure with 404 hint`() {
        val server = startServer(userCode = 200, repoCode = 404, repoBody = """{"message":"Not Found"}""")
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            whenever(i18n.t(eq("config.be_github_repo_not_found"), any<String>())).thenReturn("repo not found")
            val result = makeAdapter(port).testConnection(config(), "token", "http://localhost:$port")
            assertTrue(result is TestResult.Failure, "was: $result")
            assertEquals("repo not found", (result as TestResult.Failure).message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection returns Failure with generic HTTP hint for 500`() {
        val server = startServer(userCode = 500, userBody = """{"message":"Internal Server Error"}""")
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            val result = makeAdapter(port).testConnection(config(), "token", "http://localhost:$port")
            assertTrue(result is TestResult.Failure, "was: $result")
            assertTrue((result as TestResult.Failure).message.startsWith("HTTP 500"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection returns Failure with conn error on connection refused`() {
        val adapter = GithubConnectionTestAdapter(WebClient.builder(), i18n)
        val result = adapter.testConnection(config(), "token", "http://localhost:19876")
        assertTrue(result is TestResult.Failure, "was: $result")
        assertFalse((result as TestResult.Failure).message.isBlank())
    }

    @Test
    fun `testConnection handles null repoNode when body is empty`() {
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress(0), 0)
        server.createContext("/user") { ex ->
            val bytes = """{"login":"testuser"}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/repos/") { ex ->
            ex.sendResponseHeaders(200, -1)
            ex.responseBody.close()
        }
        server.start()
        try {
            val port = (server.address as java.net.InetSocketAddress).port
            val result = makeAdapter(port).testConnection(config(), "token", "http://localhost:$port")
            assertTrue(result is TestResult.Success, "was: $result")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `testConnection returns Failure using class name when exception message is null`() {
        val builder = mock<WebClient.Builder>()
        val client = mock<WebClient>()
        whenever(builder.clone()).thenReturn(builder)
        whenever(builder.baseUrl(any<String>())).thenReturn(builder)
        whenever(builder.defaultHeader(any<String>(), any<String>())).thenReturn(builder)
        whenever(builder.defaultHeader(any<String>(), any<String>(), any<String>())).thenReturn(builder)
        whenever(builder.defaultHeader(any<String>(), any<String>(), any<String>(), any<String>())).thenReturn(builder)
        whenever(builder.build()).thenReturn(client)
        whenever(client.get()).thenThrow(RuntimeException())

        val adapter = GithubConnectionTestAdapter(builder, i18n)

        val result = adapter.testConnection(config(), "token", "http://localhost:12345")

        assertTrue(result is TestResult.Failure, "was: $result")
    }
}
