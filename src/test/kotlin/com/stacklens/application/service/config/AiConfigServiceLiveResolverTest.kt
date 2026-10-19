package com.stacklens.application.service.config
import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiConfigServiceLiveResolverTest {
    private val aiConfigRepo: AiConfigPort = stubRepo()
    private val connectionTest: AiConnectionTestGateway = mock()

    @Test
    fun `saveAiConfig looks up the live model via the resolver for the LM Studio provider`() {
        whenever(connectionTest.resolveLmStudioModel(anyOrNull(), anyOrNull(), any()))
            .thenReturn("local-model")

        val controller =
            AiConfigService(aiConfigRepo, connectionTest, AiConfigSecretFieldRegistry())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = AiProvider.LMSTUDIO.id))

        controller.saveAiConfigFrom(
            saveRequest(
                aiProvider = AiProvider.LMSTUDIO.id,
                model = "",
                lmStudioUrl = "http://localhost:1234",
            ),
        )

        org.mockito.kotlin
            .verify(connectionTest)
            .resolveLmStudioModel(anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `saveAiConfig does not call the live-model port for non LM Studio providers`() {
        val controller =
            AiConfigService(aiConfigRepo, connectionTest, AiConfigSecretFieldRegistry())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = AiProvider.OPENAI.id))
        whenever(connectionTest.resolveLmStudioModel(anyOrNull(), anyOrNull(), any())).thenReturn("ignored")

        controller.saveAiConfigFrom(
            saveRequest(
                aiProvider = AiProvider.OPENAI.id,
                model = "gpt-5.2",
            ),
        )

        org.mockito.kotlin
            .verify(connectionTest, org.mockito.kotlin.never())
            .resolveLmStudioModel(anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `saveAiConfig persists the configured model when DTO model is provided`() {
        val controller =
            AiConfigService(aiConfigRepo, connectionTest, AiConfigSecretFieldRegistry())
        val saved =
            AiConfig(
                id = 1L,
                aiProvider = AiProvider.OPENAI.id,
                model = "gpt-5.2",
            )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = AiProvider.OPENAI.id))
        whenever(aiConfigRepo.saveAiConfig(any())).thenReturn(saved)

        val result =
            controller.saveAiConfigFrom(
                saveRequest(
                    aiProvider = AiProvider.OPENAI.id,
                    model = "gpt-5.2",
                ),
            )

        assertThat(result.model).isEqualTo("gpt-5.2")
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
