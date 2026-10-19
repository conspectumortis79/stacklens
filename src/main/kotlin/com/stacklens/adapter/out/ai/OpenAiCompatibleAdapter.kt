package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
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

open class OpenAiCompatibleAdapter(
    private val endpoint: OpenAiCompatibleEndpoint,
    private val timeoutSeconds: Long = 180L,
    private val aiConfigRepo: AiConfigPort,
    private val objectMapper: ObjectMapper,
    private val promptLoader: PromptReaderPort,
    internal val httpVersion: HttpClient.Version = HttpClient.Version.HTTP_1_1,
    internal val httpSendRetry: HttpSendRetry = HttpSendRetry(),
    internal val aiHttpClientExecutor: AiHttpClientExecutor = AiHttpClientExecutor(),
) : CopilotStreamingPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override val providerLabel: String
        get() = endpoint.label

    internal val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(httpVersion)
            .build()

    internal val toolLoop: OpenAiCompatibleToolLoop =
        OpenAiCompatibleToolLoop(
            objectMapper = objectMapper,
            transport =
                object : OpenAiCompatibleToolLoop.ChatTransport {
                    override val providerLabel: String = endpoint.label

                    override fun send(
                        body: String,
                        timeoutSeconds: Long,
                    ): String {
                        val token = resolveToken() ?: error("No $providerLabel API key set.")
                        return sendChatRequest(body, token, timeoutSeconds, null)
                    }
                },
        )

    internal fun processToolLoopIteration(
        iteration: Int,
        respJson: String,
        messages: MutableList<Map<String, Any>>,
        allText: StringBuilder,
        repoDir: File,
        onPartialOutput: (String) -> Unit,
    ): Boolean = toolLoop.processToolLoopIteration(iteration, respJson, messages, allText, repoDir, onPartialOutput)

    internal fun currentAiConfig(): AiConfig? = aiConfigRepo.findAiConfig()

    override fun resolveToken(): String? = endpoint.resolveApiKey(currentAiConfig())

    override fun isReady(): Boolean = resolveToken() != null

    override fun resolveModel(): String = currentAiConfig()?.model?.takeIf { it.isNotBlank() } ?: endpoint.defaultModel

    override fun resolveLanguage(): String = currentAiConfig()?.language?.takeIf { it.isNotBlank() } ?: "de"

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
        val token = requireToken()
        log.info(
            "{} request | Model: {} | Prompt: {} chars | Attachments: {}",
            providerLabel,
            model,
            prompt.length,
            attachments.size,
        )
        val requestBody = buildChatRequestBody(model, prompt, attachments)
        val response = sendChatRequest(requestBody, token, timeoutOverride ?: timeoutSeconds, isCancelled)
        val result = parseChatResponse(response)
        log.info("{} completed | Response: {} chars", providerLabel, result.length)
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
        AiCancellation.checkCancelled(isCancelled, "$providerLabel stream")
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
            log.warn(
                "[RepoFileTools] {}: repoDir does not exist: {} — falling back to plain call",
                providerLabel,
                effectiveRepoDir,
            )
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
        val token = requireToken()

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
            sendRequest = { body, timeoutSec -> sendChatRequest(body, token, timeoutSec, isCancelled) },
            onComplete = { finalText ->
                val extracted = RepoFileMarkdownExtractor.extract(finalText, repoDir, log)
                if (extracted > 0) {
                    log.info(
                        "[RepoFileTools] {} markdown-extract: {} additional file(s) written",
                        providerLabel,
                        extracted,
                    )
                }
            },
            isCancelled = isCancelled,
        )
    }

    private fun requireToken(): String = resolveToken() ?: error("No $providerLabel API key set.")

    private fun sendChatRequest(
        requestBody: String,
        token: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)?,
    ): String {
        val url = "${endpoint.resolveBaseUrl(currentAiConfig())}/chat/completions"
        val requestBuilder =
            HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutOverride))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
        endpoint.extraHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }
        val request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build()
        val response =
            httpSendRetry.sendWithRetry(opName = "$providerLabel.chatCompletions") {
                aiHttpClientExecutor.sendWithTimeout(
                    httpClient,
                    request,
                    HttpResponse.BodyHandlers.ofString(),
                    timeoutOverride,
                    isCancelled,
                )
            }
        if (response.statusCode() !in 200..299) {
            error("$providerLabel error (HTTP ${response.statusCode()}): ${response.body().take(400)}")
        }
        return response.body()
    }

    internal fun resolveSystemPrompt(): String {
        val base = promptLoader.get(resolveLanguage(), "commontools.systemPrompt")
        return if (isNonToolCapableModel(resolveModel())) {
            base + "\n\n" + MARKDOWN_OUTPUT_HINT
        } else {
            base
        }
    }

    internal fun isNonToolCapableModel(model: String): Boolean {
        val patterns = endpoint.nonToolCapablePatterns
        if (patterns.isEmpty()) return false
        val lower = model.lowercase()
        return patterns.any { it.lowercase() in lower }
    }

    internal fun buildChatRequestBody(
        model: String,
        prompt: String,
        attachments: List<AiAttachment> = emptyList(),
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "model" to model,
                "messages" to
                    listOf(
                        mapOf("role" to "system", "content" to resolveSystemPrompt()),
                        mapOf("role" to "user", "content" to buildUserContent(prompt, attachments)),
                    ),
            ),
        )

    private fun buildUserContent(
        prompt: String,
        attachments: List<AiAttachment>,
    ): Any {
        if (attachments.isEmpty()) return prompt
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
        return parts
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseChatResponse(json: String): String {
        if (json.isBlank()) return ""
        val map =
            try {
                objectMapper.readValue(json, Map::class.java)
            } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
                log.warn("{} returned non-JSON body ({} chars): {}", providerLabel, json.length, e.message)
                return ""
            }
        val choices = map["choices"] as? List<*> ?: return ""
        val firstChoice = choices.firstOrNull() as? Map<String, Any> ?: return ""
        val message = firstChoice["message"] as? Map<String, Any> ?: return ""
        return message["content"] as? String ?: ""
    }

    companion object {
        private const val MARKDOWN_OUTPUT_HINT: String =
            "CRITICAL — OUTPUT FORMAT: This model has no tool-calling. " +
                "For every file you produce, output exactly one block in this format:\n\n" +
                "### relative/path/to/file.ext\n" +
                "```language\n" +
                "<complete file content>\n" +
                "```\n\n" +
                "Do NOT write greetings, explanations, or status updates between blocks. " +
                "Output ONLY the file blocks."
    }
}
