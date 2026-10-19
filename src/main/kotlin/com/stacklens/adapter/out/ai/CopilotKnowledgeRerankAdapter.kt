package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiChatPort
import com.stacklens.application.port.out.ai.KnowledgeRerankPort
import com.stacklens.domain.model.knowledge.RetrievedKnowledge
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CopilotKnowledgeRerankAdapter(
    private val copilot: AiChatPort,
) : KnowledgeRerankPort {
    private val log = LoggerFactory.getLogger(CopilotKnowledgeRerankAdapter::class.java)

    override fun rewriteQueryForSearch(
        originalQuestion: String,
        maxOutputTokens: Int,
    ): String? {
        if (originalQuestion.isBlank() || !copilot.isReady()) return null
        val model = copilot.resolveModel()
        val prompt = buildRewritePrompt(originalQuestion)
        return runCatching {
            val raw = copilot.execute(model, prompt)
            raw.trim().take(maxOutputTokens * CHARS_PER_TOKEN).takeIf { it.isNotBlank() }
        }.onFailure {
            log.warn("Query-Rewriting failed (provider={}, model={}): {}", copilot.providerLabel, model, it.message)
        }.getOrNull()
    }

    override fun rerankChunks(
        question: String,
        candidates: List<RetrievedKnowledge>,
        finalCount: Int,
        maxOutputTokens: Int,
    ): List<RetrievedKnowledge>? {
        if (!copilot.isReady()) return null
        if (candidates.size <= finalCount) return candidates
        val model = copilot.resolveModel()
        val prompt = buildRerankPrompt(question, candidates, finalCount)
        return runCatching {
            val raw = copilot.execute(model, prompt)
            parseRerankResponse(raw.take(maxOutputTokens * CHARS_PER_TOKEN), candidates, finalCount)
        }.onFailure {
            log.warn(
                "LLM-Re-Rank failed (provider={}, model={}, candidates={}): {}",
                copilot.providerLabel,
                model,
                candidates.size,
                it.message,
            )
        }.getOrNull()
    }

    private fun buildRewritePrompt(question: String): String =
        """
        You are a search-query optimizer. Rewrite the user's question as a
        short, keyword-focused search query (3-7 keywords) that would
        match relevant document chunks in a local knowledge database.
        Keep technical terms, product names, error messages, and identifiers.
        Output ONLY the rewritten query, no explanation, no quotes.

        Question: "$question"
        Rewritten query:
        """.trimIndent()

    private fun buildRerankPrompt(
        question: String,
        candidates: List<RetrievedKnowledge>,
        finalCount: Int,
    ): String {
        val chunkList =
            candidates
                .mapIndexed { i, c ->
                    "[$i] ${c.citation} — ${c.title}\n${c.content.take(400)}"
                }.joinToString("\n\n---\n\n")
        return """
            You are a relevance ranker. Given a question and ${candidates.size}
            document chunks, output the indices of the $finalCount most
            relevant chunks sorted by relevance, most relevant first.
            Output ONLY space-separated integer indices, nothing else.

            Question: "$question"

            Chunks:
            $chunkList

            Sorted indices:
            """.trimIndent()
    }

    internal fun parseRerankResponse(
        response: String,
        candidates: List<RetrievedKnowledge>,
        finalCount: Int,
    ): List<RetrievedKnowledge> {
        val indices =
            response
                .trim()
                .split(Regex("[\\s,]+"))
                .mapNotNull { it.toIntOrNull() }
                .filter { it in candidates.indices }
                .distinct()
        if (indices.isEmpty()) return candidates.take(finalCount)
        if (indices.size < finalCount) {
            val taken = indices.toSet()
            val padding = candidates.indices.filter { it !in taken }
            return (indices + padding).take(finalCount).map { candidates[it] }
        }
        return indices.take(finalCount).map { candidates[it] }
    }

    companion object {
        internal const val CHARS_PER_TOKEN: Int = 4
    }
}
