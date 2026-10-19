package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.AiExportDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigImportMapperOllamaNumCtxCoverageTest {
    private val mapper = ConfigImportMapper()

    @Test
    fun `a positive ollama num ctx is taken from the export dto`() {
        val config = mapper.toAiConfig(AiExportDto(ollamaNumCtx = 8192), "copilot", 4096)

        assertEquals(8192, config?.ollamaNumCtx)
    }

    @Test
    fun `a zero ollama num ctx falls back to the default`() {
        val config = mapper.toAiConfig(AiExportDto(ollamaNumCtx = 0), "copilot", 4096)

        assertEquals(4096, config?.ollamaNumCtx)
    }

    @Test
    fun `a missing ollama num ctx falls back to the default`() {
        val config = mapper.toAiConfig(AiExportDto(), "copilot", 4096)

        assertEquals(4096, config?.ollamaNumCtx)
    }

    @Test
    fun `a missing ai section yields no ai config`() {
        assertNull(mapper.toAiConfig(null, "copilot", 4096))
    }
}
