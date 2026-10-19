package com.stacklens.application.port.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProviderSpecVariantsTest {
    @Test
    fun `GithubToken stores token`() {
        val s = ProviderSpec.GithubToken(token = "tok-1")
        assertEquals("tok-1", s.token)
    }

    @Test
    fun `GithubToken accepts null token`() {
        val s = ProviderSpec.GithubToken(token = null)
        assertNull(s.token)
    }

    @Test
    fun `CopilotModels stores token`() {
        val s = ProviderSpec.CopilotModels(token = "tok-c")
        assertEquals("tok-c", s.token)
    }

    @Test
    fun `CopilotModels accepts null token`() {
        val s = ProviderSpec.CopilotModels(token = null)
        assertNull(s.token)
    }

    @Test
    fun `CopilotModel stores model and savedProvider`() {
        val s = ProviderSpec.CopilotModel(model = "gpt-4", savedProvider = "copilot")
        assertEquals("gpt-4", s.model)
        assertEquals("copilot", s.savedProvider)
    }

    @Test
    fun `CopilotModel accepts null model`() {
        val s = ProviderSpec.CopilotModel(model = null, savedProvider = "copilot")
        assertNull(s.model)
    }

    @Test
    fun `Ollama stores baseUrl`() {
        val s = ProviderSpec.Ollama(baseUrl = "http://localhost:11434")
        assertEquals("http://localhost:11434", s.baseUrl)
    }

    @Test
    fun `OpenAi stores apiKey`() {
        val s = ProviderSpec.OpenAi(apiKey = "sk-1")
        assertEquals("sk-1", s.apiKey)
    }

    @Test
    fun `OpenAi accepts null apiKey`() {
        val s = ProviderSpec.OpenAi(apiKey = null)
        assertNull(s.apiKey)
    }

    @Test
    fun `LmStudio stores baseUrl`() {
        val s = ProviderSpec.LmStudio(baseUrl = "http://localhost:1234")
        assertEquals("http://localhost:1234", s.baseUrl)
    }

    @Test
    fun `OpenRouter stores apiKey`() {
        val s = ProviderSpec.OpenRouter(apiKey = "sk-or-1")
        assertEquals("sk-or-1", s.apiKey)
    }

    @Test
    fun `OpenRouter accepts null apiKey`() {
        val s = ProviderSpec.OpenRouter(apiKey = null)
        assertNull(s.apiKey)
    }

    @Test
    fun `Compatible stores apiKey baseUrl and model`() {
        val s =
            ProviderSpec.Compatible(
                apiKey = "sk-compat",
                baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                model = "qwen3.8-flash",
            )
        assertEquals("sk-compat", s.apiKey)
        assertEquals("https://dashscope-intl.aliyuncs.com/compatible-mode/v1", s.baseUrl)
        assertEquals("qwen3.8-flash", s.model)
    }

    @Test
    fun `Compatible accepts a null apiKey and a null model`() {
        val s = ProviderSpec.Compatible(apiKey = null, baseUrl = "http://localhost:8000/v1", model = null)
        assertNull(s.apiKey)
        assertNull(s.model)
        assertEquals("http://localhost:8000/v1", s.baseUrl)
    }
}
