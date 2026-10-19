package com.stacklens.adapter.out.knowledge.embedding

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.application.port.out.knowledge.EmbeddingPort
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.WebClientBuilderDsl.bearerToken
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class OpenRouterEmbeddingAdapter(
    private val httpFactory: ReactiveHttpClientFactory,
    @Value("\${embedding.openrouter.base-url:https://openrouter.ai}")
    private val configuredBaseUrl: String,
) : EmbeddingPort {
    private val log = LoggerFactory.getLogger(OpenRouterEmbeddingAdapter::class.java)

    override fun provider(): KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OPENROUTER

    override fun embed(
        config: KnowledgeEmbeddingConfig,
        texts: List<String>,
    ): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val key =
            config.openRouterApiKey?.takeIf { it.isNotBlank() }
                ?: error("OpenRouter API key not configured in the knowledge embedding configuration.")

        val client =
            httpFactory
                .builder()
                .baseUrl(resolvedBaseUrl(config))
                .bearerToken(key)
                .build()

        val payload: Map<String, Any?> =
            mapOf(
                "model" to config.model,
                "input" to texts,
            )
        val root =
            client
                .post()
                .uri("/api/v1/embeddings")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono<JsonNode>()
                .block(Duration.ofSeconds(60))
                ?: error("OpenRouter returned no response for model '${config.model}'")

        val data = root.path("data")
        if (!data.isArray) error("OpenRouter response missing 'data' array: ${root.toString().take(200)}")
        val vectors = arrayOfNulls<FloatArray>(data.size())
        data.forEach { node ->
            val idx = node.path("index").asInt(-1)
            if (idx < 0 || idx >= vectors.size) error("OpenRouter response contained out-of-range index: $idx")
            val arr = node.path("embedding")
            vectors[idx] = FloatArray(arr.size()) { arr.get(it).asDouble().toFloat() }
        }
        return vectors.map { it ?: error("OpenRouter response missing embedding for an index") }
    }

    override fun availableModels(
        config: KnowledgeEmbeddingConfig,
        overrides: EmbeddingListingOverrides,
    ): List<String> {
        val key =
            (overrides.apiKey ?: config.openRouterApiKey)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val base = resolvedBaseUrl(config)
        return runCatching {
            val client =
                httpFactory
                    .builder()
                    .baseUrl(base)
                    .bearerToken(key)
                    .build()
            val root =
                client
                    .get()
                    .uri("/api/v1/embeddings/models")
                    .retrieve()
                    .bodyToMono<JsonNode>()
                    .onErrorResume { Mono.empty() }
                    .block(Duration.ofSeconds(15))
                    ?: return@runCatching emptyList()
            root
                .path("data")
                .mapNotNull { entry ->
                    val id = entry.path("id").asText(null) ?: return@mapNotNull null
                    val modalities = entry.path("architecture").path("output_modalities")
                    if (modalities.isArray && modalities.any { it.asText("") == "embeddings" }) id else null
                }.filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }.onFailure { log.warn("OpenRouter available models failed: {}", it.message) }
            .getOrDefault(emptyList())
    }

    override fun resolvedBaseUrl(config: KnowledgeEmbeddingConfig): String = configuredBaseUrl.trimEnd('/')
}
