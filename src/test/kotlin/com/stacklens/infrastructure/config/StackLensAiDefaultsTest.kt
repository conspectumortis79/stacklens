package com.stacklens.infrastructure.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StackLensAiDefaultsTest {
    private fun defaults(properties: StackLensProperties = StackLensProperties()) = StackLensAiDefaults(properties)

    @Test
    fun `the shipped default base url is the QwenCloud international endpoint`() {
        assertEquals("https://dashscope-intl.aliyuncs.com/compatible-mode/v1", defaults().compatibleDefaultBaseUrl())
    }

    @Test
    fun `a configured base url wins over the shipped one`() {
        val properties =
            StackLensProperties(
                ai =
                    AiProperties(
                        compatible = OpenAiCompatibleProperties(defaultBaseUrl = "http://localhost:8000/v1"),
                    ),
            )
        assertEquals("http://localhost:8000/v1", defaults(properties).compatibleDefaultBaseUrl())
    }
}
