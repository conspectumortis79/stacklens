package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LmStudioAdapterHelpersTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val objectMapper = ObjectMapper()

    private fun adapter() =
        LmStudioAdapter(
            defaultLmStudioUrl = "http://localhost:1234",
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
        )

    private fun aiConfig(
        lmStudioUrl: String = "",
        model: String = "local-model",
        language: String = "en",
    ) = AiConfig(lmStudioUrl = lmStudioUrl, model = model, language = language)

    @Test
    fun `extractNativeChatContent returns string when output is plain string`() {
        val map = mapOf<String, Any?>("output" to "plain text")
        assertEquals("plain text", adapter().extractNativeChatContent(map))
    }

    @Test
    fun `extractNativeChatContent returns joined message content from list`() {
        val map =
            mapOf<String, Any?>(
                "output" to
                    listOf(
                        mapOf("type" to "message", "content" to "hello"),
                        mapOf("type" to "message", "content" to " world"),
                    ),
            )
        assertEquals("hello world", adapter().extractNativeChatContent(map))
    }

    @Test
    fun `extractNativeChatContent returns null when output missing`() {
        val map = mapOf<String, Any?>("other" to "value")
        assertNull(adapter().extractNativeChatContent(map))
    }

    @Test
    fun `extractNativeChatContent returns null when output is non-message list`() {
        val map = mapOf<String, Any?>("output" to listOf(mapOf("type" to "other", "content" to "ignored")))
        assertNull(adapter().extractNativeChatContent(map))
    }

    @Test
    fun `extractNativeChatContent returns null for empty map`() {
        assertNull(adapter().extractNativeChatContent(emptyMap<String, Any?>()))
    }

    @Test
    fun `extractNativeChatContent ignores non-message items in list`() {
        val map =
            mapOf<String, Any?>(
                "output" to
                    listOf(
                        mapOf("type" to "tool_call", "name" to "x"),
                        mapOf("type" to "message", "content" to "kept"),
                    ),
            )
        assertEquals("kept", adapter().extractNativeChatContent(map))
    }

    @Test
    fun `parseCompletionResponse returns text from choices array`() {
        val json = """{"choices":[{"message":{"content":"the answer"}}]}"""
        assertEquals("the answer", adapter().parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns empty string when content missing`() {
        val json = """{"choices":[{"message":{}}]}"""
        assertEquals("", adapter().parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns empty string when choices empty`() {
        val json = """{"choices":[]}"""
        assertEquals("", adapter().parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns empty string when choices missing`() {
        val json = """{"other":"value"}"""
        assertEquals("", adapter().parseCompletionResponse(json))
    }

    @Test
    fun `resolveLmStudioUrl uses default when configured is null`() {
        val method =
            LmStudioAdapter::class.java.declaredMethods
                .firstOrNull { it.name == "resolveLmStudioUrl\$stacklens" }
        if (method != null) {
            method.isAccessible = true
            val result = method.invoke(adapter()) as String
            assertEquals("http://localhost:1234", result)
        }
    }

    @Test
    fun `resolveLmStudioUrl uses default when configured is blank`() {
        val cfgBlank = aiConfig(lmStudioUrl = "")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfgBlank)
        val method =
            LmStudioAdapter::class.java.declaredMethods
                .firstOrNull { it.name == "resolveLmStudioUrl\$stacklens" }
        if (method != null) {
            method.isAccessible = true
            val result = method.invoke(adapter()) as String
            assertEquals("http://localhost:1234", result)
        }
    }

    @Test
    fun `resolveLmStudioUrl returns configured URL when set`() {
        val cfg = aiConfig(lmStudioUrl = "http://custom:5678")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val method =
            LmStudioAdapter::class.java.declaredMethods
                .firstOrNull { it.name == "resolveLmStudioUrl\$stacklens" }
        if (method != null) {
            method.isAccessible = true
            val result = method.invoke(adapter()) as String
            assertEquals("http://custom:5678", result)
        }
    }

    @Test
    fun `buildRequestBody includes model and stream false`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system")
        val body = adapter().buildRequestBody("local-model", "hello", stream = false)
        val parsed = objectMapper.readTree(body)
        assertEquals("local-model", parsed.path("model").asText())
        assertFalse(parsed.path("stream").asBoolean())
    }

    @Test
    fun `buildRequestBody sets stream true when requested`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system")
        val body = adapter().buildRequestBody("local-model", "hello", stream = true)
        val parsed = objectMapper.readTree(body)
        assertTrue(parsed.path("stream").asBoolean())
    }

    @Test
    fun `buildRequestBody includes user prompt in messages array`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system")
        val body = adapter().buildRequestBody("local-model", "hello", stream = false)
        val parsed = objectMapper.readTree(body)
        val messages = parsed.path("messages")
        assertTrue(messages.isArray)
        assertEquals("user", messages[1].path("role").asText())
        assertEquals("hello", messages[1].path("content").asText())
    }

    @Test
    fun `buildRequestBody includes system prompt as first message`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("MY_SYSTEM_PROMPT")
        val body = adapter().buildRequestBody("local-model", "hello", stream = false)
        val parsed = objectMapper.readTree(body)
        val messages = parsed.path("messages")
        assertEquals("system", messages[0].path("role").asText())
        assertEquals("MY_SYSTEM_PROMPT", messages[0].path("content").asText())
    }

    @Test
    fun `resolveSystemPrompt delegates to promptLoader`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("the prompt")
        assertEquals("the prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `resolveSystemPrompt uses default language when config missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        whenever(promptLoader.get("de", "commontools.systemPrompt")).thenReturn("der Prompt")
        assertEquals("der Prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `buildRequestBody converts attachments into OpenAI image_url content array`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val attachment =
            com.stacklens.application.port.out.ai.AiAttachment(
                mimeType = "image/jpeg",
                base64Data = "BASE64DATA",
            )
        val body = adapter().buildRequestBody("local-model", "describe this", stream = false, attachments = listOf(attachment))
        val parsed =
            com.fasterxml.jackson.databind
                .ObjectMapper()
                .readTree(body)
        val userMessage = parsed.path("messages").path(1)
        assertEquals("user", userMessage.path("role").asText())
        val content = userMessage.path("content")
        assertTrue(content.isArray, "user.content must be an array when attachments are present")
        assertEquals(2, content.size())
        assertEquals("text", content.path(0).path("type").asText())
        assertEquals("describe this", content.path(0).path("text").asText())
        assertEquals("image_url", content.path(1).path("type").asText())
        assertEquals(
            "data:image/jpeg;base64,BASE64DATA",
            content
                .path(1)
                .path("image_url")
                .path("url")
                .asText(),
        )
    }
}
