package com.stacklens.domain.enums

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderStrategyModelResolutionTest {
    @Test fun `blank live models fall through to the provider default`() {
        assertEquals("default", ProviderStrategy.resolveModelStatic(null, AiConfig(model = null), "default"))
        assertEquals(
            "stored",
            ProviderStrategy.resolveModelStatic("  ", AiConfig(model = "stored"), "default"),
        )
    }

    @Test fun `the interface default resolves without an explicit live model argument`() {
        assertEquals("dto", ProviderStrategies.COMPATIBLE.resolveModel("dto", AiConfig()))
        assertEquals(
            ProviderStrategies.COMPATIBLE.defaultModel,
            ProviderStrategies.COMPATIBLE.resolveModel(null, AiConfig(model = " ")),
        )
        assertEquals("stored-model", ProviderStrategies.COMPATIBLE.resolveModel(null, AiConfig(model = "stored-model")))
    }

    @Test fun `lmstudio prefers a live model while the stored provider is lmstudio`() {
        val strategy = ProviderStrategies.LMSTUDIO
        val storedBlank = AiConfig(aiProvider = "lmstudio", model = "   ")

        assertEquals("live-model", strategy.resolveModel(null, null, storedBlank, "live-model"))
        assertEquals("local-model", strategy.resolveModel(null, null, storedBlank, "  "))
        assertEquals("local-model", strategy.resolveModel(null, null, storedBlank, null))
    }

    @Test fun `lmstudio ignores stored models of a different provider`() {
        val strategy = ProviderStrategies.LMSTUDIO

        assertEquals("live-model", strategy.resolveModel(null, null, AiConfig(aiProvider = "copilot", model = null), "live-model"))
        assertEquals(
            "live-model",
            strategy.resolveModel(null, null, AiConfig(aiProvider = "copilot", model = "   "), "live-model"),
        )
        assertEquals(
            "local-model",
            strategy.resolveModel(null, null, AiConfig(aiProvider = "copilot", model = "gpt-5.4-mini"), "live-model"),
        )
    }
}
