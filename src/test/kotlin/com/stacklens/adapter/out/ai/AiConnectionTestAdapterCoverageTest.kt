package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiConnectionTestAdapterCoverageTest {
    private val lmStudioService = mock<LmStudioModelLookupPort>()
    private val probe = mock<ContainerRuntimeProbePort>()
    private val adapter = AiConnectionTestAdapter(emptyList(), lmStudioService, probe)

    @Test
    fun `an explicitly requested model always wins`() {
        assertEquals("gpt-selected", adapter.resolveLmStudioModel("gpt-selected", "http://host:1234", AiConfig()))
    }

    @Test
    fun `a saved lmstudio model is reused before any lookup happens`() {
        val config = AiConfig(aiProvider = AiProvider.LMSTUDIO.id, model = "llama-saved")

        assertEquals("llama-saved", adapter.resolveLmStudioModel(null, null, config))
    }

    @Test
    fun `a blank saved lmstudio model falls through to the server lookup`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(listOf("llama-listed"))
        val config = AiConfig(aiProvider = AiProvider.LMSTUDIO.id, model = "   ")

        assertEquals("llama-listed", adapter.resolveLmStudioModel("", "http://host:1234", config))
    }

    @Test
    fun `a non lmstudio config never reuses the saved model`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(listOf("mistral-listed"))
        val config = AiConfig(aiProvider = AiProvider.COPILOT.id, model = "claude-saved")

        assertEquals("mistral-listed", adapter.resolveLmStudioModel(null, null, config))
    }

    @Test
    fun `the stored lmstudio url is preferred over the built in default`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(listOf("model-from-stored-url"))
        val config = AiConfig(lmStudioUrl = "http://stored:1234")

        assertEquals("model-from-stored-url", adapter.resolveLmStudioModel(null, "  ", config))
    }

    @Test
    fun `the requested lmstudio url wins over the stored one`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(listOf("model-from-requested-url"))

        assertEquals("model-from-requested-url", adapter.resolveLmStudioModel(null, "http://requested:1234", AiConfig(lmStudioUrl = "http://stored:1234")))
    }

    @Test
    fun `an empty model list yields the provider default model`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(emptyList())

        assertEquals(AiProvider.LMSTUDIO.defaultModel, adapter.resolveLmStudioModel(null, null, AiConfig()))
    }

    @Test
    fun `a failing lookup yields the provider default model`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenThrow(IllegalStateException("lm studio offline"))

        assertEquals(AiProvider.LMSTUDIO.defaultModel, adapter.resolveLmStudioModel(null, null, AiConfig()))
    }

    @Test
    fun `a blank first entry from the server yields the provider default model`() {
        whenever(lmStudioService.fetchAvailableModels(any())).thenReturn(listOf("   "))

        assertEquals(AiProvider.LMSTUDIO.defaultModel, adapter.resolveLmStudioModel(null, null, AiConfig()))
    }
}
