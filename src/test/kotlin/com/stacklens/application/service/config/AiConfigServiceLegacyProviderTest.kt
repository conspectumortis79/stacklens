package com.stacklens.application.service.config
import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiConfigServiceLegacyProviderTest {
    private val aiConfigRepo: AiConfigPort = stubRepo()
    private val connectionTest: AiConnectionTestGateway = mock()
    private val controller = AiConfigService(aiConfigRepo, connectionTest, AiConfigSecretFieldRegistry())

    private fun storedWith(provider: String) {
        whenever(aiConfigRepo.findAiConfig()) doReturn AiConfig(id = 1L, aiProvider = provider, model = "stored")
    }

    @Test
    fun `an unknown stored provider keeps a model that was sent explicitly`() {
        storedWith(LEGACY_PROVIDER)

        val saved = controller.saveAiConfigFrom(saveRequest(model = "explicit-model"))

        assertEquals("explicit-model", saved.model)
        assertEquals(LEGACY_PROVIDER, saved.aiProvider)
    }

    @Test
    fun `an unknown stored provider falls back to the copilot model`() {
        storedWith(LEGACY_PROVIDER)

        val saved = controller.saveAiConfigFrom(saveRequest(model = "   "))

        assertEquals(AiProvider.COPILOT.defaultModel, saved.model)
    }

    @Test
    fun `an unknown stored provider normalises a submitted reasoning effort`() {
        storedWith(LEGACY_PROVIDER)

        val saved = controller.saveAiConfigFrom(saveRequest(openAiReasoningEffort = "WILD"))

        assertEquals("medium", saved.openAiReasoningEffort)
    }

    @Test
    fun `an unknown stored provider without any model falls back to the copilot model`() {
        storedWith(LEGACY_PROVIDER)

        val saved = controller.saveAiConfigFrom(saveRequest())

        assertEquals(AiProvider.COPILOT.defaultModel, saved.model)
    }

    private companion object {
        const val LEGACY_PROVIDER = "copilot-cli"
    }

    private fun stubRepo(): AiConfigPort =
        mock {
            on { saveAiConfig(any()) } doAnswer { invocation -> invocation.getArgument(0) }
        }

    private fun saveRequest(
        aiProvider: String? = null,
        model: String? = null,
        ollamaUrl: String? = null,
        ollamaNumCtx: Int = 0,
        lmStudioUrl: String? = null,
        openAiApiKey: String? = null,
        openAiReasoningEffort: String? = null,
        githubToken: String? = null,
        openRouterApiKey: String? = null,
        claudeCodeApiKey: String? = null,
        compatibleApiKey: String? = null,
        compatibleBaseUrl: String? = null,
    ) = AiConfigSaveRequest(
        aiProvider = aiProvider,
        model = model,
        ollamaUrl = ollamaUrl,
        ollamaNumCtx = ollamaNumCtx,
        lmStudioUrl = lmStudioUrl,
        openAiApiKey = openAiApiKey,
        openAiReasoningEffort = openAiReasoningEffort,
        githubToken = githubToken,
        openRouterApiKey = openRouterApiKey,
        claudeCodeApiKey = claudeCodeApiKey,
        compatibleApiKey = compatibleApiKey,
        compatibleBaseUrl = compatibleBaseUrl,
    )
}
