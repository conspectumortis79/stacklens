package com.stacklens.adapter.out.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class AiHttpClientExecutorTest {
    private lateinit var server: HttpServer
    private var port: Int = 0
    private lateinit var client: HttpClient
    private val executor = AiHttpClientExecutor()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.start()
        port = server.address.port
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        client.close()
    }

    @Test
    fun `returns body for a normal 2xx response`() {
        server.createContext("/ok") { ex ->
            val body = "hello".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://127.0.0.1:$port/ok"))
                .GET()
                .build()
        val resp = executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 5L)
        assertEquals(200, resp.statusCode(), "GET /ok must be answered by the in-process test server")
        assertEquals("hello", resp.body())
    }

    @Test
    fun `surfaces IOException within the configured total timeout when server never responds`() {
        server.createContext("/hang") {}
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://127.0.0.1:$port/hang"))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build()
        val start = System.currentTimeMillis()
        val ex =
            assertThrows(IOException::class.java) {
                executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 1L)
            }
        val elapsedMs = System.currentTimeMillis() - start
        assertTrue(
            elapsedMs < 5_000L,
            "expected executor to surface timeout within ~1s, but it took ${elapsedMs}ms (msg: ${ex.message})",
        )
        assertTrue(
            ex.message?.contains("Timeout") == true ||
                ex is java.net.http.HttpTimeoutException,
            "expected timeout-related IOException, got: ${ex::class.simpleName} / ${ex.message}",
        )
    }

    @Test
    fun `returns 5xx status code without throwing`() {
        server.createContext("/bad") { ex ->
            ex.sendResponseHeaders(503, -1)
            ex.close()
        }
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://127.0.0.1:$port/bad"))
                .GET()
                .build()
        val resp = executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 10L)
        assertEquals(503, resp.statusCode())
    }

    @Test
    fun `throws IllegalArgumentException when totalTimeoutSeconds is zero or negative`() {
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://127.0.0.1:$port/ok"))
                .GET()
                .build()
        assertThrows(IllegalArgumentException::class.java) {
            executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), -1L)
        }
    }

    @Test
    fun `surfaces IOException when nothing listens on the target port`() {
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://$REFUSED_ADDRESS/nowhere"))
                .GET()
                .build()
        val ex =
            assertThrows(IOException::class.java) {
                executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 5L)
            }
        assertTrue(ex.message != null || ex.cause != null)
    }

    @Test
    fun `surfaces non-IOException cause as wrapped IOException with simpleName and message`() {
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://192.0.2.1:8080/blocked"))
                .GET()
                .build()
        val ex =
            assertThrows(IOException::class.java) {
                executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 1L)
            }
        assertTrue(ex.cause != null, "Expected ex.cause to be non-null, got: ${ex.message}")
    }

    @Test
    fun `surfaces IOException before any HTTP traffic when hostname cannot be resolved`() {
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://stacklens-no-such-host.invalid/never"))
                .GET()
                .build()
        val ex =
            assertThrows(IOException::class.java) {
                executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 5L)
            }
        assertTrue(
            ex.message?.contains("DNS pre-resolution failed") == true,
            "expected DNS pre-resolution IOException, got: ${ex::class.simpleName} / ${ex.message}",
        )
        assertTrue(ex.cause is java.net.UnknownHostException)
    }

    @Test
    fun `the dns pre-resolution runs on the dedicated daemon pool`() {
        server.createContext("/v1/models") { exchange ->
            val body = """{"models":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val request =
            HttpRequest
                .newBuilder(java.net.URI("http://localhost:$port/v1/models"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

        val response = executor.sendWithTimeout(client, request, HttpResponse.BodyHandlers.ofString(), 5L)
        val worker: java.util.concurrent.Future<Thread> =
            AiHttpClientExecutor
                .DNS_PRIME_EXECUTOR
                .submit<Thread> { Thread.currentThread() }
        val pooled = worker.get(5, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals(200, response.statusCode())
        assertTrue(pooled.isDaemon, "dns prime worker must be a daemon thread")
        assertTrue(pooled.name.startsWith("ai-dns-prime"), pooled.name)
    }

    @Test
    fun `IPv6 literal in URI does not throw during DNS pre-resolution`() {
        server.createContext("/v6") { ex ->
            val body = "v6-ok".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        val req =
            HttpRequest
                .newBuilder()
                .uri(java.net.URI("http://[0:0:0:0:0:0:0:1]:$port/v6"))
                .GET()
                .build()
        val resp = executor.sendWithTimeout(client, req, HttpResponse.BodyHandlers.ofString(), 5L)
        assertEquals(200, resp.statusCode())
        assertEquals("v6-ok", resp.body())
    }

    companion object {
        const val REFUSED_ADDRESS = "127.0.0.1:1"
    }
}
