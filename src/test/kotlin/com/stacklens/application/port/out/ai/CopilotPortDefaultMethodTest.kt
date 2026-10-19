package com.stacklens.application.port.out.ai

import com.stacklens.application.port.out.ai.AiAttachment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CopilotPortDefaultMethodTest {
    @Test
    fun `default providerLabel returns Unknown`() {
        val port =
            object : CopilotPort {
                override fun resolveToken(): String? = null

                override fun resolveModel(): String = "test-model"

                override fun resolveLanguage(): String = "de"

                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = "output"
            }
        assertEquals("Unknown", port.providerLabel)
    }

    @Test
    fun `default isReady returns true`() {
        val port =
            object : CopilotPort {
                override fun resolveToken(): String? = null

                override fun resolveModel(): String = "test-model"

                override fun resolveLanguage(): String = "de"

                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = "output"
            }
        assertEquals(true, port.isReady())
    }

    @Test
    fun `default supportsAttachments returns true`() {
        val port =
            object : CopilotPort {
                override fun resolveToken(): String? = null

                override fun resolveModel(): String = "test-model"

                override fun resolveLanguage(): String = "de"

                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = "output"
            }
        assertEquals(true, port.supportsAttachments)
    }

    @Test
    fun `override of providerLabel returns custom value`() {
        val port =
            object : CopilotPort {
                override val providerLabel: String = "CustomProvider"

                override fun resolveToken(): String? = null

                override fun resolveModel(): String = "test-model"

                override fun resolveLanguage(): String = "de"

                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = "output"
            }
        assertEquals("CustomProvider", port.providerLabel)
    }
}
