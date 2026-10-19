
package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.LanguageDto
import com.stacklens.application.port.inbound.config.AiConfigUseCase
import com.stacklens.domain.model.AiConfig
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config")
class LanguageConfigApiController(
    private val configManagement: AiConfigUseCase,
) {
    @GetMapping("/language")
    fun getLanguage(): Map<String, String> = mapOf("language" to LanguageConfigSupport.resolve(configManagement.getAiConfig()?.language))

    @PutMapping("/language")
    fun setLanguage(
        @RequestBody dto: LanguageDto,
    ): Map<String, String> {
        val existing = configManagement.getAiConfig() ?: AiConfig(id = 1L)
        existing.language = LanguageConfigSupport.resolve(dto.language)
        configManagement.saveAiConfig(existing)
        return mapOf("language" to existing.language)
    }
}
