package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.AiExportDto
import com.stacklens.adapter.inbound.rest.ConfigExportDto
import com.stacklens.adapter.inbound.rest.DeveloperOptionsExportDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigImportFlagShapeCoverageTest {
    private val mapper = ConfigImportMapper()

    @Test
    fun `a file without any ai related field does not replace the ai config`() {
        val request = mapper.toRequest(ConfigExportDto())

        assertEquals(false, request.replaceAiConfig)
    }

    @Test
    fun `a language alone is enough to replace the ai config`() {
        val request = mapper.toRequest(ConfigExportDto(language = "de"))

        assertEquals(true, request.replaceAiConfig)
    }

    @Test
    fun `developer options alone are enough to replace the ai config`() {
        val request = mapper.toRequest(ConfigExportDto(developerOptions = DeveloperOptionsExportDto()))

        assertEquals(true, request.replaceAiConfig)
    }

    @Test
    fun `a model list without language or options still replaces the ai config`() {
        val request =
            mapper.toRequest(
                ConfigExportDto(ai = AiExportDto(availableModels = listOf("gpt-one", "gpt-two"))),
            )

        assertEquals(true, request.replaceAiConfig)
        assertEquals("gpt-one,gpt-two", request.availableModels)
    }

    @Test
    fun `an ai section without a model list keeps the stored models`() {
        val request = mapper.toRequest(ConfigExportDto(ai = AiExportDto(availableModels = null)))

        assertEquals(false, request.replaceAiConfig)
        assertEquals(null, request.availableModels)
    }
}
