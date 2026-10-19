package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.LmStudioModelLookupPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.slf4j.LoggerFactory
import java.io.File

class
LmStudioAdapter(
    private val defaultLmStudioUrl: String = "http://localhost:1234",
    private val timeoutSeconds: Long = 600L,
    private val aiConfigRepo: AiConfigPort,
    private val objectMapper: ObjectMapper,
    private val promptLoader: PromptReaderPort,
    private val containerRuntimeProbe: com.stacklens.application.port.out.platform.ContainerRuntimeProbePort,
) : CopilotStreamingPort,
    LmStudioModelLookupPort {
    private val log = LoggerFactory.getLogger(LmStudioAdapter::class.java)
    private val apiClient = LmStudioApiClient(objectMapper)

    override val providerLabel: String = "LM Studio"

    internal val toolLoop: OpenAiCompatibleToolLoop =
        OpenAiCompatibleToolLoop(
            objectMapper = objectMapper,
            transport =
                object : OpenAiCompatibleToolLoop.ChatTransport {
                    override val providerLabel: String = "LmStudio"

                    override fun send(
                        body: String,
                        timeoutSeconds: Long,
                    ): String {
                        val baseUrl = resolveLmStudioUrl()
                        if (!apiClient.isModelLoaded(baseUrl, resolveModel())) {
                            apiClient.loadModel(baseUrl, resolveModel(), timeoutSeconds)
                        }
                        return apiClient.sendRequest(baseUrl, body, timeoutSeconds)
                    }
                },
            normalizer = OpenAiCompatibleToolLoop.ContentNormalizer.stripLmStudioThinkingResponse(),
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

    internal fun resolveLmStudioUrl(): String {
        val url = currentAiConfig()?.lmStudioUrl?.takeIf { it.isNotBlank() } ?: defaultLmStudioUrl
        val isInContainer = containerRuntimeProbe.detectActiveRuntime() != com.stacklens.application.port.out.platform.ContainerRuntime.NONE
        return if (isInContainer) url.replace("://localhost", "://host.docker.internal") else url
    }

    override fun resolveToken(): String? = null

    override fun isReady(): Boolean = true

    override fun resolveModel(): String {
        val cfg = currentAiConfig()
        val m = cfg?.model
        return if (m.isNullOrBlank()) "local-model" else m
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
        val baseUrl = resolveLmStudioUrl()
        log.info("LM Studio request | Model: {} | URL: {} | Prompt: {} chars | Attachments: {}", model, baseUrl, prompt.length, attachments.size)
        val startMs = System.currentTimeMillis()
        val effectiveTimeout = timeoutOverride ?: timeoutSeconds
        if (!apiClient.isModelLoaded(baseUrl, model)) apiClient.loadModel(baseUrl, model, effectiveTimeout)
        val requestBody = buildRequestBody(model, prompt, stream = false, attachments = attachments)
        val responseJson = apiClient.sendRequest(baseUrl, requestBody, effectiveTimeout)
        val result = parseCompletionResponse(responseJson)
        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000
        log.info("LM Studio completed ({}s) | Response: {} chars", elapsedSec, result.length)
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
        val baseUrl = resolveLmStudioUrl()
        val effectiveTimeout = timeoutOverride ?: timeoutSeconds
        log.info("LM Studio streaming | Model: {} | Prompt: {} chars | Attachments: {}", model, prompt.length, attachments.size)
        val startMs = System.currentTimeMillis()
        if (!apiClient.isModelLoaded(baseUrl, model)) apiClient.loadModel(baseUrl, model, effectiveTimeout, isCancelled)
        AiCancellation.checkCancelled(isCancelled, "LmStudio stream")
        val result =
            apiClient.streamRequest(
                baseUrl,
                buildRequestBody(model, prompt, stream = true, attachments = attachments),
                effectiveTimeout,
                flushIntervalMs = 1_000L,
                isCancelled,
                onPartialOutput,
            )
        AiCancellation.checkCancelled(isCancelled, "LmStudio stream")
        log.info("LM Studio streaming completed ({}s) | Response: {} chars", (System.currentTimeMillis() - startMs) / 1000, result.length)
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
        if (effectiveRepoDir != null) {
            if (!effectiveRepoDir.exists() || !effectiveRepoDir.isDirectory) {
                log.warn("[RepoFileTools] LM Studio: repoDir does not exist: {} — falling back to plain call", effectiveRepoDir)
                return executeWithStreaming(model, prompt, effectiveTimeout, isCancelled, onPartialOutput, attachments)
            }
            return executeWithRepoTools(model, prompt, effectiveTimeout, effectiveRepoDir, onPartialOutput, isCancelled)
        }
        val baseUrl = resolveLmStudioUrl()
        if (!apiClient.isModelLoaded(baseUrl, model)) apiClient.loadModel(baseUrl, model, effectiveTimeout, isCancelled)
        AiCancellation.checkCancelled(isCancelled, "LmStudio stream")
        return apiClient.streamRequest(
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
        val baseUrl = resolveLmStudioUrl()
        if (!apiClient.isModelLoaded(baseUrl, model)) apiClient.loadModel(baseUrl, model, timeoutOverride, isCancelled)

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
            sendRequest = { body, timeoutSec ->
                apiClient.sendRequest(baseUrl, body, timeoutSec, isCancelled)
            },
            onComplete = { finalText ->
                RepoFileMarkdownExtractor.extract(finalText, repoDir, log)
            },
            isCancelled = isCancelled,
        )
    }

    internal fun resolveSystemPrompt(): String = promptLoader.get(resolveLanguage(), "commontools.systemPrompt")

    internal fun buildRequestBody(
        model: String,
        prompt: String,
        stream: Boolean,
        attachments: List<AiAttachment> = emptyList(),
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
                            "type" to "image_url",
                            "image_url" to mapOf("url" to att.toDataUri()),
                        ),
                    )
                }
                parts
            }
        return objectMapper.writeValueAsString(
            mapOf(
                "model" to model,
                "messages" to
                    listOf(
                        mapOf("role" to "system", "content" to resolveSystemPrompt()),
                        mapOf("role" to "user", "content" to userContent),
                    ),
                "stream" to stream,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseCompletionResponse(json: String): String {
        val map = objectMapper.readValue(json, Map::class.java)
        extractNativeChatContent(map)?.let { return it }
        val choices = map["choices"] as? List<*> ?: return ""
        val first = choices.firstOrNull() as? Map<*, *> ?: return ""
        val message = first["message"] as? Map<*, *> ?: return ""
        return message["content"] as? String ?: ""
    }

    internal fun extractNativeChatContent(map: Map<*, *>): String? {
        val output = map["output"]
        if (output is String) return output
        if (output !is List<*>) return null
        return output
            .mapNotNull {
                it as? Map<*, *>
            }.filter { it["type"] == "message" }
            .mapNotNull { it["content"] as? String }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }

    override fun fetchAvailableModels(baseUrl: String): List<String> = apiClient.fetchAvailableModels(baseUrl)
}
