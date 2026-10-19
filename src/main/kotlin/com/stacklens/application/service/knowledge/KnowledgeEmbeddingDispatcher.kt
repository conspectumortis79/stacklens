package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.application.port.out.knowledge.EmbeddingPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider

@ApplicationService
class KnowledgeEmbeddingDispatcher(
    private val configPort: KnowledgeEmbeddingConfigPort,
    private val providers: List<EmbeddingPort>,
) {
    private val byName: Map<KnowledgeEmbeddingProvider, EmbeddingPort> by lazy {
        providers.associateBy { it.provider() }
    }

    fun embed(texts: List<String>): EmbeddingResult {
        if (texts.isEmpty()) return EmbeddingResult(emptyList(), 0, "", "")
        val config = configPort.get()
        val adapter = resolveAdapter(config.provider)
        val raw =
            try {
                adapter.embed(config, texts)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Embedding call failed for provider=${config.provider} model=${config.model}: ${e.message}",
                    e,
                )
            }
        require(raw.size == texts.size) {
            "Embedding adapter returned ${raw.size} vectors for ${texts.size} inputs"
        }
        val dimension = raw.first().size
        require(dimension > 0) { "Embedding adapter returned zero-dimensional vectors" }
        raw.forEachIndexed { idx, vector ->
            require(vector.size == dimension) {
                "Embedding vector $idx has dimension ${vector.size}, expected $dimension"
            }
            vector.forEach { value ->
                require(!value.isNaN() && !value.isInfinite()) {
                    "Embedding vector $idx contains non-finite values"
                }
            }
        }
        val profile = buildProfile(config, adapter, dimension)
        return EmbeddingResult(raw, dimension, profile, config.model)
    }

    fun availableModels(
        provider: KnowledgeEmbeddingProvider? = null,
        ollamaUrl: String? = null,
        openRouterApiKey: String? = null,
    ): List<String> {
        val config = configPort.get()
        val effectiveProvider = provider ?: config.provider
        val adapter = byName[effectiveProvider] ?: return emptyList()
        return adapter.availableModels(config, EmbeddingListingOverrides(baseUrl = ollamaUrl, apiKey = openRouterApiKey))
    }

    fun probe(): Int = embed(listOf("ping")).dimension

    private fun resolveAdapter(provider: KnowledgeEmbeddingProvider): EmbeddingPort =
        byName[provider]
            ?: error(
                "No EmbeddingPort registered for provider $provider. " +
                    "Available: ${byName.keys}",
            )

    private fun buildProfile(
        config: KnowledgeEmbeddingConfig,
        adapter: EmbeddingPort,
        dimension: Int,
    ): String =
        "${config.provider.name.lowercase()}|" +
            "${adapter.resolvedBaseUrl(config).trim()}|" +
            "${config.model}|$dimension|chunker-v1|${config.chunkSize}|${config.chunkOverlap}"
}

data class EmbeddingResult(
    val vectors: List<FloatArray>,
    val dimension: Int,
    val profile: String,
    val model: String,
)
