package com.stacklens.adapter.inbound.rest.config
import com.stacklens.application.port.inbound.config.AiConfigUseCase
import com.stacklens.application.port.inbound.config.AiConnectionTestUseCase
import com.stacklens.application.port.model.AiConfigMasking
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AiConfigApiControllerTest {
    @Mock private lateinit var configManagement: AiConfigUseCase

    @Mock private lateinit var connectionTest: AiConnectionTestUseCase

    private val controller by lazy { AiConfigApiController(configManagement, connectionTest, AiConfigDtoMapper()) }

    private val successPayload: Map<String, Any> = mapOf("success" to true)
    private val failurePayload: Map<String, Any> = mapOf("success" to false, "error" to "err")

    private fun mockAiConfig(
        token: String? = "mytoken",
        model: String? = "gpt-4",
        provider: String = "copilot",
    ): AiConfig =
        AiConfig(
            id = 1L,
            githubToken = token,
            model = model,
            availableModels = "gpt-4,gpt-3.5-turbo",
            language = "en",
            aiProvider = provider,
        )

    private fun mockOpenAiConfig(
        apiKey: String? = "sk-proj-test",
        model: String? = "gpt-5.2",
        reasoningEffort: String? = null,
    ) = AiConfig(
        id = 1L,
        openAiApiKey = apiKey,
        model = model,
        aiProvider = "openai",
        language = "en",
        openAiReasoningEffort = reasoningEffort,
    )

    @Test
    fun `the controller uses the injected dto mapper`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockAiConfig())
        assertEquals(AiConfigMasking.MASKED_SECRET, controller.getAiConfig().githubToken)
    }

    @Test
    fun `getAiConfig returns masked token when token is set`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockAiConfig())
        val result = controller.getAiConfig()
        assertEquals("••••••••", result.githubToken)
        assertTrue(result.githubTokenSet)
        assertEquals("gpt-4", result.model)
    }

    @Test
    fun `getAiConfig returns masked OpenAI API key when set`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockOpenAiConfig())
        val result = controller.getAiConfig()
        assertEquals("••••••••", result.openAiApiKey)
        assertTrue(result.openAiApiKeySet)
        assertEquals("openai", result.aiProvider)
    }

    @Test
    fun `getAiConfig returns empty OpenAI API key when none set`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockOpenAiConfig(apiKey = null))
        val result = controller.getAiConfig()
        assertEquals("", result.openAiApiKey)
        assertFalse(result.openAiApiKeySet)
    }

    @Test
    fun `getAiConfig returns configured OpenAI reasoning effort`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockOpenAiConfig(reasoningEffort = "high"))
        assertEquals("high", controller.getAiConfig().openAiReasoningEffort)
    }

    @Test
    fun `getAiConfig returns medium OpenAI reasoning effort when config is missing`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockOpenAiConfig(reasoningEffort = null))
        assertEquals("medium", controller.getAiConfig().openAiReasoningEffort)
    }

    @Test
    fun `getAiConfig returns empty token when no token set`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockAiConfig(token = null))
        val result = controller.getAiConfig()
        assertEquals("", result.githubToken)
        assertFalse(result.githubTokenSet)
    }

    @Test
    fun `getAiConfig returns empty token when blank token`() {
        whenever(configManagement.getAiConfig()).thenReturn(mockAiConfig(token = ""))
        val result = controller.getAiConfig()
        assertEquals("", result.githubToken)
        assertFalse(result.githubTokenSet)
    }

    @Test
    fun `getAiConfig returns defaults when no config exists`() {
        whenever(configManagement.getAiConfig()).thenReturn(null)
        val result = controller.getAiConfig()
        assertEquals("", result.githubToken)
        assertFalse(result.githubTokenSet)
        assertEquals("", result.model)
        assertEquals(emptyList<String>(), result.availableModels)
    }

    @Test
    fun `getAiConfig returns empty available models when config availableModels is null`() {
        whenever(configManagement.getAiConfig()).thenReturn(AiConfig(githubToken = "t", model = "gpt-4"))
        assertEquals(emptyList<String>(), controller.getAiConfig().availableModels)
    }

    @Test
    fun `getAiConfig filters blank entries from availableModels`() {
        whenever(configManagement.getAiConfig()).thenReturn(
            AiConfig(githubToken = "t", model = "gpt-4", availableModels = "gpt-4,,gpt-3.5-turbo"),
        )
        assertEquals(listOf("gpt-4", "gpt-3.5-turbo"), controller.getAiConfig().availableModels)
    }

    @Test
    fun `getAiConfig returns default ollamaNumCtx 32768 when no config exists`() {
        whenever(configManagement.getAiConfig()).thenReturn(null)
        assertEquals(32768, controller.getAiConfig().ollamaNumCtx)
    }

    @Test
    fun `getAiConfig returns ollamaNumCtx from config when set`() {
        whenever(configManagement.getAiConfig()).thenReturn(AiConfig(id = 1L, ollamaNumCtx = 65536))
        assertEquals(65536, controller.getAiConfig().ollamaNumCtx)
    }

    @Test
    fun `getAiConfig returns lmStudioUrl from config when set`() {
        whenever(configManagement.getAiConfig()).thenReturn(AiConfig(id = 1L, lmStudioUrl = "http://myhost:1234"))
        assertEquals("http://myhost:1234", controller.getAiConfig().lmStudioUrl)
    }

    @Test
    fun `getAiConfig returns empty lmStudioUrl when config has no lmStudioUrl`() {
        whenever(configManagement.getAiConfig()).thenReturn(AiConfig(id = 1L, lmStudioUrl = null))
        assertEquals("", controller.getAiConfig().lmStudioUrl)
    }

    @Test
    fun `getAiConfig returns empty lmStudioUrl when no config exists`() {
        whenever(configManagement.getAiConfig()).thenReturn(null)
        assertEquals("", controller.getAiConfig().lmStudioUrl)
    }

    @Test
    fun `getAiConfig returns masked OpenRouter API key when set`() {
        whenever(configManagement.getAiConfig()).thenReturn(
            AiConfig(id = 1L, openRouterApiKey = "sk-or-my-secret-key-12345", aiProvider = "openrouter", model = "openai/gpt-4.1"),
        )
        val result = controller.getAiConfig()
        assertEquals("••••••••", result.openRouterApiKey)
        assertTrue(result.openRouterApiKeySet)
        assertEquals("openrouter", result.aiProvider)
    }

    @Test
    fun `getAiConfig returns empty OpenRouter API key when not set`() {
        whenever(configManagement.getAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "copilot"))
        val result = controller.getAiConfig()
        assertEquals("", result.openRouterApiKey)
        assertFalse(result.openRouterApiKeySet)
    }

    private companion object {
        private const val MASK: String = "••••••••"
    }
}
