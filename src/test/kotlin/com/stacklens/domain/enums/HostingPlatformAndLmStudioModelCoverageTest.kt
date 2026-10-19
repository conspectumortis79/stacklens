package com.stacklens.domain.enums

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HostingPlatformAndLmStudioModelCoverageTest {
    @Test
    fun `a wire value resolves to the platform that owns it`() {
        assertEquals(CodeHostingPlatform.Github, CodeHostingPlatform.fromWire("  GitHub "))
        assertEquals(CodeHostingPlatform.Gitlab, CodeHostingPlatform.fromWire("\tGITLAB\n"))
    }

    @Test
    fun `an unowned wire value resolves to the unscoped platform carrying the raw value`() {
        assertEquals(CodeHostingPlatform.Unknown("bitbucket"), CodeHostingPlatform.fromWire("bitbucket"))
        assertEquals(CodeHostingPlatform.Unknown(""), CodeHostingPlatform.fromWire(null))
    }

    @Test
    fun `the platform catalogue exposes the two known hosts`() {
        assertEquals(listOf(CodeHostingPlatform.Gitlab, CodeHostingPlatform.Github), CodeHostingPlatform.known)
        assertEquals(listOf("gitlab", "github"), CodeHostingPlatform.allIds)
    }

    @Test
    fun `a live lmstudio model is only preferred while no other model is known`() {
        val otherProvider = AiConfig(aiProvider = ProviderStrategies.COPILOT_ID, model = null)

        assertEquals("live-model", ProviderStrategies.LMSTUDIO.resolveModel(null, null, otherProvider, "live-model"))
        assertEquals(
            ProviderStrategies.LMSTUDIO.defaultModel,
            ProviderStrategies.LMSTUDIO.resolveModel(null, null, otherProvider, null),
        )
        assertEquals(
            ProviderStrategies.LMSTUDIO.defaultModel,
            ProviderStrategies.LMSTUDIO.resolveModel(null, null, otherProvider, "   "),
        )
    }

    @Test
    fun `a stored model of another provider wins over the lmstudio default`() {
        val stored = AiConfig(aiProvider = ProviderStrategies.OPENAI_ID, model = "gpt-4")

        assertEquals(
            ProviderStrategies.LMSTUDIO.defaultModel,
            ProviderStrategies.LMSTUDIO.resolveModel(null, null, stored, "live-model"),
        )
    }

    @Test
    fun `the lmstudio provider keeps its own stored model before the live one`() {
        val lmStudio = AiConfig(aiProvider = ProviderStrategies.LMSTUDIO_ID, model = "stored-llama")

        assertEquals("stored-llama", ProviderStrategies.LMSTUDIO.resolveModel(null, null, lmStudio, "live-model"))
        assertEquals("explicit", ProviderStrategies.LMSTUDIO.resolveModel("explicit", null, lmStudio, "live-model"))
    }
}
