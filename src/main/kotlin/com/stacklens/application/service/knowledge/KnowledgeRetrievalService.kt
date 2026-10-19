package com.stacklens.application.service.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeRetrievalUseCase
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.RetrievedKnowledge
import org.slf4j.LoggerFactory

@ApplicationService
class KnowledgeRetrievalService(
    private val chunkPort: KnowledgeChunkPort,
    private val documentPort: KnowledgeDocumentPort,
    private val basePort: KnowledgeBasePort,
    private val configPort: KnowledgeEmbeddingConfigPort,
    private val embeddingDispatcher: KnowledgeEmbeddingDispatcher,
    private val indexPort: com.stacklens.application.port.out.knowledge.KnowledgeIndexPort,
    private val rerankPort: com.stacklens.application.port.out.ai.KnowledgeRerankPort,
) : KnowledgeRetrievalUseCase,
    KnowledgeRetrievalPort {
    private val log = LoggerFactory.getLogger(KnowledgeRetrievalService::class.java)

    override fun retrieveForChat(
        query: String,
        limit: Int?,
    ): List<RetrievedKnowledge> {
        if (query.isBlank()) return emptyList()
        val config = configPort.get()
        val topK = limit ?: config.topK
        val postCfg = config.postRetrieval
        val activeBases = basePort.findAllByActiveTrue().filter { it.includeInGlobalChat }
        if (activeBases.isEmpty()) return emptyList()

        val searchQuery =
            if (postCfg.queryRewritingEnabled) {
                rerankPort.rewriteQueryForSearch(query, postCfg.queryRewriteMaxOutputTokens) ?: query
            } else {
                query
            }
        if (!config.enabled) {
            log.debug("Knowledge retrieval skipped: embedding config is disabled")
            return emptyList()
        }

        val ticketKeyCandidates = TicketKeyExtractor.findAll(query)
        val exactHits = mutableListOf<RetrievedKnowledge>()
        if (ticketKeyCandidates.isNotEmpty()) {
            for (key in ticketKeyCandidates) {
                for (base in activeBases) {
                    for (hit in indexPort.searchByTicketKey(base.id, key)) {
                        exactHits +=
                            RetrievedKnowledge(
                                chunkId = hit.chunkId,
                                documentId = hit.documentId,
                                knowledgeBaseId = base.id,
                                title = hit.title,
                                sourceType =
                                    com.stacklens.domain.model.knowledge.KnowledgeSourceType
                                        .valueOf(hit.sourceType),
                                sourceUri = hit.sourceUri,
                                content = hit.content,
                                score = hit.score,
                                citation = hit.citation,
                            )
                    }
                }
            }
            log.debug(
                "Ticket-key lookup found {} chunks for keys {} in query",
                exactHits.size,
                ticketKeyCandidates,
            )
        }

        val queryVector =
            try {
                embeddingDispatcher.embed(listOf(searchQuery)).vectors.firstOrNull()
            } catch (e: Exception) {
                log.warn("Query embedding failed, returning no knowledge: {}", e.message)
                return exactHits.take(topK)
            }
        val candidateCount =
            if (postCfg.rerankEnabled) maxOf(topK, postCfg.rerankCandidateCount) else topK
        val vectorHits = mutableListOf<RetrievedKnowledge>()
        if (queryVector != null) {
            for (base in activeBases) {
                val raw = indexPort.searchByVector(base.id, queryVector, candidateCount)
                for (hit in raw) {
                    vectorHits +=
                        RetrievedKnowledge(
                            chunkId = hit.chunkId,
                            documentId = hit.documentId,
                            knowledgeBaseId = base.id,
                            title = hit.title,
                            sourceType =
                                com.stacklens.domain.model.knowledge.KnowledgeSourceType
                                    .valueOf(hit.sourceType),
                            sourceUri = hit.sourceUri,
                            content = hit.content,
                            score = hit.score,
                            citation = hit.citation,
                        )
                }
            }
        }

        val merged =
            (exactHits.asSequence() + vectorHits.asSequence())
                .groupBy { it.chunkId }
                .map { (_, list) -> list.maxBy { it.score } }
                .sortedByDescending { it.score }
                .take(candidateCount)

        if (merged.isEmpty()) return emptyList()

        return if (postCfg.rerankEnabled) {
            val finalCount = minOf(postCfg.rerankFinalCount, topK)
            rerankPort.rerankChunks(query, merged, finalCount, postCfg.rerankMaxOutputTokens)
                ?: merged.take(topK)
        } else {
            merged.take(topK)
        }
    }
}

internal object TicketKeyExtractor {
    private val PATTERN = Regex("[A-Z][A-Z0-9_]+-\\d+", RegexOption.IGNORE_CASE)

    fun findAll(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        for (match in PATTERN.findAll(query)) {
            seen.add(match.value.uppercase())
        }
        return seen.toList()
    }
}
