package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiDispatcherAdapterReadinessTest {
    private val aiConfigRepo = mock<AiConfigPort>()
    private val copilot = mock<CopilotStreamingPort>()

    private fun adapterWithDefault(
        providers: Map<String, CopilotStreamingPort>,
        provider: String = "copilot",
    ): AiDispatcherAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(aiProvider = provider))
        return AiDispatcherAdapter(providers = providers, aiConfigRepo = aiConfigRepo, defaultProviderName = "copilot", clock = com.stacklens.adapter.out.time.SystemClockPort)
    }

    @Test
    fun `isReady answers false instead of failing when no provider is registered`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(aiProvider = "ghost"))
        val adapter = AiDispatcherAdapter(providers = emptyMap(), aiConfigRepo = aiConfigRepo, defaultProviderName = "copilot", clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertFalse(adapter.isReady())
        assertNull(adapter.resolveToken())
        assertEquals("Unknown", adapter.providerLabel)
    }

    @Test
    fun `a misconfigured provider name still answers readiness and executes through the default provider`() {
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.execute("m", "p")).thenReturn("ok")
        val adapter = adapterWithDefault(mapOf("copilot" to copilot), provider = "ghost")

        assertTrue(adapter.isReady())
        assertEquals("ok", adapter.execute("m", "p"))
        verify(copilot).execute("m", "p")
    }

    @Test
    fun `readiness of the resolved provider is reported unchanged`() {
        whenever(copilot.isReady()).thenReturn(false)
        val adapter = adapterWithDefault(mapOf("copilot" to copilot))

        assertFalse(adapter.isReady())
        verify(copilot).isReady()
    }

    @Test
    fun `an empty registry still fails loudly when a completion is requested`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(aiProvider = "copilot"))
        val adapter = AiDispatcherAdapter(providers = emptyMap(), aiConfigRepo = aiConfigRepo, defaultProviderName = "copilot", clock = com.stacklens.adapter.out.time.SystemClockPort)

        assertThrows(IllegalStateException::class.java) { adapter.execute("m", "p") }
    }
}
