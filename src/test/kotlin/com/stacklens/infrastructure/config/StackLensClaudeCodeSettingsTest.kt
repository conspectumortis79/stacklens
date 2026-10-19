package com.stacklens.infrastructure.config

import com.stacklens.domain.enums.AiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StackLensClaudeCodeSettingsTest {
    private fun settings(properties: AnthropicProperties) = StackLensClaudeCodeSettings(properties)

    @Test
    fun `the api key and the oauth token are trimmed and blank becomes absent`() {
        val configured = settings(AnthropicProperties(apiKey = "  sk-ant-api03-one  ", oauthToken = "sk-ant-oat01-two"))

        assertEquals("sk-ant-api03-one", configured.configuredApiKey())
        assertEquals("sk-ant-oat01-two", configured.configuredOauthToken())

        val blank = settings(AnthropicProperties(apiKey = "   ", oauthToken = "\t"))

        assertNull(blank.configuredApiKey())
        assertNull(blank.configuredOauthToken())
    }

    @Test
    fun `the model override wins and the provider default covers a blank value`() {
        assertEquals("claude-opus-4-5", settings(AnthropicProperties(model = " claude-opus-4-5 ")).defaultModel())
        assertEquals(AiProvider.CLAUDECODE.defaultModel, settings(AnthropicProperties(model = "  ")).defaultModel())
    }

    @Test
    fun `the oauth endpoint falls back to the claude code production url`() {
        assertEquals(AnthropicProperties.DEFAULT_OAUTH_TOKEN_URL, settings(AnthropicProperties(oauthTokenUrl = "")).oauthTokenUrl())
        assertEquals("https://proxy.test/oauth/token", settings(AnthropicProperties(oauthTokenUrl = " https://proxy.test/oauth/token ")).oauthTokenUrl())
    }

    @Test
    fun `the oauth client id is configured and can be switched off`() {
        assertEquals(AnthropicProperties.DEFAULT_OAUTH_CLIENT_ID, settings(AnthropicProperties()).oauthClientId())
        assertNull(settings(AnthropicProperties(oauthClientId = "  ")).oauthClientId())
    }
}
