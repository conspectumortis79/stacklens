package com.stacklens.domain.enums

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderStrategyContractTest {
    private val strategies = AiProvider.entries.map { it.strategy }

    @Test
    fun `a submitted model always wins across every strategy`() {
        strategies.forEach { strategy ->
            assertEquals(
                "submitted",
                strategy.resolveModel("submitted", AiConfig(model = "stored", aiProvider = strategy.id)),
            )
        }
    }

    @Test
    fun `resolution never returns blank across every strategy`() {
        strategies.forEach { strategy ->
            assertTrue(strategy.resolveModel(null, AiConfig()).isNotBlank())
            assertTrue(strategy.resolveModel("   ", AiConfig(aiProvider = strategy.id, model = "  ")).isNotBlank())
        }
    }

    @Test
    fun `resolution always falls back to the provider default across every strategy`() {
        strategies.forEach { strategy ->
            assertEquals(strategy.defaultModel, strategy.resolveModel(null, AiConfig(aiProvider = strategy.id)))
        }
    }
}
