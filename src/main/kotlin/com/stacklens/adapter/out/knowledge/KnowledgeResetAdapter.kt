package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeResetPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Component
class KnowledgeResetAdapter(
    private val basePort: KnowledgeBasePort,
    private val sourcePort: KnowledgeSourcePort,
    private val documentPort: KnowledgeDocumentPort,
    private val chunkPort: KnowledgeChunkPort,
    private val jobPort: KnowledgeIngestionJobPort,
    private val indexGenerationPort: KnowledgeIndexGenerationPort,
    private val vectorIndexPort: KnowledgeIndexPort,
    @Value("\${stacklens.knowledge.index-dir:\${user.home}/.stacklens/data/knowledge-index}")
    private val indexDir: String,
) : KnowledgeResetPort {
    private val log = LoggerFactory.getLogger(KnowledgeResetAdapter::class.java)

    override fun resetAllKnowledgeData() {
        log.warn("Resetting ALL knowledge base data (factory reset)")
        chunkPort.deleteAll()
        documentPort.deleteAll()
        sourcePort.deleteAll()
        jobPort.deleteAll()
        indexGenerationPort.deleteAll()
        basePort.deleteAll()
        bestEffortDeleteIndexDir()
    }

    override fun clearKnowledgeBase(base: KnowledgeBase) {
        chunkPort.deleteByKnowledgeBaseId(base.id)
        documentPort.deleteByKnowledgeBaseId(base.id)
        sourcePort.deleteByKnowledgeBaseId(base.id)
        indexGenerationPort.deleteByKnowledgeBaseId(base.id)
        vectorIndexPort.clearKnowledgeBase(base.id)
    }

    private fun bestEffortDeleteIndexDir() {
        val dir: Path = Paths.get(indexDir)
        bestEffortDelete(dir)
    }

    private fun bestEffortDelete(path: Path) {
        if (!Files.exists(path)) return
        try {
            Files
                .walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach { child -> Files.deleteIfExists(child) }
        } catch (e: Exception) {
            log.warn("Best-effort delete failed for {}: {}", path, e.message)
        }
    }
}
