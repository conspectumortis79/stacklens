package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.model.AiConfigMasking
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class AiConfigServiceSaveTest {
    private fun service(existing: AiConfig?): Pair<AiConfigService, AiConnectionTestGateway> {
        val repo: AiConfigPort =
            mock {
                on { findAiConfig() } doReturn existing
                on { saveAiConfig(any()) } doAnswer { invocation -> invocation.getArgument<AiConfig>(0) }
            }
        val gateway: AiConnectionTestGateway =
            mock {
                on { resolveLmStudioModel(anyOrNull(), anyOrNull(), any()) } doReturn "resolved-lm-model"
            }
        return AiConfigService(repo, gateway, AiConfigSecretFieldRegistry()) to gateway
    }

    private fun save(
        existing: AiConfig?,
        request: AiConfigSaveRequest,
    ): AiConfig = service(existing).first.saveAiConfigFrom(request)

    @Test
    fun `a freshly typed token replaces the stored one`() {
        val stored = AiConfig(id = 1L, githubToken = "old")

        val saved = save(stored, AiConfigSaveRequest(githubToken = "newtoken", model = "gpt-4"))

        assertEquals("newtoken", saved.githubToken)
        assertEquals("gpt-4", saved.model)
        assertEquals("copilot", saved.aiProvider)
    }

    @Test
    fun `the mask placeholder keeps the stored token`() {
        val saved = save(AiConfig(id = 1L, githubToken = "storedtoken"), AiConfigSaveRequest(githubToken = AiConfigMasking.MASKED_SECRET))

        assertEquals("storedtoken", saved.githubToken)
    }

    @Test
    fun `a blank token clears the stored one`() {
        val saved =
            save(
                AiConfig(id = 1L, githubToken = "storedtoken", openAiApiKey = "k", openRouterApiKey = "r", claudeCodeApiKey = "c"),
                AiConfigSaveRequest(githubToken = "", openAiApiKey = "", openRouterApiKey = "", claudeCodeApiKey = ""),
            )

        assertNull(saved.githubToken)
        assertNull(saved.openAiApiKey)
        assertNull(saved.openRouterApiKey)
        assertNull(saved.claudeCodeApiKey)
    }

    @Test
    fun `an omitted token keeps the stored value`() {
        val saved = save(AiConfig(id = 1L, githubToken = "storedtoken", claudeCodeApiKey = "sk-ant-stored"), AiConfigSaveRequest(model = "gpt-4"))

        assertEquals("storedtoken", saved.githubToken)
        assertEquals("sk-ant-stored", saved.claudeCodeApiKey)
    }

    @Test
    fun `a typed claude code key is stored`() {
        val saved = save(AiConfig(id = 1L, claudeCodeApiKey = "old"), AiConfigSaveRequest(claudeCodeApiKey = "sk-ant-new"))

        assertEquals("sk-ant-new", saved.claudeCodeApiKey)
    }

    @Test
    fun `whitespace around a typed secret is trimmed`() {
        val saved = save(AiConfig(id = 1L), AiConfigSaveRequest(githubToken = "  spaced  ", openAiApiKey = " a ", openRouterApiKey = " b ", claudeCodeApiKey = " c "))

        assertEquals("spaced", saved.githubToken)
        assertEquals("a", saved.openAiApiKey)
        assertEquals("b", saved.openRouterApiKey)
        assertEquals("c", saved.claudeCodeApiKey)
    }

    @Test
    fun `an invalid provider falls back to copilot`() {
        val saved = save(AiConfig(id = 1L), AiConfigSaveRequest(aiProvider = "nonsense", model = "gpt-x"))

        assertEquals("copilot", saved.aiProvider)
        assertEquals("gpt-x", saved.model)
    }

    @Test
    fun `the lmstudio provider resolves the live model and stores the url`() {
        val (sut, gateway) = service(AiConfig(id = 1L))

        val saved = sut.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "lmstudio", lmStudioUrl = "http://x:1234"))

        assertEquals("lmstudio", saved.aiProvider)
        assertEquals("resolved-lm-model", saved.model)
        assertEquals("http://x:1234", saved.lmStudioUrl)
        verify(gateway).resolveLmStudioModel(null, "http://x:1234", saved)
    }

    @Test
    fun `a typed model wins over the live lookup`() {
        val (sut, _) = service(AiConfig(id = 1L, model = "stored-model"))

        val saved = sut.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "lmstudio", model = "typed-model"))

        assertEquals("typed-model", saved.model)
    }

    @Test
    fun `a non canonical stored lmstudio id does not trigger the live lookup`() {
        val (sut, gateway) = service(AiConfig(id = 1L, aiProvider = "LMStudio", model = "stored"))

        val saved = sut.saveAiConfigFrom(AiConfigSaveRequest())

        assertEquals(AiProvider.LMSTUDIO.defaultModel, saved.model)
        verify(gateway, never()).resolveLmStudioModel(anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `a blank model falls back to the provider default instead of a stored model of another provider`() {
        val (sut, _) = service(AiConfig(id = 1L, model = "stored-model", aiProvider = "ollama"))

        val saved = sut.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "ollama", model = ""))

        assertEquals("ollama", saved.aiProvider)
        assertEquals(AiProvider.OLLAMA.defaultModel, saved.model)
    }

    @Test
    fun `ollama url and context size are stored only when supplied`() {
        val stored = AiConfig(id = 1L, ollamaUrl = "http://stored:11434", ollamaNumCtx = 4096)

        val cleared = save(stored, AiConfigSaveRequest(ollamaUrl = "", ollamaNumCtx = 0))
        assertNull(cleared.ollamaUrl)
        assertEquals(4096, cleared.ollamaNumCtx)

        val updated = save(AiConfig(id = 1L, ollamaNumCtx = 4096), AiConfigSaveRequest(ollamaUrl = "http://new:11434", ollamaNumCtx = 8192))
        assertEquals("http://new:11434", updated.ollamaUrl)
        assertEquals(8192, updated.ollamaNumCtx)
    }

    @Test
    fun `the reasoning effort is normalised for the openai provider`() {
        val saved = save(AiConfig(id = 1L), AiConfigSaveRequest(aiProvider = "openai", openAiReasoningEffort = "  HIGH  "))

        assertEquals("high", saved.openAiReasoningEffort)
    }

    @Test
    fun `the openai provider normalises a missing reasoning effort to the default`() {
        val saved = save(AiConfig(id = 1L, openAiReasoningEffort = "high"), AiConfigSaveRequest(aiProvider = "openai"))

        assertEquals("medium", saved.openAiReasoningEffort)
    }

    @Test
    fun `the reasoning effort is normalised for the lmstudio provider`() {
        val saved =
            save(
                AiConfig(id = 1L, aiProvider = "lmstudio", model = "stored"),
                AiConfigSaveRequest(aiProvider = "lmstudio", openAiReasoningEffort = " HIGH "),
            )

        assertEquals("high", saved.openAiReasoningEffort)
    }

    @Test
    fun `the reasoning effort is normalised when the dto carries one for another provider`() {
        val saved = save(AiConfig(id = 1L, openAiReasoningEffort = "low"), AiConfigSaveRequest(aiProvider = "copilot", openAiReasoningEffort = "unknown"))

        assertEquals("medium", saved.openAiReasoningEffort)
    }

    @Test
    fun `the reasoning effort is untouched for other providers without an explicit value`() {
        val saved = save(AiConfig(id = 1L, openAiReasoningEffort = "high"), AiConfigSaveRequest(aiProvider = "copilot"))

        assertEquals("high", saved.openAiReasoningEffort)
    }

    @Test
    fun `a missing stored config starts a new one`() {
        val saved = save(null, AiConfigSaveRequest(model = "gpt-4", aiProvider = "copilot"))

        assertEquals("gpt-4", saved.model)
        assertTrue(saved.aiProvider.isNotBlank())
    }

    @Test
    fun `the compatible key follows the explicit clear flag`() {
        val stored = AiConfig(id = 1L, compatibleApiKey = "kept", compatibleBaseUrl = "http://kept:8000/v1")

        val kept = save(stored, AiConfigSaveRequest(compatibleApiKey = null))
        assertEquals("kept", kept.compatibleApiKey)

        val cleared = save(stored, AiConfigSaveRequest(compatibleApiKey = null, explicitClearCompatibleKey = true))
        assertNull(cleared.compatibleApiKey)
    }

    @Test
    fun `a blank compatible base url drops the stored one`() {
        val saved = save(AiConfig(id = 1L, compatibleBaseUrl = "http://stored"), AiConfigSaveRequest(compatibleBaseUrl = ""))

        assertNull(saved.compatibleBaseUrl)
    }

    @Test
    fun `a supplied compatible base url is stored`() {
        val saved = save(AiConfig(id = 1L), AiConfigSaveRequest(compatibleBaseUrl = " http://new:8000/v1 "))

        assertEquals(" http://new:8000/v1 ", saved.compatibleBaseUrl)
    }

    @Test
    fun `github token clearing requires the explicit flag`() {
        val stored = AiConfig(id = 1L, githubToken = "stored")

        assertFalse(save(stored, AiConfigSaveRequest(githubToken = null)).githubToken.isNullOrBlank())
        assertTrue(save(stored, AiConfigSaveRequest(githubToken = null, explicitClearGithubToken = true)).githubToken == null)
    }
}
