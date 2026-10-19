package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.KnowledgeSourceChip
import com.stacklens.application.port.out.platform.AppKnowledgePort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.RetrievedKnowledge

@ApplicationService
class KiChatPromptBuilder(
    private val promptLoader: PromptReaderPort,
    private val appKnowledge: AppKnowledgePort,
) {
    fun build(
        req: ChatRequest,
        languageInstruction: String,
        mcpResults: String,
        retrievedKnowledge: List<RetrievedKnowledge>,
    ): String =
        buildString {
            appendStaticPromptSections(req, languageInstruction)
            appendKnowledgeSections(req)
            appendRetrievedKnowledgeSection(req, retrievedKnowledge)
            appendSystemHintSection(req)
            appendMcpSection(mcpResults)
            appendTabContextSection(req)
            appendHistorySection(req)
            appendClosingSections(languageInstruction)
            appendLine("## User Message")
            appendLine(req.userMessage)
        }

    fun chipsFor(retrieved: List<RetrievedKnowledge>): List<KnowledgeSourceChip> =
        retrieved.map { item ->
            KnowledgeSourceChip(
                citation = item.citation,
                title = item.title,
                sourceType = item.sourceType.name,
                sourceUri = item.sourceUri,
                score = item.score,
                documentId = item.documentId,
                chunkId = item.chunkId,
                preview = item.content.take(280),
            )
        }

    private fun StringBuilder.appendStaticPromptSections(
        req: ChatRequest,
        languageInstruction: String,
    ) {
        val tabLabel = req.tabLabel.ifBlank { req.tabId }
        val vars = mapOf("tabLabel" to tabLabel, "languageInstruction" to languageInstruction)
        appendLine(promptLoader.get("en", "kichat.roleIntro", vars))
        appendLine()
        appendLine(promptLoader.get("en", "kichat.responseFormat"))
        appendLine()
    }

    private fun StringBuilder.appendKnowledgeSections(req: ChatRequest) {
        val knowledgeIndex = runCatching { appKnowledge.index() }.getOrDefault("")
        val knowledgeRelevant = runCatching { appKnowledge.relevant(req.userMessage, 4, 3000) }.getOrDefault("")
        val mentionsApp = KiChatService.MetaKeywords.any { kw -> req.userMessage.contains(kw, ignoreCase = true) }
        if (knowledgeIndex.isNotBlank()) {
            appendLine("## StackLens knowledge base (always available)")
            appendLine(knowledgeIndex.take(2500))
            appendLine()
        }
        if (knowledgeRelevant.isNotBlank() || mentionsApp) {
            appendLine("## Relevant StackLens documentation excerpts")
            if (knowledgeRelevant.isNotBlank()) {
                appendLine(knowledgeRelevant.take(12000))
            } else {
                appKnowledge.readme()?.let {
                    appendLine("===== README.md =====")
                    appendLine(it.take(8000))
                }
            }
            appendLine()
            appendLine(
                "Use the excerpts above as primary source-of-truth when answering questions about StackLens features, agents, prompts, analyses, or how the app works. Always cite the file path you draw from in parentheses, e.g. \"(prompts/en/securityanalysis.md)\".",
            )
            appendLine()
        }
    }

    private fun StringBuilder.appendSystemHintSection(req: ChatRequest) {
        if (req.systemHint.isNotBlank()) {
            appendLine("## Tab-specific instructions")
            appendLine(req.systemHint.take(2000))
            appendLine()
        }
    }

    private fun StringBuilder.appendRetrievedKnowledgeSection(
        req: ChatRequest,
        retrieved: List<RetrievedKnowledge>,
    ) {
        if (retrieved.isEmpty()) return
        appendLine("## Retrieved StackLens knowledge (RAG)")
        appendLine(promptLoader.get("en", "kichat.ragSourceCitation"))
        val grouped = retrieved.groupBy { it.knowledgeBaseId to it.documentId }
        for ((_, group) in grouped) {
            for (item in group) {
                appendLine()
                appendLine(item.citation)
                appendLine("Title: ${item.title}")
                appendLine("Source: ${item.sourceUri ?: item.sourceType.name}")
                appendLine("Score: ${"%.3f".format(item.score)}")
                appendLine("Content:")
                appendLine(item.content.take(2000))
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendMcpSection(mcpResults: String) {
        if (mcpResults.isNotBlank()) {
            appendLine("## Live data fetched via MCP tools")
            appendLine(
                "The following results were retrieved from live systems to answer the user's question. Use them as the primary source for your answer.",
            )
            appendLine(mcpResults.take(20000))
            appendLine()
        }
    }

    private fun StringBuilder.appendTabContextSection(req: ChatRequest) {
        if (req.tabContext.isNotBlank()) {
            appendLine("## Current context from this tab")
            appendLine(
                "This includes the active tab's structured state (selected repo, summary text, full findings JSON when available) and a DOM snapshot of what the user currently sees. Treat this as authoritative for questions like \"explain all bugs\" or \"why did the analysis flag X?\".",
            )
            appendLine(req.tabContext.take(24000))
            appendLine()
        }
    }

    private fun StringBuilder.appendHistorySection(req: ChatRequest) {
        if (req.history.isNotEmpty()) {
            appendLine("## Previous conversation")
            for (turn in req.history.takeLast(20)) {
                val who = if (turn.role.equals("assistant", true)) "Assistant" else "User"
                appendLine("$who: ${turn.content.take(2000)}")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendClosingSections(languageInstruction: String) {
        appendLine(
            promptLoader.get(
                "en",
                "kichat.closingInstruction",
                mapOf("languageInstruction" to languageInstruction),
            ),
        )
        appendLine()
        appendLine(promptLoader.get("en", "kichat.downloads"))
        appendLine()
        appendLine(promptLoader.get("en", "kichat.windowDirective"))
        appendLine()
    }
}
