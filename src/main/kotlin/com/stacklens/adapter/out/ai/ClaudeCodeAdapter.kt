package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.config.ClaudeCodeSettingsPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.AiConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.net.http.HttpClient
import java.time.Duration

class
ClaudeCodeAdapter(
    private val defaultBaseUrl: String = "https://api.anthropic.com",
    private val timeoutSeconds: Long = 180L,
    private val anthropicVersion: String = DEFAULT_ANTHROPIC_VERSION,
    private val aiConfigRepo: AiConfigPort,
    private val objectMapper: ObjectMapper,
    private val promptLoader: PromptReaderPort,
    private val tokenLookup: ClaudeCodeTokenLookupPort,
    private val settings: ClaudeCodeSettingsPort,
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build(),
    internal val httpSendRetry: HttpSendRetry = HttpSendRetry(),
    internal val aiHttpClientExecutor: AiHttpClientExecutor = AiHttpClientExecutor(),
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
    private val maxTokensCap: Int = DEFAULT_MAX_TOKENS_CAP,
    private val truncationRetries: Int = DEFAULT_TRUNCATION_RETRIES,
    private val thinkingMode: String = THINKING_MODE_ADAPTIVE,
    private val thinkingEffort: String = DEFAULT_THINKING_EFFORT,
) : CopilotStreamingPort {
    private val log = LoggerFactory.getLogger(ClaudeCodeAdapter::class.java)
    private val thinkingPolicy = ClaudeThinkingPolicy(maxTokens, maxTokensCap, thinkingMode, thinkingEffort)
    private val transport = ClaudeHttpTransport(httpClient, httpSendRetry, aiHttpClientExecutor, anthropicVersion)
    private val credentialRotator = ClaudeCredentialRotator(tokenLookup, transport)

    override val providerLabel: String = "ClaudeCode"

    internal val toolLoop: AnthropicToolLoop =
        AnthropicToolLoop(
            objectMapper = objectMapper,
            transport =
                object : AnthropicToolLoop.ChatTransport {
                    override val providerLabel: String = "ClaudeCode"

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
        onPartialOutput: (String) -> Unit,
    ): Boolean = toolLoop.processToolLoopIteration(iteration, respJson, messages, allText, repoDir, onPartialOutput)

    internal fun currentAiConfig(): AiConfig? = aiConfigRepo.findAiConfig()

    override fun resolveToken(): String? = tokenLookup.resolveToken()?.trim()?.takeIf { it.isNotEmpty() }

    override fun isReady(): Boolean = resolveToken() != null

    override fun resolveModel(): String = currentAiConfig()?.model?.takeIf { it.isNotBlank() } ?: settings.defaultModel()

    override fun resolveLanguage(): String = currentAiConfig()?.language?.takeIf { it.isNotBlank() } ?: "de"

    internal fun resolveBaseUrl(): String = defaultBaseUrl

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
        val baseUrl = resolveBaseUrl()
        val timeout = timeoutOverride ?: timeoutSeconds
        var budget = effectiveMaxTokens()
        var attempt = 0
        while (true) {
            attempt++
            log.info(
                "ClaudeCode request | Model: {} | Prompt: {} chars | Attachments: {} | max_tokens: {} | attempt: {} | thinking: {}",
                model,
                prompt.length,
                attachments.size,
                budget,
                attempt,
                effectiveThinkingMode(model),
            )
            val response =
                sendWithThinkingFallback(baseUrl, timeout, isCancelled, model) {
                    buildRequestBody(model, prompt, attachments, budget)
                }
            val parsed = parseMessages(response)
            if (parsed.stopReason != STOP_REASON_MAX_TOKENS) {
                log.info(
                    "ClaudeCode completed | Response: {} chars | output tokens: {} | thinking tokens: {}",
                    parsed.text.length,
                    parsed.outputTokens,
                    parsed.thinkingTokens,
                )
                return parsed.text
            }
            val next = escalatedBudget(budget)
            if (next == null || attempt > truncationRetries) {
                log.warn(
                    "ClaudeCode response truncated after {} output tokens ({} thinking, {} text chars) — no escalation budget left",
                    parsed.outputTokens,
                    parsed.thinkingTokens,
                    parsed.text.length,
                )
                throw StackLensFailureException(
                    StackLensFailure.AiOutputTruncated(parsed.outputTokens, parsed.thinkingTokens, parsed.text.length),
                )
            }
            log.warn(
                "ClaudeCode response truncated after {} output tokens ({} thinking, {} text chars) — retrying with max_tokens {}",
                parsed.outputTokens,
                parsed.thinkingTokens,
                parsed.text.length,
                next,
            )
            budget = next
        }
    }

    private fun sendWithThinkingFallback(
        baseUrl: String,
        timeoutSeconds: Long,
        isCancelled: (() -> Boolean)?,
        model: String,
        buildBody: () -> String,
    ): String {
        while (true) {
            val body = buildBody()
            try {
                return sendWithCredentialRotation(baseUrl, body, timeoutSeconds, isCancelled)
            } catch (failure: IllegalStateException) {
                if (!thinkingPolicy.isAdaptiveThinkingRejected(failure.message) || !thinkingPolicy.markDegraded(model)) throw failure
                log.warn(
                    "ClaudeCode: adaptive thinking rejected for model '{}' — retrying without thinking parameters",
                    model,
                )
            }
        }
    }

    private fun sendWithCredentialRotation(
        baseUrl: String,
        requestBody: String,
        timeoutSeconds: Long,
        isCancelled: (() -> Boolean)?,
    ): String = credentialRotator.sendWithCredentialRotation(baseUrl, requestBody, timeoutSeconds, isCancelled)

    internal fun tokenCandidates(): Sequence<String> = credentialRotator.tokenCandidates()

    internal fun isAdaptiveThinkingRejected(message: String?): Boolean = thinkingPolicy.isAdaptiveThinkingRejected(message)

    internal fun effectiveMaxTokens(): Int = thinkingPolicy.effectiveMaxTokens()

    internal fun effectiveThinkingMode(model: String): String = thinkingPolicy.effectiveThinkingMode(model)

    internal fun thinkingFields(model: String): Map<String, Any> = thinkingPolicy.thinkingFields(model)

    internal fun resolvedThinkingEffort(): String = thinkingPolicy.resolvedThinkingEffort()

    internal fun escalatedBudget(current: Int): Int? = thinkingPolicy.escalatedBudget(current)

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String {
        val result = execute(model, prompt, timeoutOverride, attachments, isCancelled)
        AiCancellation.checkCancelled(isCancelled, "ClaudeCode stream")
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
            log.warn("[RepoFileTools] ClaudeCode: repoDir does not exist: {} — falling back to plain call", effectiveRepoDir)
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
        val baseUrl = resolveBaseUrl()

        var budget = effectiveMaxTokens()
        var attempt = 0
        var lastMessages: List<Map<String, Any>> = emptyList()
        while (true) {
            attempt++
            try {
                return toolLoop.runLoop(
                    model = model,
                    systemPrompt = resolveSystemPrompt(),
                    userPrompt = prompt,
                    timeoutSeconds = timeoutOverride,
                    repoDir = repoDir,
                    onPartialOutput = onPartialOutput,
                    buildRequestBody = { msgs ->
                        lastMessages = msgs
                        buildToolRequestBody(model, msgs, budget)
                    },
                    sendRequest = { _, timeoutSec ->
                        sendWithThinkingFallback(baseUrl, timeoutSec, isCancelled, model) {
                            buildToolRequestBody(model, lastMessages, budget)
                        }
                    },
                    onComplete = { finalText ->
                        val extracted = RepoFileMarkdownExtractor.extract(finalText, repoDir, log)
                        if (extracted > 0) {
                            log.info("[RepoFileTools] ClaudeCode markdown-extract: {} additional file(s) written", extracted)
                        }
                    },
                    isCancelled = isCancelled,
                )
            } catch (e: StackLensFailureException) {
                val truncated = e.failure as? StackLensFailure.AiOutputTruncated ?: throw e
                val next = escalatedBudget(budget)
                if (next == null || attempt > truncationRetries) throw e
                log.warn(
                    "[RepoFileTools] ClaudeCode tool loop truncated after {} output tokens — restarting with max_tokens {}",
                    truncated.outputTokens,
                    next,
                )
                budget = next
            }
        }
    }

    private fun sendToolRequest(
        body: String,
        timeoutOverride: Long,
    ): String = sendWithCredentialRotation(resolveBaseUrl(), body, timeoutOverride, null)

    internal fun resolveSystemPrompt(): String = promptLoader.get(resolveLanguage(), "commontools.systemPrompt")

    internal fun buildToolRequestBody(
        model: String,
        messages: List<Map<String, Any>>,
        budget: Int = effectiveMaxTokens(),
    ): String {
        val payload = basePayload(model, budget, messages).toMutableMap()
        payload["tools"] = RepoFileToolDefinitions.ANTHROPIC_DEFINITIONS
        return objectMapper.writeValueAsString(payload)
    }

    private fun basePayload(
        model: String,
        budget: Int,
        messages: List<Map<String, Any>>,
    ): Map<String, Any> {
        val payload =
            linkedMapOf<String, Any>(
                "model" to model,
                "max_tokens" to budget.coerceIn(MIN_TOKENS_FLOOR, thinkingPolicy.tokensCap),
                "system" to resolveSystemPrompt(),
                "messages" to messages,
            )
        payload.putAll(thinkingFields(model))
        return payload
    }

    internal fun buildRequestBody(
        model: String,
        prompt: String,
        attachments: List<AiAttachment> = emptyList(),
        budget: Int = effectiveMaxTokens(),
    ): String {
        val userContent: Any =
            if (attachments.isEmpty()) {
                prompt
            } else {
                val parts =
                    mutableListOf<Map<String, Any>>(
                        mapOf("type" to "text", "text" to prompt),
                    )
                for (att in attachments) {
                    parts.add(
                        mapOf(
                            "type" to "image",
                            "source" to
                                mapOf(
                                    "type" to "base64",
                                    "media_type" to att.mimeType,
                                    "data" to att.base64Data,
                                ),
                        ),
                    )
                }
                parts
            }
        return objectMapper.writeValueAsString(basePayload(model, budget, listOf(mapOf("role" to "user", "content" to userContent))))
    }

    internal data class ParsedResponse(
        val text: String,
        val stopReason: String?,
        val outputTokens: Int,
        val thinkingTokens: Int,
    )

    internal fun parseMessages(json: String): ParsedResponse {
        if (json.isBlank()) return ParsedResponse("", null, 0, 0)
        val map =
            try {
                @Suppress("UNCHECKED_CAST")
                objectMapper.readValue(json, Map::class.java) as Map<String, Any>
            } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
                log.warn("ClaudeCode returned non-JSON body ({} chars): {}", json.length, e.message)
                return ParsedResponse("", null, 0, 0)
            }
        val stopReason = map["stop_reason"] as? String
        val usage = map["usage"] as? Map<*, *>
        val outputTokens = (usage?.get("output_tokens") as? Number)?.toInt() ?: 0
        val thinkingTokens =
            ((usage?.get("output_tokens_details") as? Map<*, *>)?.get("thinking_tokens") as? Number)?.toInt() ?: 0
        val content = map["content"] as? List<*> ?: return ParsedResponse("", stopReason, outputTokens, thinkingTokens)
        val text =
            content
                .mapNotNull { it as? Map<*, *> }
                .mapNotNull { part -> (part["text"] as? String)?.takeIf { part["type"] == "text" } }
                .joinToString("")
        return ParsedResponse(text, stopReason, outputTokens, thinkingTokens)
    }

    companion object {
        const val DEFAULT_ANTHROPIC_VERSION: String = "2023-06-01"

        const val DEFAULT_MAX_TOKENS: Int = 32_000

        const val DEFAULT_MAX_TOKENS_CAP: Int = 128_000

        const val DEFAULT_TRUNCATION_RETRIES: Int = 1

        const val MIN_TOKENS_FLOOR: Int = 1_024

        const val STOP_REASON_MAX_TOKENS: String = "max_tokens"

        const val THINKING_MODE_ADAPTIVE: String = "adaptive"

        const val THINKING_MODE_DISABLED: String = "disabled"

        const val THINKING_MODE_NONE: String = "none"

        const val DEFAULT_THINKING_EFFORT: String = "low"

        val SUPPORTED_THINKING_EFFORTS: List<String> = listOf("low", "medium", "high", "xhigh", "max")
    }
}
