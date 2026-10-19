package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.AiUsageExtractor
import com.stacklens.application.port.out.ai.ExtractedUsage

class DefaultAiUsageExtractor(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : AiUsageExtractor {
    override fun extract(
        responseBody: String,
        providerLabel: String,
    ): ExtractedUsage? {
        if (responseBody.isBlank()) return null
        val label = providerLabel.lowercase()
        val node =
            try {
                objectMapper.readTree(responseBody)
            } catch (_: Exception) {
                return null
            } ?: return null

        if (label.contains("openai") || label.contains("openrouter") || label.contains("lmstudio") || label.contains("lm studio")) {
            return extractFromUsageBlock(node)
        }

        if (label.contains("claude")) {
            return extractFromClaudeUsageBlock(node)
        }

        if (label.contains("ollama")) {
            extractFromUsageBlock(node)?.let { return it }
            return extractFromOllamaNative(node)
        }

        return null
    }

    private fun extractFromUsageBlock(node: JsonNode): ExtractedUsage? {
        val usage = node["usage"] ?: return null
        val inTokens = usage["prompt_tokens"]?.asLong(-1L) ?: -1L
        val outTokens = usage["completion_tokens"]?.asLong(-1L) ?: -1L
        if (inTokens < 0 || outTokens < 0) return null
        return ExtractedUsage(
            promptTokens = inTokens.coerceAtLeast(0L),
            completionTokens = outTokens.coerceAtLeast(0L),
        )
    }

    private fun extractFromClaudeUsageBlock(node: JsonNode): ExtractedUsage? {
        val usage = node["usage"] ?: return null
        val inTokens = usage["input_tokens"]?.asLong(-1L) ?: -1L
        val outTokens = usage["output_tokens"]?.asLong(-1L) ?: -1L
        if (inTokens < 0 || outTokens < 0) return null
        return ExtractedUsage(
            promptTokens = inTokens.coerceAtLeast(0L),
            completionTokens = outTokens.coerceAtLeast(0L),
        )
    }

    private fun extractFromOllamaNative(node: JsonNode): ExtractedUsage? {
        val inTokens = node["prompt_eval_count"]?.asLong(-1L) ?: -1L
        val outTokens = node["eval_count"]?.asLong(-1L) ?: -1L
        if (inTokens < 0 || outTokens < 0) return null
        return ExtractedUsage(
            promptTokens = inTokens.coerceAtLeast(0L),
            completionTokens = outTokens.coerceAtLeast(0L),
        )
    }
}
