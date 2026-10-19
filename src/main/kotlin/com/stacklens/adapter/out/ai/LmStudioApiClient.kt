package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.util.quietly
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class LmStudioApiClient(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(LmStudioApiClient::class.java)

    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1)
            .build()

    private val aiHttpClientExecutor: AiHttpClientExecutor = AiHttpClientExecutor()

    fun isModelLoaded(
        baseUrl: String,
        model: String,
    ): Boolean =
        runCatching {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create("${baseUrl.trimEnd('/')}/api/v1/models"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build()
            val response =
                aiHttpClientExecutor.sendWithTimeout(
                    httpClient,
                    request,
                    HttpResponse.BodyHandlers.ofString(),
                    10L,
                )
            if (response.statusCode() == 200) {
                @Suppress("UNCHECKED_CAST")
                val models = objectMapper.readValue(response.body(), Map::class.java)["models"] as? List<*>
                models?.any { entry ->
                    val m = entry as? Map<*, *> ?: return@any false
                    m["key"] == model && (m["loaded_instances"] as? List<*>)?.isNotEmpty() == true
                } == true
            } else {
                false
            }
        }.getOrDefault(false)

    fun loadModel(
        baseUrl: String,
        model: String,
        timeoutSeconds: Long,
        isCancelled: (() -> Boolean)? = null,
    ) {
        val url = "${baseUrl.trimEnd('/')}/api/v1/models/load"
        val body = objectMapper.writeValueAsString(mapOf("model" to model))
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        runCatching {
            aiHttpClientExecutor.sendWithTimeout(
                httpClient,
                request,
                HttpResponse.BodyHandlers.ofString(),
                timeoutSeconds,
                isCancelled,
            )
        }.onSuccess {
            if (it.statusCode() !in
                200..299
            ) {
                log.warn("LM Studio model loading HTTP {}: {}", it.statusCode(), it.body().take(400))
            }
        }.onFailure { log.warn("LM Studio model could not be loaded: {}", it.message) }
    }

    fun sendRequest(
        baseUrl: String,
        requestBody: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)? = null,
    ): String {
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutOverride))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        val response =
            try {
                aiHttpClientExecutor.sendWithTimeout(
                    httpClient,
                    request,
                    HttpResponse.BodyHandlers.ofString(),
                    timeoutOverride,
                    isCancelled,
                )
            } catch (
                e: ConnectException,
            ) {
                throw IllegalStateException(
                    "LM Studio is not reachable at $baseUrl. Please make sure LM Studio is running and the URL is configured correctly.",
                    e,
                )
            }
        if (response.statusCode() != 200) error("LM Studio error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        return response.body()
    }

    fun streamRequest(
        baseUrl: String,
        requestBody: String,
        timeoutOverride: Long,
        flushIntervalMs: Long,
        isCancelled: (() -> Boolean)? = null,
        onPartialOutput: (String) -> Unit,
    ): String {
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutOverride))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        val response =
            try {
                aiHttpClientExecutor.sendWithTimeout(
                    httpClient,
                    request,
                    HttpResponse.BodyHandlers.ofInputStream(),
                    timeoutOverride,
                    isCancelled,
                )
            } catch (
                e: ConnectException,
            ) {
                throw IllegalStateException(
                    "LM Studio is not reachable at $baseUrl. Please make sure LM Studio is running and the URL is configured correctly.",
                    e,
                )
            }
        if (response.statusCode() !=
            200
        ) {
            val errorBody = response.body().utf8Reader().use { it.readText() }
            error("LM Studio error (HTTP ${response.statusCode()}): ${errorBody.take(400)}")
        }

        val sb = StringBuilder()
        var lastFlushMs = System.currentTimeMillis()
        var finalResult: String? = null
        BufferedReader(InputStreamReader(response.body())).use { reader ->
            reader.forEachLine { line ->
                if (isCancelled?.invoke() == true) throw InterruptedException("lmstudio stream cancelled")
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") return@forEachLine
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        val chunk = objectMapper.readValue(data, Map::class.java)
                        if (chunk["type"] == "chat.end") {
                            val result = chunk["result"] as? Map<*, *> ?: return@forEachLine
                            finalResult =
                                objectMapper.readValue(objectMapper.writeValueAsString(result), Map::class.java)["output"] as? String
                            return@forEachLine
                        }
                        val content =
                            if (chunk["type"] == "message.delta") {
                                chunk["content"] as? String
                            } else {
                                val choices = chunk["choices"] as? List<*> ?: return@forEachLine
                                val first = choices.firstOrNull() as? Map<*, *> ?: return@forEachLine
                                val delta = first["delta"] as? Map<*, *> ?: return@forEachLine
                                delta["content"] as? String
                            } ?: return@forEachLine
                        sb.append(content)
                        val now = System.currentTimeMillis()
                        if (flushIntervalMs == 0L ||
                            now - lastFlushMs >= flushIntervalMs
                        ) {
                            quietly { onPartialOutput(sb.toString()) }
                            lastFlushMs = now
                        }
                    }
                }
            }
        }
        return finalResult?.trim()?.takeIf { it.isNotBlank() } ?: sb.toString().trim()
    }

    fun fetchAvailableModels(baseUrl: String): List<String> {
        val url = "${baseUrl.trimEnd('/')}/v1/models"
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .GET()
                .build()
        val response =
            aiHttpClientExecutor.sendWithTimeout(
                httpClient,
                request,
                HttpResponse.BodyHandlers.ofString(),
                10L,
            )
        if (response.statusCode() != 200) error("LM Studio /v1/models error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        @Suppress("UNCHECKED_CAST")
        val map = objectMapper.readValue(response.body(), Map::class.java)
        val data = map["data"] as? List<*> ?: return emptyList()
        return data.mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
    }
}
