package com.stacklens.adapter.inbound.rest.config

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiConfigDtoMapperCompatibleTest {
    private val mapper = AiConfigDtoMapper()

    private val mask = "••••••••"

    private fun config() =
        AiConfig(
            id = 1L,
            model = "qwen3.8-flash",
            aiProvider = "compatible",
            compatibleApiKey = "sk-secret-value",
            compatibleBaseUrl = "https://coding-intl.dashscope.aliyuncs.com/v1",
        )

    @Test
    fun `toDto masks the compatible key and reports it as set`() {
        val dto = mapper.toDto(config())
        assertEquals(mask, dto.compatibleApiKey)
        assertTrue(dto.compatibleApiKeySet)
        assertEquals("https://coding-intl.dashscope.aliyuncs.com/v1", dto.compatibleBaseUrl)
    }

    @Test
    fun `toDto reports an absent compatible key as empty and unset`() {
        val dto = mapper.toDto(AiConfig(id = 1L))
        assertEquals("", dto.compatibleApiKey)
        assertFalse(dto.compatibleApiKeySet)
        assertEquals("", dto.compatibleBaseUrl)
    }

    @Test
    fun `toDto with a null config yields an unset compatible key`() {
        val dto = mapper.toDto(null)
        assertEquals("", dto.compatibleApiKey)
        assertFalse(dto.compatibleApiKeySet)
    }

    @Test
    fun `the resolved-model overload carries the compatible fields`() {
        val dto = mapper.toDto(config(), "deepseek-v4-flash")
        assertEquals(mask, dto.compatibleApiKey)
        assertTrue(dto.compatibleApiKeySet)
        assertEquals("deepseek-v4-flash", dto.model)
        assertEquals("https://coding-intl.dashscope.aliyuncs.com/v1", dto.compatibleBaseUrl)
    }
}
