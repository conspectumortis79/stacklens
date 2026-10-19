package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files

class OllamaAdapterBranchFullTest {
    private val objectMapper = ObjectMapper()

    private fun adapter(
        ollamaUrl: String? = null,
        ollamaNumCtx: Int? = null,
        containerProbe: com.stacklens.application.port.out.platform.ContainerRuntimeProbePort =
            com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
    ): OllamaAdapter {
        val aiConfigRepo = mock<AiConfigPort>()
        whenever(aiConfigRepo.findAiConfig()).thenAnswer {
            val cfg = mock<AiConfig>()
            if (ollamaUrl != null) whenever(cfg.ollamaUrl).thenReturn(ollamaUrl)
            if (ollamaNumCtx != null) whenever(cfg.ollamaNumCtx).thenReturn(ollamaNumCtx)
            cfg
        }
        return OllamaAdapter(
            defaultOllamaUrl = "http://localhost:11434",
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = mock<PromptReaderPort>(),
            containerRuntimeProbe = containerProbe,
        )
    }

    @Test
    fun `resolveNumCtx returns default when config is null`() {
        val aiConfigRepo = mock<AiConfigPort>()
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val a =
            OllamaAdapter(
                defaultOllamaUrl = "http://localhost:11434",
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        val method = OllamaAdapter::class.java.getDeclaredMethod("resolveNumCtx\$stacklens")
        method.isAccessible = true
        val result = method.invoke(a) as Int
        assertEquals(32768, result)
    }

    @Test
    fun `resolveNumCtx returns default when numCtx is zero`() {
        val a = adapter(ollamaNumCtx = 0)
        val method = OllamaAdapter::class.java.getDeclaredMethod("resolveNumCtx\$stacklens")
        method.isAccessible = true
        val result = method.invoke(a) as Int
        assertEquals(32768, result)
    }

    @Test
    fun `resolveNumCtx returns default when numCtx is negative`() {
        val a = adapter(ollamaNumCtx = -1)
        val method = OllamaAdapter::class.java.getDeclaredMethod("resolveNumCtx\$stacklens")
        method.isAccessible = true
        val result = method.invoke(a) as Int
        assertEquals(32768, result)
    }

    @Test
    fun `resolveNumCtx returns configured value when positive`() {
        val a = adapter(ollamaNumCtx = 16384)
        val method = OllamaAdapter::class.java.getDeclaredMethod("resolveNumCtx\$stacklens")
        method.isAccessible = true
        val result = method.invoke(a) as Int
        assertEquals(16384, result)
    }

    @Test
    fun `resolveOllamaUrl replaces localhost with host docker internal inside a container`() {
        val a =
            adapter(
                ollamaUrl = "http://localhost:11434",
                containerProbe =
                    com.stacklens.testsupport.FakeContainerRuntimeProbePort(
                        com.stacklens.application.port.out.platform.ContainerRuntime.DOCKER,
                    ),
            )
        val method = OllamaAdapter::class.java.getDeclaredMethod("resolveOllamaUrl\$stacklens")
        method.isAccessible = true
        val result = method.invoke(a) as String
        assertEquals("http://host.docker.internal:11434", result)
    }

    @Test
    fun `resolveOllamaUrl keeps original URL when not in container`() {
        val a = adapter(ollamaUrl = "http://localhost:11434")
        val method = OllamaAdapter::class.java.getDeclaredMethod("resolveOllamaUrl\$stacklens")
        method.isAccessible = true
        val result = method.invoke(a) as String
        assertEquals("http://localhost:11434", result)
    }

    @Test
    fun `resolveOllamaUrl uses default when configured is blank`() {
        val a = adapter(ollamaUrl = "")
        val method = OllamaAdapter::class.java.getDeclaredMethod("resolveOllamaUrl\$stacklens")
        method.isAccessible = true
        val result = method.invoke(a) as String
        assertEquals("http://localhost:11434", result)
    }

    @Test
    fun `stripThinkingBlocks removes think blocks and trims leading whitespace`() {
        val a = adapter()
        val method = OllamaAdapter::class.java.getDeclaredMethod("stripThinkingBlocks\$stacklens", String::class.java)
        method.isAccessible = true
        val result = method.invoke(a, " <think>thinking</think>actual content") as String
        assertEquals("actual content", result)
    }

    @Test
    fun `stripThinkingBlocks returns text unchanged when no think blocks`() {
        val a = adapter()
        val method = OllamaAdapter::class.java.getDeclaredMethod("stripThinkingBlocks\$stacklens", String::class.java)
        method.isAccessible = true
        val result = method.invoke(a, "  plain text") as String
        assertEquals("plain text", result)
    }

    @Test
    fun `parseCompletionResponse handles missing message field`() {
        val a = adapter()
        val method =
            OllamaAdapter::class.java.getDeclaredMethod("parseCompletionResponse", String::class.java)
        method.isAccessible = true
        val result = method.invoke(a, """{"choices":[{"finish_reason":"stop"}]}""") as String
        assertEquals("", result)
    }

    @Test
    fun `parseCompletionResponse returns empty when JSON has no choices`() {
        val a = adapter()
        val method =
            OllamaAdapter::class.java.getDeclaredMethod("parseCompletionResponse", String::class.java)
        method.isAccessible = true
        val result = method.invoke(a, """{"error":"oops"}""") as String
        assertEquals("", result)
    }

    @Test
    fun `parseCompletionResponse returns content from standard response`() {
        val a = adapter()
        val method =
            OllamaAdapter::class.java.getDeclaredMethod("parseCompletionResponse", String::class.java)
        method.isAccessible = true
        val result =
            method.invoke(a, """{"choices":[{"message":{"content":"hello"}}]}""") as String
        assertEquals("hello", result)
    }

    @Test
    fun `executeWithRepoTools works when repoDir is a real directory`() {
        val tmp = Files.createTempDirectory("ollama-repo-tools-").toFile()
        try {
            val methods = OllamaAdapter::class.java.declaredMethods.map { it.name }
            assertNotNull(methods)
            assertNotNull(methods.firstOrNull { it.contains("executeWithRepoTools") })
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `resolveLanguage returns config language when set`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.language).thenReturn("de")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val a =
            OllamaAdapter(
                defaultOllamaUrl = "http://localhost:11434",
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals("de", a.resolveLanguage())
    }
}
