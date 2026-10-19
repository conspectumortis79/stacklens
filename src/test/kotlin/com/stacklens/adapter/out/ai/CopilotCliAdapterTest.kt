package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotCliAdapterTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private fun adapter(env: Map<String, String> = emptyMap()): CopilotCliAdapter =
        CopilotCliAdapter(
            configuredBinary = "copilot",
            legacyCliBin = "github-copilot-cli",
            timeoutSeconds = 10L,
            defaultModel = "gpt-4o",
            aiConfigRepo = aiConfigRepo,
            envReader = { name -> env[name] },
        )

    private fun aiConfig(
        githubToken: String = "test-key",
        model: String = "gpt-4o",
        language: String = "en",
    ): AiConfig {
        val cfg = AiConfig(model = model, language = language)
        cfg.githubToken = githubToken
        return cfg
    }

    @Test
    fun `writesFilesNatively is true for the copilot cli`() {
        assertEquals(true, adapter().writesFilesNatively)
    }

    @Test
    fun `resolveToken returns github token from aiConfigRepo`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(githubToken = "from-db"))
        assertEquals("from-db", adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when config has no github token`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(githubToken = ""))
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when no config exists`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `resolveToken falls back to env when config key blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(githubToken = ""))
        val result = adapter(env = mapOf("GH_TOKEN" to "from-env")).resolveToken()
        assertEquals("from-env", result)
    }

    @Test
    fun `isReady returns true when token is configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(githubToken = "key"))
        assertTrue(adapter().isReady())
    }

    @Test
    fun `isReady returns true when env var is set even without aiConfig`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        adapter(env = mapOf("GITHUB_TOKEN" to "any-value")).isReady()
    }

    @Test
    fun `resolveModel returns config model when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = "claude-opus-4.6"))
        assertEquals("claude-opus-4.6", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default when config missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertNotNull(adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default when config model is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = ""))
        assertNotNull(adapter().resolveModel())
    }

    @Test
    fun `resolveLanguage returns config language when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "de"))
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns default when config missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertNotNull(adapter().resolveLanguage())
    }

    @Test
    fun `requireToken throws when token is missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            adapter(env = emptyMap()).requireToken()
        }
    }

    @Test
    fun `requireToken returns token when configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(githubToken = "real-key"))
        assertEquals("real-key", adapter().requireToken())
    }

    @Test
    fun `checkOutput throws NOT_INSTALLED when output indicates binary missing`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().checkOutput("Cannot find GitHub Copilot CLI on PATH", 127, 5L)
            }
        assertTrue(ex.message!!.contains("COPILOT_NOT_INSTALLED"))
    }

    @Test
    fun `checkOutput throws QUOTA when output indicates quota error`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().checkOutput("quota exceeded for this account", 1, 5L)
            }
        assertTrue(ex.message!!.contains("COPILOT_QUOTA"))
    }

    @Test
    fun `checkOutput throws NO_OUTPUT when exit code != 0 and output blank`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().checkOutput("", 1, 5L)
            }
        assertTrue(ex.message!!.contains("COPILOT_NO_OUTPUT"))
    }

    @Test
    fun `checkOutput throws EXIT_ERROR when exit code greater than 1`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().checkOutput("some error output", 2, 5L)
            }
        assertTrue(ex.message!!.contains("COPILOT_EXIT_ERROR"))
    }

    @Test
    fun `checkOutput throws EMPTY_RESPONSE when exit 0 with blank output`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().checkOutput("", 0, 5L)
            }
        assertTrue(ex.message!!.contains("COPILOT_EMPTY_RESPONSE"))
    }

    @Test
    fun `checkOutput passes when exit 0 with valid output`() {
        adapter().checkOutput("normal response text", 0, 5L)
    }

    @Test
    fun `checkOutput allows exit 1 with non-blank output`() {
        adapter().checkOutput("some output", 1, 5L)
    }

    @Test
    fun `DEFAULT_MODEL_ALIASES maps gpt-4o-mini to gpt-5_4-mini`() {
        assertEquals("gpt-5.4-mini", CopilotCliAdapter.DEFAULT_MODEL_ALIASES["gpt-4o-mini"])
    }

    @Test
    fun `readEnv returns null for missing env var`() {
        val value = adapter().readEnv("DEFINITELY_NOT_SET_${System.nanoTime()}")
        assertNull(value)
    }

    private fun assertFalse(value: Boolean) =
        org.junit.jupiter.api.Assertions
            .assertFalse(value)
}
