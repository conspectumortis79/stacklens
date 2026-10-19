package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeConfigUseCase
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/knowledge")
class KnowledgeConfigController(
    private val configUseCase: KnowledgeConfigUseCase,
) {
    @GetMapping("/config")
    fun getConfig(): KnowledgeEmbeddingConfig = configUseCase.getConfig()

    @PutMapping("/config")
    fun saveConfig(
        @RequestBody config: KnowledgeEmbeddingConfig,
    ): KnowledgeEmbeddingConfig = configUseCase.saveConfig(config)

    @PostMapping("/config/test")
    fun testConfig() = configUseCase.testConnection()

    @GetMapping("/config/models")
    fun availableModels(
        @RequestParam(name = "provider", required = false) provider: String?,
        @RequestParam(name = "ollamaUrl", required = false) ollamaUrl: String?,
        @RequestParam(name = "openRouterApiKey", required = false) openRouterApiKey: String?,
    ): Map<String, Any> {
        val enumProvider =
            provider
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    runCatching {
                        com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
                            .valueOf(it)
                    }.getOrNull()
                }
        val models = configUseCase.availableModels(enumProvider, ollamaUrl, openRouterApiKey)
        return mapOf("models" to models)
    }
}
