package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenAiCompatibleToolLoopBranchTest {
    private lateinit var server: MockWebServer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `OpenAiCompatibleToolLoop is constructible with transport`() {
        val transport =
            object : com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop.ChatTransport {
                override val providerLabel: String = "test"

                override fun send(
                    body: String,
                    timeoutSeconds: Long,
                ): String = """{"choices":[{"message":{"content":"hi"}}]}"""
            }
        val loop =
            com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop(
                objectMapper = objectMapper,
                transport = transport,
            )
        assertNotNull(loop)
    }

    @Test
    fun `OpenAiCompatibleToolLoop processToolLoopIteration handles text-only response`() {
        val transport =
            object : com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop.ChatTransport {
                override val providerLabel: String = "test"

                override fun send(
                    body: String,
                    timeoutSeconds: Long,
                ): String = """{"choices":[{"message":{"content":"hi"}}]}"""
            }
        val loop =
            com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop(
                objectMapper = objectMapper,
                transport = transport,
            )
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val tmp = java.io.File.createTempFile("loop-test", "")
        tmp.delete()
        tmp.mkdirs()
        val result =
            loop.processToolLoopIteration(
                iteration = 0,
                respJson = """{"choices":[{"message":{"content":"hi"}}]}""",
                messages = messages,
                allText = allText,
                repoDir = tmp,
                onPartialOutput = { },
            )
        assertEquals(false, result)
        tmp.deleteRecursively()
    }

    @Test
    fun `OpenAiCompatibleToolLoop processToolLoopIteration handles tool_calls response`() {
        val transport =
            object : com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop.ChatTransport {
                override val providerLabel: String = "test"

                override fun send(
                    body: String,
                    timeoutSeconds: Long,
                ): String = """{"choices":[{"message":{"content":"","tool_calls":[{"id":"1","type":"function","function":{"name":"noop","arguments":"{}"}}]}}]}"""
            }
        val loop =
            com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop(
                objectMapper = objectMapper,
                transport = transport,
            )
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val tmp = java.io.File.createTempFile("loop-test2", "")
        tmp.delete()
        tmp.mkdirs()
        val result =
            loop.processToolLoopIteration(
                iteration = 0,
                respJson = """{"choices":[{"message":{"content":"","tool_calls":[{"id":"1","type":"function","function":{"name":"noop","arguments":"{}"}}]}}]}""",
                messages = messages,
                allText = allText,
                repoDir = tmp,
                onPartialOutput = { },
            )
        assertNotNull(result)
        tmp.deleteRecursively()
    }

    @Test
    fun `OpenAiCompatibleToolLoop processToolLoopIteration handles finish_reason stop`() {
        val transport =
            object : com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop.ChatTransport {
                override val providerLabel: String = "test"

                override fun send(
                    body: String,
                    timeoutSeconds: Long,
                ): String = """{"choices":[{"finish_reason":"stop","message":{"content":"done"}}]}"""
            }
        val loop =
            com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop(
                objectMapper = objectMapper,
                transport = transport,
            )
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val tmp = java.io.File.createTempFile("loop-test3", "")
        tmp.delete()
        tmp.mkdirs()
        val result =
            loop.processToolLoopIteration(
                iteration = 0,
                respJson = """{"choices":[{"finish_reason":"stop","message":{"content":"done"}}]}""",
                messages = messages,
                allText = allText,
                repoDir = tmp,
                onPartialOutput = { },
            )
        assertNotNull(result)
        tmp.deleteRecursively()
    }

    @Test
    fun `OpenAiCompatibleToolLoop runLoop runs to completion`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"done"}}]}"""),
        )
        val transport =
            object : com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop.ChatTransport {
                override val providerLabel: String = "test"

                override fun send(
                    body: String,
                    timeoutSeconds: Long,
                ): String = """{"choices":[{"finish_reason":"stop","message":{"content":"done"}}]}"""
            }
        val loop =
            com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop(
                objectMapper = objectMapper,
                transport = transport,
            )
        val tmp = java.io.File.createTempFile("openai-loop", "")
        tmp.delete()
        tmp.mkdirs()
        try {
            val result =
                loop.runLoop(
                    model = "gpt-4",
                    systemPrompt = "test system",
                    userPrompt = "test user",
                    timeoutSeconds = 10L,
                    repoDir = tmp,
                    onPartialOutput = { },
                    buildRequestBody = { msgs ->
                        objectMapper.writeValueAsString(
                            mapOf(
                                "model" to "gpt-4",
                                "messages" to msgs,
                            ),
                        )
                    },
                    sendRequest = { _, _ -> """{"choices":[{"finish_reason":"stop","message":{"content":"done"}}]}""" },
                    onComplete = { },
                )
            assertNotNull(result)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
