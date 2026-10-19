package com.stacklens.adapter.out.knowledge.embedding

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.application.port.out.knowledge.EmbeddingPort
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class OllamaEmbeddingAdapter(
    private val httpFactory: ReactiveHttpClientFactory,
    @Value("\${OLLAMA_URL:}")
    private val envOllamaUrl: String,
    private val containerRuntimeProbe: ContainerRuntimeProbePort,
) : EmbeddingPort {
    private val log = LoggerFactory.getLogger(OllamaEmbeddingAdapter::class.java)

    private val defaultOllamaUrl: String =
        envOllamaUrl.takeIf { it.isNotBlank() } ?: "http://localhost:11434"

    override fun provider(): KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OLLAMA

    override fun embed(
        config: KnowledgeEmbeddingConfig,
        texts: List<String>,
    ): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val base = resolvedBaseUrl(config)
        val client =
            httpFactory
                .builder()
                .baseUrl(base)
                .build()

        val request =
            mapOf(
                "model" to config.model,
                "input" to texts,
            )
        val response =
            client
                .post()
                .uri("/api/embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono<JsonNode>()
                .block(Duration.ofSeconds(60))
                ?: error("Ollama returned no response for model '${config.model}'")

        val vectors = response.path("embeddings")
        if (!vectors.isArray) {
            error("Ollama response did not contain an 'embeddings' array: ${response.toString().take(200)}")
        }
        return vectors.map { node ->
            if (node.any { !it.isNumber }) {
                error("Ollama embedding contained non-float values")
            }
            FloatArray(node.size()) { index -> node[index].floatValue() }
        }
    }

    override fun availableModels(
        config: KnowledgeEmbeddingConfig,
        overrides: EmbeddingListingOverrides,
    ): List<String> {
        val base = resolvedBaseUrl(config, overrides.baseUrl)
        val installed =
            runCatching {
                val client: WebClient = httpFactory.builder().baseUrl(base).build()
                val root =
                    client
                        .get()
                        .uri("/api/tags")
                        .retrieve()
                        .bodyToMono<JsonNode>()
                        .onErrorResume { Mono.empty() }
                        .block(Duration.ofSeconds(15))
                        ?: return@runCatching emptyList<String>()
                root
                    .path("models")
                    .mapNotNull { it.path("name").asText(null) }
                    .filter { it.isNotBlank() }
                    .distinct()
            }.onFailure { log.warn("Ollama available models failed: {}", it.message) }
                .getOrDefault(emptyList())
        if (installed.isEmpty()) return emptyList()
        return installed.filter { model ->
            val caps = capabilitiesCache.getOrLoad(base, model) { fetchCapabilities(base, model) }
            caps.contains("embedding")
        }
    }

    override fun resolvedBaseUrl(config: KnowledgeEmbeddingConfig): String = resolvedBaseUrl(config, null)

    private fun resolvedBaseUrl(
        config: KnowledgeEmbeddingConfig,
        baseUrlOverride: String?,
    ): String {
        val resolved = (baseUrlOverride ?: config.ollamaUrl).trimEnd('/').ifBlank { defaultOllamaUrl }
        val inContainer = containerRuntimeProbe.detectActiveRuntime() == ContainerRuntime.DOCKER
        return if (inContainer) resolved.replace("://localhost", "://host.docker.internal") else resolved
    }

    private val capabilitiesCache = CapabilitiesCache()

    private fun fetchCapabilities(
        base: String,
        model: String,
    ): Set<String> {
        return runCatching {
            val client: WebClient = httpFactory.builder().baseUrl(base).build()
            val resp =
                client
                    .post()
                    .uri("/api/show")
                    .bodyValue(mapOf("name" to model))
                    .retrieve()
                    .bodyToMono<JsonNode>()
                    .onErrorResume { Mono.empty() }
                    .block(Duration.ofSeconds(5))
                    ?: return emptySet()
            resp
                .path("capabilities")
                .mapNotNull { it.asText(null) }
                .filter { it.isNotBlank() }
                .toSet()
        }.onFailure {
            log.debug("Ollama /api/show failed for model '{}': {}", model, it.message)
        }.getOrDefault(emptySet())
    }

    private class CapabilitiesCache {
        private val cache = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

        fun getOrLoad(
            base: String,
            model: String,
            loader: () -> Set<String>,
        ): Set<String> = cache.getOrPut(key(base, model)) { loader() }

        private fun key(
            base: String,
            model: String,
        ): String = "$base::$model"
    }
}
