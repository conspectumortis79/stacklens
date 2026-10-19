package com.stacklens.application.port.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiChatPortReadinessTest {
    private val port =
        object : AiChatPort {
            override fun resolveToken(): String? = null

            override fun resolveModel(): String = "test-model"

            override fun resolveLanguage(): String = "de"

            override fun execute(
                model: String,
                prompt: String,
                timeoutOverride: Long?,
                attachments: List<AiAttachment>,
            ): String = "ok"
        }

    @Test
    fun `a provider reports readiness independently of credentials`() {
        assertTrue(port.isReady())
        assertNull(port.resolveToken())
        assertEquals("test-model", port.resolveModel())
        assertEquals("Unknown", port.providerLabel)
    }
}
