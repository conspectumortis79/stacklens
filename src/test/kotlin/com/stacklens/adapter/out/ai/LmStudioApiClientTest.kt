package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ConnectException

class LmStudioApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: LmStudioApiClient
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = LmStudioApiClient(mapper)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().removeSuffix("/")

    @Test
    fun `isModelLoaded returns true when model is loaded`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"models":[{"key":"x","loaded_instances":[{}]}]}""",
                ),
        )
        assertTrue(client.isModelLoaded(baseUrl(), "x"))
    }

    @Test
    fun `isModelLoaded returns false when model not loaded`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models":[]}"""),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "missing"))
    }

    @Test
    fun `isModelLoaded returns false on non-200`() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(client.isModelLoaded(baseUrl(), "x"))
    }

    @Test
    fun `isModelLoaded returns false on connection error`() {
        server.shutdown()
        assertFalse(client.isModelLoaded("http://127.0.0.1:1", "x"))
    }

    @Test
    fun `loadModel issues POST to load endpoint`() {
        server.enqueue(MockResponse().setResponseCode(200))
        client.loadModel(baseUrl(), "test-model", 5)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue((recorded.path.orEmpty()).contains("/api/v1/models/load"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("test-model"))
    }

    @Test
    fun `loadModel logs warning on non-2xx`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("internal error"),
        )
        client.loadModel(baseUrl(), "x", 5)
    }

    @Test
    fun `loadModel tolerates connection failures`() {
        server.shutdown()
        client.loadModel("http://127.0.0.1:1", "x", 5)
    }

    @Test
    fun `sendRequest returns body on 200`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"hi"}}]}"""),
        )
        val body = client.sendRequest(baseUrl(), """{"prompt":"hi"}""", 10)
        assertEquals("""{"choices":[{"message":{"content":"hi"}}]}""", body)
    }

    @Test
    fun `sendRequest throws on non-200`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        assertThrows(IllegalStateException::class.java) {
            client.sendRequest(baseUrl(), "{}", 10)
        }
    }

    @Test
    fun `sendRequest wraps ConnectException as IllegalStateException`() {
        server.shutdown()
        assertThrows(IllegalStateException::class.java) {
            client.sendRequest("http://127.0.0.1:1", "{}", 10)
        }
    }

    @Test
    fun `streamRequest collects SSE chunks with delta content`() {
        val sse =
            """data: {"type":"message.delta","content":"hello"}
            |data: {"type":"message.delta","content":" world"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val collected = mutableListOf<String>()
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { collected.add(it) }
        assertEquals("hello world", final)
        assertTrue(collected.isNotEmpty())
    }

    @Test
    fun `streamRequest returns chat-end output when present`() {
        val sse =
            """data: {"type":"chat.end","result":{"output":"final text"}}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("final text", final)
    }

    @Test
    fun `streamRequest returns empty when no usable data`() {
        val sse =
            """data: {"type":"message.start"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("", final)
    }

    @Test
    fun `streamRequest handles non-200 with error body`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("err"),
        )
        assertThrows(IllegalStateException::class.java) {
            client.streamRequest(baseUrl(), "{}", 10, 0) { }
        }
    }

    @Test
    fun `streamRequest tolerates flush interval threshold`() {
        val sse =
            """data: {"type":"message.delta","content":"x"}
            |data: {"type":"message.delta","content":"y"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val collected = mutableListOf<String>()
        val final = client.streamRequest(baseUrl(), "{}", 10, 999_999) { collected.add(it) }
        assertEquals("xy", final)
        assertTrue(collected.isEmpty())
    }

    @Test
    fun `streamRequest handles chat-end with non-map result`() {
        val sse =
            """data: {"choices":[{"delta":{"content":"a"}}]}
            |data: {"type":"chat.end","result":"not-a-map"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("a", final)
    }

    @Test
    fun `streamRequest handles chat-end with output as non-string`() {
        val sse =
            """data: {"choices":[{"delta":{"content":"a"}}]}
            |data: {"type":"chat.end","result":{"output":42}}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("a", final)
    }

    @Test
    fun `streamRequest handles message delta with non-string content`() {
        val sse =
            """data: {"type":"message.delta","content":42}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("", final)
    }

    @Test
    fun `streamRequest handles message delta with choices fallback when content missing`() {
        val sse =
            """data: {"choices":[{"delta":{"content":"fallback"}}]}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("fallback", final)
    }

    @Test
    fun `streamRequest handles message delta with non-map delta`() {
        val sse =
            """data: {"type":"message.delta","choices":[{"delta":"not-a-map"}]}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("", final)
    }

    @Test
    fun `isModelLoaded returns false when model list contains non-Map entries`() {
        val data = """{"data": ["not-a-map", {"id": "foo"}, {"id": "bar", "loaded_instances": ["x"]}]}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(data),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "foo"))
    }

    @Test
    fun `isModelLoaded returns false when loaded_instances is missing`() {
        val data = """{"data": [{"id": "foo"}]}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(data),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "foo"))
    }

    @Test
    fun `streamRequest falls back to OpenAI choices when no chat-end`() {
        val sse =
            """data: {"choices":[{"delta":{"content":"a"}}]}
            |data: {"choices":[{"delta":{"content":"b"}}]}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("ab", final)
    }

    @Test
    fun `streamRequest ignores malformed JSON lines`() {
        val sse =
            """data: not json
            |data: {"type":"message.delta","content":"ok"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("ok", final)
    }

    @Test
    fun `streamRequest wraps ConnectException`() {
        server.shutdown()
        assertThrows(IllegalStateException::class.java) {
            client.streamRequest("http://127.0.0.1:1", "{}", 10, 0) { }
        }
    }

    @Test
    fun `fetchAvailableModels returns list of ids`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"model-a"},{"id":"model-b"}]}"""),
        )
        val models = client.fetchAvailableModels(baseUrl())
        assertEquals(listOf("model-a", "model-b"), models)
    }

    @Test
    fun `fetchAvailableModels returns empty when data missing`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{}"""),
        )
        val models = client.fetchAvailableModels(baseUrl())
        assertTrue(models.isEmpty())
    }

    @Test
    fun `fetchAvailableModels throws on non-200`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        assertThrows(IllegalStateException::class.java) {
            client.fetchAvailableModels(baseUrl())
        }
    }

    @Test
    fun `fetchAvailableModels skips entries without id`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"a"},{"other":"x"},{"id":null},{}]}"""),
        )
        val models = client.fetchAvailableModels(baseUrl())
        assertEquals(listOf("a"), models)
    }

    @Test
    fun `streamRequest flushes on each delta when flushIntervalMs=0`() {
        val sse =
            """data: {"type":"message.delta","content":"a"}
            |data: {"type":"message.delta","content":"b"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val collected = mutableListOf<String>()
        client.streamRequest(baseUrl(), "{}", 10, 0) { collected.add(it) }
        assertNotNull(collected)
    }

    @Test
    fun `connectException is captured`() {
        server.shutdown()
        try {
            client.sendRequest("http://127.0.0.1:1", "{}", 5)
            org.junit.jupiter.api.Assertions
                .fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertNotNull(e.cause)
            assertTrue(e.cause is ConnectException || e.message!!.contains("LM Studio is not reachable"))
        }
    }
}
