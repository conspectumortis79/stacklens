package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

class LmStudioApiClientCoverageTest {
    private val client = LmStudioApiClient(ObjectMapper())

    private fun response(
        status: Int,
        body: Any?,
    ): HttpResponse<Any?> {
        val response = mock<HttpResponse<Any?>>()
        whenever(response.statusCode()).thenReturn(status)
        whenever(response.body()).thenReturn(body)
        return response
    }

    private fun install(vararg responses: HttpResponse<Any?>) {
        val queue = responses.toMutableList()
        val httpClient = mock<HttpClient>()
        whenever(httpClient.sendAsync<Any?>(any<HttpRequest>(), any())).thenAnswer {
            CompletableFuture.completedFuture(if (queue.isEmpty()) response(200, "") else queue.removeAt(0))
        }
        val field = LmStudioApiClient::class.java.getDeclaredField("httpClient")
        field.isAccessible = true
        field.set(client, httpClient)
    }

    private fun sseStream(vararg lines: String): InputStream = ByteArrayInputStream(lines.joinToString("\n").toByteArray())

    @Test
    fun `a model with a loaded instance counts as available`() {
        install(
            response(
                200,
                """{"models":[{"key":"other","loaded_instances":[]},{"key":"llama","loaded_instances":[{"pid":1}]},{"key":7}]}""",
            ),
        )

        assertTrue(client.isModelLoaded("http://localhost:1234", "llama"))
    }

    @Test
    fun `a model without loaded instances or with an error status is not loaded`() {
        install(response(200, """{"models":[{"key":"llama","loaded_instances":[]}]}"""))
        assertFalse(client.isModelLoaded("http://localhost:1234", "llama"))

        install(response(500, ""))
        assertFalse(client.isModelLoaded("http://localhost:1234", "llama"))

        install(response(200, "not json at all"))
        assertFalse(client.isModelLoaded("http://localhost:1234", "llama"))
    }

    @Test
    fun `loading a model warns about non success statuses and swallows transport failures`() {
        install(response(404, """{"error":"unknown model"}"""))
        client.loadModel("http://localhost:1234", "llama", 5L)

        install(response(200, "{}"))
        client.loadModel("http://localhost:1234", "llama", 5L)

        val failing = mock<HttpClient>()
        whenever(failing.sendAsync<Any?>(any<HttpRequest>(), any())).thenAnswer { throw IOException("refused") }
        val field = LmStudioApiClient::class.java.getDeclaredField("httpClient")
        field.isAccessible = true
        field.set(client, failing)

        client.loadModel("http://localhost:1234", "llama", 5L)
    }

    @Test
    fun `a streamed chat answer is accumulated and the final result wins`() {
        install(
            response(
                200,
                sseStream(
                    "keep-alive comment",
                    "data: {\"type\":\"message.delta\",\"content\":\"Hello\"}",
                    "data: [DONE]",
                    "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}",
                    "data: {\"choices\":[]}",
                    "data: {\"unrelated\":true}",
                    "data: {\"type\":\"chat.end\",\"result\":{\"output\":\"FINAL ANSWER\"}}",
                ),
            ),
        )

        val partials = mutableListOf<String>()
        val answer =
            client.streamRequest("http://localhost:1234", "{}", 5L, flushIntervalMs = 0L, onPartialOutput = { partials += it })

        assertEquals("FINAL ANSWER", answer)
        assertTrue(partials.isNotEmpty())
    }

    @Test
    fun `deltas are flushed on the configured interval and the buffer is returned otherwise`() {
        install(
            response(
                200,
                sseStream(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"partial \"}}]}",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"answer\"}}]}",
                ),
            ),
        )

        val flushed = mutableListOf<String>()
        val answer =
            client.streamRequest(
                "http://localhost:1234",
                "{}",
                5L,
                flushIntervalMs = 1L,
                isCancelled = { false },
            ) { flushed += it }

        assertEquals("partial answer", answer)
        assertTrue(flushed.size <= 2, flushed.toString())
    }

    @Test
    fun `cancelling during the stream aborts the read loop`() {
        install(
            response(
                200,
                sseStream("data: {\"type\":\"message.delta\",\"content\":\"x\"}"),
            ),
        )

        assertThrows(InterruptedException::class.java) {
            client.streamRequest("http://localhost:1234", "{}", 5L, 0L, isCancelled = { true }) {}
        }
    }

    @Test
    fun `a broken chat end payload yields the accumulated text`() {
        install(
            response(
                200,
                sseStream(
                    "data: {\"type\":\"message.delta\",\"content\":\"kept\"}",
                    "data: {\"type\":\"chat.end\",\"result\":\"not-a-map\"}",
                ),
            ),
        )

        assertEquals("kept", client.streamRequest("http://localhost:1234", "{}", 5L, 0L) {})
    }

    @Test
    fun `statuses around the success boundary are treated consistently`() {
        install(response(299, ""), response(300, "server said no"))

        client.loadModel("http://localhost:1234", "llama", 5L)
        client.loadModel("http://localhost:1234", "llama", 5L)
    }

    @Test
    fun `a failing streaming request exposes the error body`() {
        install(response(502, sseStream("upstream collapsed")))

        val failure =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                client.streamRequest("http://localhost:1234", "{}", 5L, 0L) {}
            }

        assertTrue(failure.message!!.contains("upstream collapsed"), failure.message)
    }

    @Test
    fun `a negative flush interval forces the elapsed comparison`() {
        install(
            response(
                200,
                sseStream(
                    "data: {\"type\":\"message.delta\",\"content\":\"tok \"}",
                    "data: {\"type\":\"message.delta\",\"content\":\"more\"}",
                ),
            ),
        )

        val flushed = mutableListOf<String>()
        val answer = client.streamRequest("http://localhost:1234", "{}", 5L, -1L) { flushed += it }

        assertEquals("tok more", answer)
        assertEquals(2, flushed.size)
    }

    @Test
    fun `a blank final result falls back to the accumulated deltas`() {
        install(
            response(
                200,
                sseStream(
                    "data: {\"type\":\"message.delta\",\"content\":\"kept\"}",
                    "data: {\"type\":\"chat.end\",\"result\":{\"output\":\"   \"}}",
                ),
            ),
        )

        assertEquals("kept", client.streamRequest("http://localhost:1234", "{}", 5L, 0L) {})
    }
}
