package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class KnowledgeSourceLister(
    private val sourcePort: KnowledgeSourcePort,
    private val chunkPort: KnowledgeChunkPort,
) {
    fun listSources(knowledgeBaseId: Long?): List<KnowledgeSource> {
        val sources =
            if (knowledgeBaseId == null) {
                sourcePort.findAllByOrderByCreatedAtAsc()
            } else {
                sourcePort.findByKnowledgeBaseId(knowledgeBaseId)
            }
        if (sources.isEmpty()) return sources
        val sizes = chunkPort.sumContentLengthBySourceIds(sources.map { it.id })
        for (source in sources) {
            source.totalBytes = sizes[source.id]
        }
        return sources
    }

    fun listSourcesByType(type: KnowledgeSourceType): List<KnowledgeSource> {
        val sources = sourcePort.findAllByOrderByCreatedAtAsc().filter { it.type == type }
        if (sources.isEmpty()) return sources
        val sizes = chunkPort.sumContentLengthBySourceIds(sources.map { it.id })
        for (source in sources) {
            source.totalBytes = sizes[source.id]
        }
        return sources
    }

    fun getSource(id: Long): KnowledgeSource? {
        val source = sourcePort.findById(id) ?: return null
        val sizes = chunkPort.sumContentLengthBySourceIds(listOf(id))
        source.totalBytes = sizes[id]
        return source
    }
}
