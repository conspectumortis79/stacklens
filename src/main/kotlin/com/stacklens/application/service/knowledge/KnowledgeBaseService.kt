package com.stacklens.application.service.knowledge

import com.stacklens.application.collaboration.KnowledgeBaseReader
import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ApplicationTransactional
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.slf4j.LoggerFactory

@ApplicationService
class KnowledgeBaseService(
    private val basePort: KnowledgeBasePort,
    private val sourcePort: KnowledgeSourcePort,
    private val documentPort: KnowledgeDocumentPort,
    private val chunkPort: KnowledgeChunkPort,
    private val indexPort: com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort,
    private val vectorIndexPort: com.stacklens.application.port.out.knowledge.KnowledgeIndexPort,
    private val clock: ClockPort,
) : KnowledgeBaseUseCase,
    KnowledgeBaseReader {
    private val log = LoggerFactory.getLogger(KnowledgeBaseService::class.java)

    override fun listBases(): List<KnowledgeBase> = basePort.findAllByOrderByCreatedAtAsc()

    override fun createBase(
        name: String,
        description: String,
        includeInGlobalChat: Boolean,
    ): KnowledgeBase {
        val now = clock.localDateTime()
        return basePort.save(
            KnowledgeBase(
                name = name,
                description = description,
                includeInGlobalChat = includeInGlobalChat,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override fun updateBase(
        id: Long,
        name: String?,
        description: String?,
        active: Boolean?,
        includeInGlobalChat: Boolean?,
    ): KnowledgeBase {
        val existing = basePort.findById(id) ?: error("Knowledge base $id not found")
        if (name != null) existing.name = name
        if (description != null) existing.description = description
        if (active != null) existing.active = active
        if (includeInGlobalChat != null) existing.includeInGlobalChat = includeInGlobalChat
        existing.updatedAt = clock.localDateTime()
        return basePort.save(existing)
    }

    @ApplicationTransactional
    override fun clearBase(id: Long) {
        val base = basePort.findById(id) ?: error("Knowledge base $id not found")
        chunkPort.deleteByKnowledgeBaseId(id)
        documentPort.deleteByKnowledgeBaseId(id)
        sourcePort.deleteByKnowledgeBaseId(id)
        indexPort.deleteByKnowledgeBaseId(id)
        vectorIndexPort.clearKnowledgeBase(id)
        log.info("Cleared knowledge base {} ({})", id, base.name)
    }

    @ApplicationTransactional
    override fun deleteBase(id: Long) {
        val base = basePort.findById(id) ?: return
        clearBase(id)
        basePort.deleteById(id)
    }

    @ApplicationTransactional
    override fun ensureAnalysisBase() {
        if (basePort.findAllByOrderByCreatedAtAsc().any { it.systemManaged }) return
        val now = clock.localDateTime()
        basePort.save(
            KnowledgeBase(
                name = DEFAULT_ANALYSIS_BASE,
                description = "Automatisch indexierte Analyseergebnisse aus Review, Sprint-Review und Ticket-Workflows.",
                includeInGlobalChat = true,
                systemManaged = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        log.info("Created system-managed analysis knowledge base")
    }

    companion object {
        const val DEFAULT_ANALYSIS_BASE = "StackLens-Analyseergebnisse"
    }
}
