package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import java.io.File

class AnthropicToolLoop(
    private val objectMapper: ObjectMapper,
    internal val transport: ChatTransport,
    maxIterations: Int = DEFAULT_MAX_ITERATIONS,
) : RepoFileToolLoop(maxIterations) {
    override val providerLabel: String get() = transport.providerLabel

    override fun initialMessages(
        systemPrompt: String,
        userPrompt: String,
    ): List<Map<String, Any>> = listOf(mapOf("role" to "user", "content" to userPrompt))

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

        val content = resp["content"] as? List<*> ?: return false
        val stopReason = resp["stop_reason"] as? String

        val textParts = mutableListOf<String>()
        val toolUses = mutableListOf<Map<String, Any>>()
        for (item in content) {
            val itemMap = item as? Map<String, Any> ?: continue
            when (itemMap["type"] as? String) {
                "text" -> (itemMap["text"] as? String)?.let { textParts.add(it) }
                "tool_use" -> {
                    val id = itemMap["id"] as? String ?: continue
                    val name = itemMap["name"] as? String ?: continue
                    val input = itemMap["input"] as? Map<String, Any> ?: emptyMap()
                    toolUses.add(mapOf("id" to id, "name" to name, "input" to input))
                }
            }
        }

        val text = textParts.joinToString("")
        appendPartialOutput(text, allText, onPartialOutput)

        if (stopReason == "max_tokens") {
            val usage = resp["usage"] as? Map<*, *>
            val outputTokens = (usage?.get("output_tokens") as? Number)?.toInt() ?: 0
            val thinkingTokens =
                ((usage?.get("output_tokens_details") as? Map<*, *>)?.get("thinking_tokens") as? Number)?.toInt() ?: 0
            val truncatedChars = allText.length + text.length
            log.warn(
                "[RepoFileTools] {} tool-loop truncated at max_tokens after {} iterations | output={} thinking={} chars={}",
                providerLabel,
                iteration,
                outputTokens,
                thinkingTokens,
                truncatedChars,
            )
            throw StackLensFailureException(StackLensFailure.AiOutputTruncated(outputTokens, thinkingTokens, truncatedChars))
        }

        if (toolUses.isEmpty() || stopReason == "end_turn" || stopReason == "stop_sequence") {
            logLoopDone(iteration, allText)
            return false
        }

        logToolCalls(iteration, toolUses.size, "tool_use block(s)")

        val assistantContent: MutableList<Map<String, Any>> = mutableListOf()
        if (text.isNotBlank()) {
            assistantContent.add(
                mapOf<String, Any>("type" to "text", "text" to text),
            )
        }
        for (tu in toolUses) {
            assistantContent.add(
                mapOf<String, Any>(
                    "type" to "tool_use",
                    "id" to (tu["id"] as String),
                    "name" to (tu["name"] as String),
                    "input" to (tu["input"] as Map<String, Any>),
                ),
            )
        }
        messages.add(mapOf("role" to "assistant", "content" to assistantContent))

        val toolResultBlocks: MutableList<Map<String, Any>> = mutableListOf()
        for (tu in toolUses) {
            val id = tu["id"] as String
            val name = tu["name"] as String
            val args = tu["input"] as Map<String, Any>
            val result = RepoFileToolExecutor.execute(name, args, repoDir, log)
            toolResultBlocks.add(
                mapOf<String, Any>(
                    "type" to "tool_result",
                    "tool_use_id" to id,
                    "content" to result,
                ),
            )
        }
        messages.add(mapOf("role" to "user", "content" to toolResultBlocks))
        return true
    }

    interface ChatTransport {
        val providerLabel: String

        fun send(
            body: String,
            timeoutSeconds: Long,
        ): String
    }

    companion object {
        const val DEFAULT_MAX_ITERATIONS: Int = 60

        const val DEFAULT_MAX_TOKENS: Int = ClaudeCodeAdapter.DEFAULT_MAX_TOKENS
    }
}
