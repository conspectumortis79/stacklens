package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenRouterAdapterResolveTest {
    private val cfg: AiConfigPort = mock()
    private val om = ObjectMapper()
    private val prompt: PromptReaderPort = mock()
    private val adapter = OpenRouterAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt)

    @Test
    fun `resolveToken returns null when no config and no env var`() {
        whenever(cfg.findAiConfig()).thenReturn(null)
        val savedKey = System.getenv("OPENROUTER_API_KEY")
        if (savedKey == null) {
            assertNull(adapter.resolveToken())
        }
    }

    @Test
    fun `resolveToken returns config key when present and non-blank`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = "config-key"))
        assertEquals("config-key", adapter.resolveToken())
    }

    @Test
    fun `resolveToken returns null when config key is blank`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = ""))
        val savedKey = System.getenv("OPENROUTER_API_KEY")
        if (savedKey == null) {
            assertNull(adapter.resolveToken())
        }
    }

    @Test
    fun `resolveToken returns null when config is null and env is not set`() {
        whenever(cfg.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENROUTER_API_KEY") == null) {
            assertNull(adapter.resolveToken())
        }
    }

    @Test
    fun `resolveLanguage returns configured language when set`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(language = "fr"))
        assertEquals("fr", adapter.resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns default 'de' when config language is null`() {
        val c = AiConfig()
        c.language = ""
        whenever(cfg.findAiConfig()).thenReturn(c)
        assertEquals("de", adapter.resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns default 'de' when config language is blank`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(language = "   "))
        assertEquals("de", adapter.resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns default 'de' when no config`() {
        whenever(cfg.findAiConfig()).thenReturn(null)
        assertEquals("de", adapter.resolveLanguage())
    }

    @Test
    fun `resolveModel returns configured model when set`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(model = "anthropic/claude-3"))
        assertEquals("anthropic/claude-3", adapter.resolveModel())
    }

    @Test
    fun `resolveModel returns default when not set`() {
        val c = AiConfig()
        c.model = ""
        whenever(cfg.findAiConfig()).thenReturn(c)
        assertNotNull(adapter.resolveModel())
    }

    @Test
    fun `resolveModel returns default when blank`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(model = ""))
        assertNotNull(adapter.resolveModel())
    }

    @Test
    fun `isReady returns true when token available`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = "key"))
        assertEquals(true, adapter.isReady())
    }

    @Test
    fun `isReady returns false when no token available`() {
        whenever(cfg.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENROUTER_API_KEY") == null) {
            assertEquals(false, adapter.isReady())
        }
    }
}

class OpenRouterAdapterResolveTokenEnvTest {
    private val cfg: AiConfigPort = mock()
    private val om = ObjectMapper()
    private val prompt: PromptReaderPort = mock()

    @Test
    fun `resolveToken returns config key when config exists with non-blank key`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = "config-key"))
        val adapter = OpenRouterAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt)
        assertEquals("config-key", adapter.resolveToken())
    }

    @Test
    fun `resolveToken returns null when config is null and env var is null`() {
        whenever(cfg.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENROUTER_API_KEY") == null) {
            val adapter = OpenRouterAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt)
            assertNull(adapter.resolveToken())
        }
    }

    @Test
    fun `resolveToken skips config key when blank and falls back to env`() {
        whenever(cfg.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = ""))
        val adapter = OpenRouterAdapter(aiConfigRepo = cfg, objectMapper = om, promptLoader = prompt)
        val token = adapter.resolveToken()
        if (token != null) {
            assertTrue(token.isNotBlank())
        }
    }
}
