package com.stacklens.adapter.out.ai

import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

class AiHttpClientExecutorDnsPreResolutionCoverageTest {
    private lateinit var server: MockWebServer

    private val executor = AiHttpClientExecutor(pollIntervalMillis = 5L)

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun requestTo(host: String): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(URI.create("http://$host:${server.port}/v1/chat"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

    private fun sending(client: HttpClient): HttpResponse<String> {
        val response = mock<HttpResponse<String>>()
        whenever(response.statusCode()).thenReturn(200)
        whenever(response.body()).thenReturn("{}")
        val future = CompletableFuture<HttpResponse<String>>()
        future.complete(response)
        whenever(client.sendAsync(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(future)
        return response
    }

    @Test
    fun `an unknown host is reported as a dns failure`() {
        val client = mock<HttpClient>()

        val failure =
            assertThrows(IOException::class.java) {
                executor.sendWithTimeout(client, requestTo("no-such-host.invalid"), HttpResponse.BodyHandlers.ofString(), 5L)
            }

        assertTrue(failure.message!!.startsWith("DNS pre-resolution failed for no-such-host.invalid"), failure.message)
    }

    @Test
    fun `a bracketed ipv6 literal is resolved after the brackets are removed`() {
        val client = mock<HttpClient>()
        sending(client)

        val response = executor.sendWithTimeout(client, requestTo("[::1]"), HttpResponse.BodyHandlers.ofString(), 5L, null)

        assertEquals(200, response.statusCode())
    }

    @Test
    fun `a plain hostname is resolved before the request goes out`() {
        val client = mock<HttpClient>()
        sending(client)

        assertEquals(200, executor.sendWithTimeout(client, requestTo("localhost"), HttpResponse.BodyHandlers.ofString(), 5L, null).statusCode())
    }
}
