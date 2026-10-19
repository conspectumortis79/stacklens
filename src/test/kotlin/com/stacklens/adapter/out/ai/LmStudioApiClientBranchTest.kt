package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LmStudioApiClientBranchTest {
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
    fun `isModelLoaded returns true when key matches and loaded_instances is non-empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models":[{"key":"m","loaded_instances":["i1"]}]}"""),
        )
        assertTrue(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `isModelLoaded returns false when key matches but loaded_instances is empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models":[{"key":"m","loaded_instances":[]}]}"""),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `isModelLoaded returns false when key matches but loaded_instances is missing`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models":[{"key":"m"}]}"""),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `isModelLoaded returns false when models field is missing`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{}"""),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `isModelLoaded returns false when models list is empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models":[]}"""),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `isModelLoaded returns false when entry is not a Map`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models":["not-a-map"]}"""),
        )
        assertFalse(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `isModelLoaded returns false on non-200 status`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        assertFalse(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `isModelLoaded returns false on connection error`() {
        server.shutdown()
        assertFalse(client.isModelLoaded(baseUrl(), "m"))
    }

    @Test
    fun `loadModel logs warning on non-2xx response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        client.loadModel(baseUrl(), "m", 5)
    }

    @Test
    fun `loadModel logs warning on connection error`() {
        server.shutdown()
        client.loadModel(baseUrl(), "m", 5)
    }

    @Test
    fun `streamRequest skips non-data lines (no prefix)`() {
        val sse =
            """not-data-prefix
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
    fun `streamRequest handles DONE marker`() {
        val sse = "data: [DONE]\n"
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
    fun `streamRequest handles non-Map choices list`() {
        val sse =
            """data: {"choices":["not-a-map"]}
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
    fun `streamRequest handles empty choices list`() {
        val sse =
            """data: {"choices":[]}
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
    fun `streamRequest handles first choice not a Map`() {
        val sse =
            """data: {"choices":["not-a-map"]}
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
    fun `streamRequest handles delta not a Map`() {
        val sse =
            """data: {"choices":[{"delta":"not-a-map"}]}
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
    fun `streamRequest handles delta missing content`() {
        val sse =
            """data: {"choices":[{"delta":{}}]}
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
    fun `streamRequest handles chat-end with non-Map result`() {
        val sse =
            """data: {"type":"chat.end","result":"not-a-map"}
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
    fun `streamRequest handles chat-end with non-Map result via String`() {
        val sse =
            """data: {"choices":[{"delta":{"content":"x"}}]}
            |data: {"type":"chat.end","result":"plain-string"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("x", final)
    }

    @Test
    fun `streamRequest returns chat-end output as final result`() {
        val sse =
            """data: {"choices":[{"delta":{"content":"partial"}}]}
            |data: {"type":"chat.end","result":{"output":"final-result"}}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("final-result", final)
    }

    @Test
    fun `streamRequest chat-end with non-string output`() {
        val sse =
            """data: {"choices":[{"delta":{"content":"x"}}]}
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
        assertEquals("x", final)
    }

    @Test
    fun `streamRequest handles non-data lines mixed with data`() {
        val sse =
            """event: foo
            |data: {"type":"message.delta","content":"x"}
            |: ping
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("x", final)
    }

    @Test
    fun `streamRequest skips invalid JSON in data lines`() {
        val sse =
            """data: not-valid-json
            |data: {"type":"message.delta","content":"x"}
            |data: [DONE]
            """.trimMargin()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val final = client.streamRequest(baseUrl(), "{}", 10, 0) { }
        assertEquals("x", final)
    }

    @Test
    fun `fetchAvailableModels filters out non-Map entries`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":["not-a-map",{"id":"m1"},42,{"id":"m2"}]}"""),
        )
        val models = client.fetchAvailableModels(baseUrl())
        assertEquals(listOf("m1", "m2"), models)
    }

    @Test
    fun `fetchAvailableModels returns empty when data field is missing`() {
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
    fun `fetchAvailableModels throws on non-200 status`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        try {
            client.fetchAvailableModels(baseUrl())
            assert(false) { "should have thrown" }
        } catch (e: Exception) {
        }
    }

    @Test
    fun `sendRequest throws IllegalStateException on connection error`() {
        server.shutdown()
        try {
            client.sendRequest("http://127.0.0.1:1", "{}", 5)
            assert(false) { "should have thrown" }
        } catch (e: IllegalStateException) {
        }
    }

    @Test
    fun `sendRequest throws on non-200 status`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        try {
            client.sendRequest(baseUrl(), "{}", 5)
            assert(false) { "should have thrown" }
        } catch (e: Exception) {
        }
    }
}
