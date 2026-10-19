package com.stacklens.adapter.out.knowledge.embedding

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.application.port.out.knowledge.EmbeddingPort
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

@Component
class OpenAiEmbeddingAdapter(
    private val httpFactory: ReactiveHttpClientFactory,
) : EmbeddingPort {
    override fun provider(): KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OPENAI

    override fun embed(
        config: KnowledgeEmbeddingConfig,
        texts: List<String>,
    ): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val key =
            config.openAiApiKey?.takeIf { it.isNotBlank() }
                ?: error("OpenAI API key not configured in the knowledge embedding configuration.")

        val client =
            httpFactory
                .builder()
                .baseUrl(resolvedBaseUrl(config))
                .defaultHeader("Authorization", "Bearer $key")
                .build()

        val payload: Map<String, Any?> =
            mapOf(
                "model" to config.model,
                "input" to texts,
            )
        val root =
            client
                .post()
                .uri("/v1/embeddings")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono<JsonNode>()
                .block(Duration.ofSeconds(60))
                ?: error("OpenAI returned no response for model '${config.model}'")

        val data = root.path("data")
        if (!data.isArray) error("OpenAI response missing 'data' array: ${root.toString().take(200)}")
        val byIndex = IntArray(data.size())
        val vectors = arrayOfNulls<FloatArray>(data.size())
        data.forEach { node ->
            val idx = node.path("index").asInt(-1)
            if (idx < 0 || idx >= byIndex.size) error("OpenAI response contained out-of-range index: $idx")
            byIndex[idx] = 1
            val arr = node.path("embedding")
            vectors[idx] = FloatArray(arr.size()) { arr.get(it).asDouble().toFloat() }
        }
        return vectors.map { it ?: error("OpenAI response missing embedding for an index") }
    }

    override fun availableModels(
        config: KnowledgeEmbeddingConfig,
        overrides: EmbeddingListingOverrides,
    ): List<String> = listOf("text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002")

    override fun resolvedBaseUrl(config: KnowledgeEmbeddingConfig): String = "https://api.openai.com"
}
