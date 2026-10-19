package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.OpenAiModelLookupPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class
OpenAiAdapter(
    private val defaultOpenAiBaseUrl: String = "https://api.openai.com",
    private val timeoutSeconds: Long = 180L,
    private val aiConfigRepo: AiConfigPort,
    private val objectMapper: ObjectMapper,
    private val promptLoader: PromptReaderPort,
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build(),
    internal val httpSendRetry: HttpSendRetry = HttpSendRetry(),
    internal val aiHttpClientExecutor: AiHttpClientExecutor = AiHttpClientExecutor(),
    private val envReader: (String) -> String? = { name -> System.getenv(name) },
) : CopilotStreamingPort,
    OpenAiModelLookupPort {
    private val log = LoggerFactory.getLogger(OpenAiAdapter::class.java)

    override val providerLabel: String = "OpenAI"
    private val supportedReasoningEfforts = setOf("low", "medium", "high")

    internal val toolLoop: OpenAiCompatibleToolLoop =
        OpenAiCompatibleToolLoop(
            objectMapper = objectMapper,
            transport =
                object : OpenAiCompatibleToolLoop.ChatTransport {
                    override val providerLabel: String = "OpenAI"

                    override fun send(
                        body: String,
                        timeoutSeconds: Long,
                    ): String = sendToolRequest(body, timeoutSeconds)
                },
        )

    internal fun processToolLoopIteration(
        iteration: Int,
        respJson: String,
        messages: MutableList<Map<String, Any>>,
        allText: StringBuilder,
        repoDir: File,
        onPartialOutput: (String) -> Unit = {},
    ): Boolean = toolLoop.processToolLoopIteration(iteration, respJson, messages, allText, repoDir, onPartialOutput)

    internal fun currentAiConfig(): AiConfig? = aiConfigRepo.findAiConfig()

    internal fun resolveOpenAiBaseUrl(): String = defaultOpenAiBaseUrl

    override fun resolveToken(): String? =
        currentAiConfig()
            ?.openAiApiKey
            ?.takeIf { it.isNotBlank() }
            ?: envReader("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }

    override fun isReady(): Boolean = resolveToken() != null

    override fun resolveModel(): String = currentAiConfig()?.model?.takeIf { it.isNotBlank() } ?: "gpt-5.2"

    override fun resolveLanguage(): String = currentAiConfig()?.language?.takeIf { it.isNotBlank() } ?: "de"

    internal fun resolveReasoningEffort(): String =
        currentAiConfig()
            ?.openAiReasoningEffort
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in supportedReasoningEfforts }
            ?: "medium"

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
    ): String = execute(model, prompt, timeoutOverride, attachments, null)

    private fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
        isCancelled: (() -> Boolean)?,
    ): String {
        val token = resolveToken() ?: error("No OpenAI API key set.")
        val baseUrl = resolveOpenAiBaseUrl()
        log.info("OpenAI request | Model: {} | Prompt: {} chars | Attachments: {}", model, prompt.length, attachments.size)
        val timeout = timeoutOverride ?: timeoutSeconds
        if (attachments.isNotEmpty()) {
            val requestBody = buildChatCompletionsVisionBody(model, prompt, attachments)
            val response = sendChatCompletionsRequest(baseUrl, token, requestBody, timeout, isCancelled)
            val result = parseChatCompletionsResponse(response)
            log.info("OpenAI completed (vision) | Response: {} chars", result.length)
            return result
        }
        val requestBody = buildRequestBody(model, prompt)
        val response = sendRequest(baseUrl, token, requestBody, timeout, isCancelled)
        val result = parseResponsesApiResponse(response)
        log.info("OpenAI completed | Response: {} chars", result.length)
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
        val result = execute(model, prompt, timeoutOverride, attachments, isCancelled)
        AiCancellation.checkCancelled(isCancelled, "OpenAI stream")
        onPartialOutput(result)
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
        val effectiveTimeout = timeoutOverride ?: timeoutSeconds
        val effectiveRepoDir = repoDir
        if (effectiveRepoDir == null) {
            return executeWithStreaming(model, prompt, effectiveTimeout, isCancelled, onPartialOutput, attachments)
        }
        if (!effectiveRepoDir.exists() || !effectiveRepoDir.isDirectory) {
            log.warn("[RepoFileTools] OpenAI: repoDir does not exist: {} — falling back to plain call", effectiveRepoDir)
            return executeWithStreaming(model, prompt, effectiveTimeout, isCancelled, onPartialOutput, attachments)
        }
        return executeWithRepoTools(model, prompt, effectiveTimeout, effectiveRepoDir, onPartialOutput, isCancelled)
    }

    private fun executeWithRepoTools(
        model: String,
        prompt: String,
        timeoutOverride: Long,
        repoDir: File,
        onPartialOutput: (String) -> Unit,
        isCancelled: (() -> Boolean)?,
    ): String {
        val token = resolveToken() ?: error("No OpenAI API key set.")
        val baseUrl = resolveOpenAiBaseUrl()

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
                    ),
                )
            },
            sendRequest = { body, timeoutSec -> sendToolRequestWithAuth(baseUrl, token, body, timeoutSec, isCancelled) },
            onComplete = { finalText ->
                val extracted = RepoFileMarkdownExtractor.extract(finalText, repoDir, log)
                if (extracted > 0) {
                    log.info("[RepoFileTools] OpenAI markdown-extract: {} additional file(s) written", extracted)
                }
            },
            isCancelled = isCancelled,
        )
    }

    private fun sendToolRequest(
        body: String,
        timeoutOverride: Long,
    ): String {
        val token = resolveToken() ?: error("No OpenAI API key set.")
        return sendToolRequestWithAuth(resolveOpenAiBaseUrl(), token, body, timeoutOverride, null)
    }

    private fun sendToolRequestWithAuth(
        baseUrl: String,
        token: String,
        body: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)?,
    ): String {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${baseUrl.trimEnd('/')}/v1/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutOverride))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response =
            httpSendRetry.sendWithRetry(opName = "OpenAI.chatCompletions") {
                aiHttpClientExecutor.sendWithTimeout(
                    buildHttpClient(),
                    request,
                    HttpResponse.BodyHandlers.ofString(),
                    timeoutSeconds,
                    isCancelled,
                )
            }
        if (response.statusCode() !in 200..299) {
            error("OpenAI error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        }
        return response.body()
    }

    internal fun resolveSystemPrompt(): String = promptLoader.get(resolveLanguage(), "commontools.systemPrompt")

    internal fun supportsReasoningEffort(model: String): Boolean {
        val lower = model.lowercase().trim()
        return Regex("^o\\d").containsMatchIn(lower)
    }

    internal fun buildRequestBody(
        model: String,
        prompt: String,
        attachments: List<AiAttachment> = emptyList(),
    ): String {
        if (attachments.isNotEmpty()) {
            return buildChatCompletionsVisionBody(model, prompt, attachments)
        }
        val body =
            mutableMapOf<String, Any>(
                "model" to model,
                "input" to prompt,
                "instructions" to resolveSystemPrompt(),
                "store" to false,
            )
        if (supportsReasoningEffort(model)) {
            body["reasoning"] = mapOf("effort" to resolveReasoningEffort())
        }
        return objectMapper.writeValueAsString(body)
    }

    internal fun buildChatCompletionsVisionBody(
        model: String,
        prompt: String,
        attachments: List<AiAttachment>,
    ): String {
        val parts =
            mutableListOf<Map<String, Any>>(
                mapOf("type" to "text", "text" to prompt),
            )
        for (att in attachments) {
            parts.add(
                mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf("url" to att.toDataUri()),
                ),
            )
        }
        return objectMapper.writeValueAsString(
            mapOf(
                "model" to model,
                "messages" to
                    listOf(
                        mapOf("role" to "system", "content" to resolveSystemPrompt()),
                        mapOf("role" to "user", "content" to parts),
                    ),
            ),
        )
    }

    private fun buildHttpClient(): HttpClient = httpClient

    private fun sendRequest(
        baseUrl: String,
        token: String,
        requestBody: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)?,
    ): String {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${baseUrl.trimEnd('/')}/v1/responses"))
                .timeout(Duration.ofSeconds(timeoutOverride))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        val response =
            httpSendRetry.sendWithRetry(opName = "OpenAI.responses") {
                aiHttpClientExecutor.sendWithTimeout(
                    buildHttpClient(),
                    request,
                    HttpResponse.BodyHandlers.ofString(),
                    timeoutOverride,
                    isCancelled,
                )
            }
        if (response.statusCode() !in 200..299) {
            error("OpenAI error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        }
        return response.body()
    }

    private fun sendChatCompletionsRequest(
        baseUrl: String,
        token: String,
        requestBody: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)?,
    ): String {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${baseUrl.trimEnd('/')}/v1/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutOverride))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
        val response =
            httpSendRetry.sendWithRetry(opName = "OpenAI.chatCompletions") {
                aiHttpClientExecutor.sendWithTimeout(
                    buildHttpClient(),
                    request,
                    HttpResponse.BodyHandlers.ofString(),
                    timeoutOverride,
                    isCancelled,
                )
            }
        if (response.statusCode() !in 200..299) {
            error("OpenAI error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        }
        return response.body()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseChatCompletionsResponse(json: String): String {
        if (json.isBlank()) return ""
        val map =
            try {
                objectMapper.readValue(json, Map::class.java) as Map<String, Any>
            } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
                return ""
            }
        val choices = map["choices"] as? List<*> ?: return ""
        val firstChoice = choices.firstOrNull() as? Map<String, Any> ?: return ""
        val message = firstChoice["message"] as? Map<String, Any> ?: return ""
        return message["content"] as? String ?: ""
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseResponsesApiResponse(json: String): String {
        val map = objectMapper.readValue(json, Map::class.java)
        (map["output_text"] as? String)?.let { return it }
        val output = map["output"] as? List<*> ?: return ""
        return output
            .mapNotNull { it as? Map<*, *> }
            .flatMap { item ->
                val content = item["content"] as? List<*> ?: return@flatMap emptyList<String>()
                content
                    .mapNotNull { it as? Map<*, *> }
                    .mapNotNull { part ->
                        (part["text"] as? String) ?: (part["content"] as? String)
                    }
            }.joinToString("")
    }

    @Suppress("UNCHECKED_CAST")
    override fun fetchAvailableModels(apiKey: String): List<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${resolveOpenAiBaseUrl().trimEnd('/')}/v1/models"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .GET()
                .build()
        val response =
            httpSendRetry.sendWithRetry(opName = "OpenAI.models") {
                aiHttpClientExecutor.sendWithTimeout(
                    buildHttpClient(),
                    request,
                    HttpResponse.BodyHandlers.ofString(),
                    30L,
                )
            }
        if (response.statusCode() !in 200..299) {
            error("OpenAI /v1/models error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        }
        val map = objectMapper.readValue(response.body(), Map::class.java)
        val data = map["data"] as? List<*> ?: return emptyList()
        return data
            .mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
            .filter { it.startsWith("gpt-") }
    }
}
