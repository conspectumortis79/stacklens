package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.util.closingAuto
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class
OllamaAdapter(
    private val defaultOllamaUrl: String = "http://localhost:11434",
    private val timeoutSeconds: Long = 180L,
    private val aiConfigRepo: AiConfigPort,
    private val objectMapper: ObjectMapper,
    private val promptLoader: PromptReaderPort,
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build(),
    private val aiHttpClientExecutor: AiHttpClientExecutor = AiHttpClientExecutor(),
    private val containerRuntimeProbe: ContainerRuntimeProbePort,
) : CopilotStreamingPort {
    private val log = LoggerFactory.getLogger(OllamaAdapter::class.java)

    override val providerLabel: String = "Ollama"

    internal val toolLoop: OpenAiCompatibleToolLoop =
        OpenAiCompatibleToolLoop(
            objectMapper = objectMapper,
            transport =
                object : OpenAiCompatibleToolLoop.ChatTransport {
                    override val providerLabel: String = "Ollama"

                    override fun send(
                        body: String,
                        timeoutSeconds: Long,
                    ): String = sendChatRequest(body, timeoutSeconds, null)
                },
            normalizer = OpenAiCompatibleToolLoop.ContentNormalizer.stripThinkBlocks(),
        )

    internal fun processToolLoopIteration(
        iteration: Int,
        respJson: String,
        messages: MutableList<Map<String, Any>>,
        allText: StringBuilder,
        repoDir: File,
        onPartialOutput: (String) -> Unit,
    ): Boolean = toolLoop.processToolLoopIteration(iteration, respJson, messages, allText, repoDir, onPartialOutput)

    private fun currentAiConfig(): AiConfig? = aiConfigRepo.findAiConfig()

    internal fun resolveOllamaUrl(): String {
        val cfg = currentAiConfig()
        val configured = cfg?.ollamaUrl
        val url = if (configured.isNullOrBlank()) defaultOllamaUrl else configured
        val isInContainer = containerRuntimeProbe.detectActiveRuntime() != ContainerRuntime.NONE
        return if (isInContainer) url.replace("://localhost", "://host.docker.internal") else url
    }

    internal fun resolveNumCtx(): Int {
        val cfg = currentAiConfig()
        val n = cfg?.ollamaNumCtx ?: 0
        return if (n > 0) n else 32768
    }

    override fun resolveToken(): String? = null

    override fun isReady(): Boolean = true

    override fun resolveModel(): String {
        val cfg = currentAiConfig()
        val m = cfg?.model
        return if (m.isNullOrBlank()) "qwen3-coder:30b-a3b-q4_k_m" else m
    }

    override fun resolveLanguage(): String {
        val cfg = currentAiConfig()
        val l = cfg?.language
        return if (l.isNullOrBlank()) "de" else l
    }

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
    ): String {
        val baseUrl = resolveOllamaUrl()
        val numCtx = resolveNumCtx()
        val promptTokensEst = prompt.length / 4
        val remainingEst = numCtx - promptTokensEst
        if (remainingEst < numCtx * 0.2) {
            log.warn(
                "Ollama num_ctx running low! num_ctx={} | Prompt ~{} tokens → only ~{} tokens remaining for response. " +
                    "Increase num_ctx (Settings → AI → Context).",
                numCtx,
                promptTokensEst,
                remainingEst,
            )
        }
        log.info(
            "Ollama request | Model: {} | URL: {} | Prompt: {} chars (~{} tokens) | num_ctx: {} | remaining capacity ~{} tokens | Attachments: {}",
            model,
            baseUrl,
            prompt.length,
            promptTokensEst,
            numCtx,
            remainingEst,
            attachments.size,
        )
        val startMs = System.currentTimeMillis()

        val requestBody = buildRequestBody(model, prompt, stream = false, attachments = attachments)
        val response = sendChatRequest(requestBody, timeoutOverride ?: timeoutSeconds, null)
        val result = parseCompletionResponse(response)

        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000
        log.info("Ollama completed ({} seconds) | Response: {} chars", elapsedSec, result.length)
        return result
    }

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        val baseUrl = resolveOllamaUrl()
        val numCtx = resolveNumCtx()
        val promptTokensEst = prompt.length / 4
        val remainingEst = numCtx - promptTokensEst
        if (remainingEst < numCtx * 0.2) {
            log.warn(
                "Ollama num_ctx running low! num_ctx={} | Prompt ~{} tokens → only ~{} tokens remaining for response. " +
                    "Increase num_ctx (Settings → AI → Context).",
                numCtx,
                promptTokensEst,
                remainingEst,
            )
        }
        log.info(
            "Ollama streaming | Model: {} | Prompt: {} chars (~{} tokens) | num_ctx: {} | remaining capacity ~{} tokens | Attachments: {}",
            model,
            prompt.length,
            promptTokensEst,
            numCtx,
            remainingEst,
            attachments.size,
        )
        val startMs = System.currentTimeMillis()

        val requestBody = buildRequestBody(model, prompt, stream = true, attachments = attachments)
        val result = streamRequest(baseUrl, requestBody, timeoutOverride ?: timeoutSeconds, flushIntervalMs = 1_000L, isCancelled, onPartialOutput)

        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000
        log.info("Ollama streaming completed ({} seconds) | Response: {} chars", elapsedSec, result.length)
        return result
    }

    override fun executeWithStreamingPerLine(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        repoDir: File?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        val effectiveRepoDir = repoDir
        val effectiveTimeout = timeoutOverride ?: timeoutSeconds
        if (effectiveRepoDir != null) {
            if (!effectiveRepoDir.exists() || !effectiveRepoDir.isDirectory) {
                log.warn("[RepoFileTools] Ollama: repoDir does not exist: {} — falling back to plain call", effectiveRepoDir)
                return executeWithStreaming(model, prompt, effectiveTimeout, isCancelled, onPartialOutput, attachments)
            }
            return executeWithRepoTools(model, prompt, effectiveTimeout, effectiveRepoDir, onPartialOutput, isCancelled)
        }
        val baseUrl = resolveOllamaUrl()
        return streamRequest(
            baseUrl,
            buildRequestBody(model, prompt, stream = true, attachments = attachments),
            effectiveTimeout,
            flushIntervalMs = 0L,
            isCancelled,
            onPartialOutput,
        )
    }

    private fun executeWithRepoTools(
        model: String,
        prompt: String,
        timeoutOverride: Long,
        repoDir: File,
        onPartialOutput: (String) -> Unit,
        isCancelled: (() -> Boolean)?,
    ): String {
        val numCtx = resolveNumCtx()
        val baseUrl = resolveOllamaUrl()
        val messages =
            mutableListOf<Map<String, Any>>(
                mapOf("role" to "system", "content" to resolveSystemPrompt()),
                mapOf("role" to "user", "content" to prompt),
            )

        return toolLoop.runLoop(
            model = model,
            systemPrompt = resolveSystemPrompt(),
            userPrompt = prompt,
            timeoutSeconds = timeoutOverride,
            repoDir = repoDir,
            onPartialOutput = onPartialOutput,
            buildRequestBody = { msgs ->
                objectMapper.writeValueAsString(
                    mapOf(
                        "model" to model,
                        "messages" to msgs,
                        "tools" to RepoFileToolDefinitions.DEFINITIONS,
                        "stream" to false,
                        "think" to false,
                        "num_ctx" to numCtx,
                    ),
                )
            },
            sendRequest = { body, timeoutSec ->
                sendRequestInternal(baseUrl, body, timeoutSec, isCancelled)
            },
            onComplete = { finalText ->
                val extracted = RepoFileMarkdownExtractor.extract(finalText, repoDir, log)
                if (extracted > 0) {
                    log.info("[RepoFileTools] markdown-extract: {} additional file(s) written from text output", extracted)
                }
            },
            isCancelled = isCancelled,
        )
    }

    private fun resolveSystemPrompt(): String = promptLoader.get(resolveLanguage(), "commontools.systemPrompt")

    internal fun buildRequestBody(
        model: String,
        prompt: String,
        stream: Boolean,
        attachments: List<AiAttachment> = emptyList(),
    ): String {
        val numCtx = resolveNumCtx()
        val userMessage: Map<String, Any> =
            if (attachments.isEmpty()) {
                mapOf("role" to "user", "content" to prompt)
            } else {
                mapOf(
                    "role" to "user",
                    "content" to prompt,
                    "images" to attachments.map { it.base64Data },
                )
            }
        val messages =
            listOf(
                mapOf("role" to "system", "content" to resolveSystemPrompt()),
                userMessage,
            )
        val body =
            mapOf(
                "model" to model,
                "messages" to messages,
                "stream" to stream,
                "think" to false,
                "num_ctx" to numCtx,
            )
        return objectMapper.writeValueAsString(body)
    }

    private fun sendChatRequest(
        requestBody: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)?,
    ): String {
        val baseUrl = resolveOllamaUrl()
        return sendRequestInternal(baseUrl, requestBody, timeoutOverride, isCancelled)
    }

    private fun sendRequestInternal(
        baseUrl: String,
        requestBody: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)?,
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
            aiHttpClientExecutor.sendWithTimeout(
                httpClient,
                request,
                HttpResponse.BodyHandlers.ofString(),
                timeoutSeconds,
                isCancelled,
            )
        if (response.statusCode() != 200) {
            error("Ollama error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        }
        return response.body()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCompletionResponse(json: String): String {
        val map = objectMapper.readValue(json, Map::class.java)
        val choices = map["choices"] as? List<*> ?: return ""
        val first = choices.firstOrNull() as? Map<*, *> ?: return ""
        val message = first["message"] as? Map<*, *> ?: return ""
        val content = message["content"] as? String ?: ""
        return stripThinkingBlocks(content)
    }

    private fun streamRequest(
        baseUrl: String,
        requestBody: String,
        timeoutOverride: Long,
        flushIntervalMs: Long,
        isCancelled: (() -> Boolean)?,
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

        val sb = StringBuilder()
        var lastFlushMs = System.currentTimeMillis()

        val response =
            aiHttpClientExecutor.sendWithTimeout(
                httpClient,
                request,
                HttpResponse.BodyHandlers.ofInputStream(),
                timeoutOverride,
                isCancelled,
            )
        if (response.statusCode() != 200) {
            val errorBody = response.body().utf8Reader().closingAuto { it.readText() }
            error("Ollama error (HTTP ${response.statusCode()}): ${errorBody.take(400)}")
        }

        BufferedReader(InputStreamReader(response.body())).use { reader ->
            reader.forEachLine { line ->
                if (isCancelled?.invoke() == true) throw InterruptedException("ollama stream cancelled")
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") return@forEachLine
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        val chunk = objectMapper.readValue(data, Map::class.java)
                        val choices = chunk["choices"] as? List<*> ?: return@forEachLine
                        val first = choices.firstOrNull() as? Map<*, *> ?: return@forEachLine
                        val delta = first["delta"] as? Map<*, *> ?: return@forEachLine
                        val content = delta["content"] as? String ?: return@forEachLine
                        sb.append(content)
                        val now = System.currentTimeMillis()
                        if (flushIntervalMs == 0L || now - lastFlushMs >= flushIntervalMs) {
                            runCatching { onPartialOutput(stripThinkingBlocks(sb.toString())) }
                            lastFlushMs = now
                        }
                    }.onFailure {
                        log.debug("[Ollama] dropped partial JSON chunk: {}", it.message)
                    }
                }
            }
        }
        return stripThinkingBlocks(sb.toString().trim())
    }

    internal fun stripThinkingBlocks(text: String): String =
        text
            .replace(OpenAiCompatibleToolLoop.ContentNormalizer.THINK_BLOCK, "")
            .trimStart()
}
