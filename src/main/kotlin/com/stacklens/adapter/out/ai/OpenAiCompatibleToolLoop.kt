package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

class OpenAiCompatibleToolLoop(
    private val objectMapper: ObjectMapper,
    internal val transport: ChatTransport,
    private val normalizer: ContentNormalizer = ContentNormalizer.Identity,
    maxIterations: Int = DEFAULT_MAX_ITERATIONS,
) : RepoFileToolLoop(maxIterations) {
    override val providerLabel: String get() = transport.providerLabel

    override fun initialMessages(
        systemPrompt: String,
        userPrompt: String,
    ): List<Map<String, Any>> =
        listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt),
        )

    override fun handleIteration(
        iteration: Int,
        respJson: String,
        messages: MutableList<Map<String, Any>>,
        allText: StringBuilder,
        repoDir: File,
        onPartialOutput: (String) -> Unit,
    ): Boolean = processToolLoopIteration(iteration, respJson, messages, allText, repoDir, onPartialOutput)

    @Suppress("UNCHECKED_CAST")
    fun processToolLoopIteration(
        iteration: Int,
        respJson: String,
        messages: MutableList<Map<String, Any>>,
        allText: StringBuilder,
        repoDir: File,
        onPartialOutput: (String) -> Unit = {},
    ): Boolean {
        val resp: Map<String, Any> =
            runCatching { objectMapper.readValue(respJson, Map::class.java) as Map<String, Any> }
                .getOrElse { return false }

        val choices = resp["choices"] as? List<*> ?: return false
        val choice = choices.firstOrNull() as? Map<String, Any> ?: return false
        val message = choice["message"] as? Map<String, Any> ?: return false
        val finishReason = choice["finish_reason"] as? String
        val rawContent = message["content"] as? String ?: ""
        val content = normalizer.normalize(rawContent)
        val structuredToolCalls = message["tool_calls"] as? List<*>

        val toolCalls: List<*>? =
            if (!structuredToolCalls.isNullOrEmpty()) {
                structuredToolCalls
            } else {
                val parsed = RepoFileTextToolCallParser.parse(content)
                if (parsed.isNotEmpty()) {
                    log.info("[RepoFileTools] Iteration {}: parsed {} text-form tool call(s)", iteration, parsed.size)
                    parsed
                } else {
                    null
                }
            }

        appendPartialOutput(content, allText, onPartialOutput)

        val hasTextFormToolCalls = structuredToolCalls.isNullOrEmpty() && toolCalls != null
        if (toolCalls == null || (finishReason == "stop" && !hasTextFormToolCalls)) {
            logLoopDone(iteration, allText)
            return false
        }

        logToolCalls(iteration, toolCalls.size, "tool call(s)")

        val assistantMsg = mutableMapOf<String, Any>("role" to "assistant")
        if (content.isNotBlank()) assistantMsg["content"] = content
        assistantMsg["tool_calls"] = toolCalls
        messages.add(assistantMsg)

        for (tc in toolCalls) {
            val tcMap = tc as? Map<String, Any> ?: continue
            val callId = tcMap["id"] as? String ?: "call_$iteration"

            val fn = tcMap["function"] as? Map<String, Any> ?: continue
            val funcName = fn["name"] as? String ?: continue
            val argsJson = fn["arguments"] as? String ?: "{}"

            val args =
                runCatching {
                    objectMapper.readValue(argsJson, Map::class.java) as Map<String, Any>
                }.getOrElse { emptyMap() }

            val result = RepoFileToolExecutor.execute(funcName, args, repoDir, log)
            messages.add(
                mapOf(
                    "role" to "tool",
                    "tool_call_id" to callId,
                    "content" to result,
                ),
            )
        }
        return true
    }

    interface ChatTransport {
        val providerLabel: String

        fun send(
            body: String,
            timeoutSeconds: Long,
        ): String
    }

    fun interface ContentNormalizer {
        fun normalize(content: String): String

        object Identity : ContentNormalizer {
            override fun normalize(content: String): String = content
        }

        companion object {
            val THINK_BLOCK: Regex =
                Regex(
                    "<think>[\\s\\S]*?</think>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )

            val LM_STUDIO_THINKING_RESPONSE: Regex =
                Regex(
                    " thinking[\\s\\S]*? response",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )

            fun stripThinkBlocks(): ContentNormalizer =
                ContentNormalizer { input ->
                    input.replace(THINK_BLOCK, "").trimStart()
                }

            fun stripLmStudioThinkingResponse(): ContentNormalizer =
                ContentNormalizer { input ->
                    input.replace(LM_STUDIO_THINKING_RESPONSE, "").trimStart()
                }
        }
    }

    companion object {
        const val DEFAULT_MAX_ITERATIONS: Int = 60
    }
}
