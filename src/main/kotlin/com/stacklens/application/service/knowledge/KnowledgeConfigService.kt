package com.stacklens.application.service.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeConfigUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeConnectionTestResult
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig

@ApplicationService
class KnowledgeConfigService(
    private val configPort: KnowledgeEmbeddingConfigPort,
    private val dispatcher: KnowledgeEmbeddingDispatcher,
) : KnowledgeConfigUseCase {
    override fun getConfig(): KnowledgeEmbeddingConfig = configPort.get()

    override fun saveConfig(config: KnowledgeEmbeddingConfig): KnowledgeEmbeddingConfig = configPort.save(config)

    override fun testConnection(): KnowledgeConnectionTestResult {
        val config = configPort.get()
        return try {
            val dim = dispatcher.probe()
            KnowledgeConnectionTestResult(
                ok = true,
                provider = config.provider,
                model = config.model,
                dimension = dim,
            )
        } catch (e: Exception) {
            KnowledgeConnectionTestResult(
                ok = false,
                provider = config.provider,
                model = config.model,
                dimension = null,
                errorMessage = e.message,
            )
        }
    }

    override fun availableModels(
        provider: com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider?,
        ollamaUrl: String?,
        openRouterApiKey: String?,
    ): List<String> = dispatcher.availableModels(provider, ollamaUrl, openRouterApiKey)
}
