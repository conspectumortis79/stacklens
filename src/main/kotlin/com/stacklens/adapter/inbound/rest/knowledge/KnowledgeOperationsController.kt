package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeMaintenanceUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeReindexUseCase
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/knowledge")
class KnowledgeOperationsController(
    private val maintenanceUseCase: KnowledgeMaintenanceUseCase,
    private val reindexService: KnowledgeReindexUseCase,
) {
    @PostMapping("/reset")
    fun resetKnowledge(): Map<String, Any> =
        mapOf(
            "ok" to true,
            "resetAt" to maintenanceUseCase.resetKnowledge().toString(),
        )

    @PostMapping("/reindex-all")
    fun reindexAll(): Map<String, Any> {
        val results = reindexService.reindexAll()
        return mapOf(
            "ok" to true,
            "baseCount" to results.size,
            "totalChunks" to results.sumOf { it.chunkCount },
            "results" to results.associate { it.knowledgeBaseId to it.chunkCount },
        )
    }
}
