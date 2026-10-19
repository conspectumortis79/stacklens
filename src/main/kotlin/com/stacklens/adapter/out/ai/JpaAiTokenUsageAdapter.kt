package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.persistence.AiTokenUsageRepository
import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.domain.model.AiTokenUsageEntity
import com.stacklens.domain.model.AiUsage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JpaAiTokenUsageAdapter(
    private val repo: AiTokenUsageRepository,
) : AiTokenUsagePort {
    @Transactional
    override fun record(
        kind: String,
        analysisId: Long,
        callIndex: Int,
        usage: AiUsage,
    ) {
        repo.save(AiTokenUsageEntity.from(kind, analysisId, callIndex, usage))
    }

    @Transactional(readOnly = true)
    override fun findAll(
        kind: String,
        analysisId: Long,
    ): List<AiTokenUsageEntity> = repo.findByAnalysisKindAndAnalysisIdOrderByCallIndexAsc(kind, analysisId)

    @Transactional(readOnly = true)
    override fun aggregate(
        kind: String,
        analysisId: Long,
    ): AiUsage? {
        val rows = repo.findByAnalysisKindAndAnalysisIdOrderByCallIndexAsc(kind, analysisId)
        if (rows.isEmpty()) return null
        var prompt = 0L
        var completion = 0L
        var savings = 0L
        var duration = 0L
        var model = ""
        var provider = ""
        for (row in rows) {
            prompt += row.promptTokens
            completion += row.completionTokens
            savings += row.filterTokensSaved
            duration += row.durationMs
            if (row.model.isNotBlank()) model = row.model
            if (row.provider.isNotBlank()) provider = row.provider
        }
        return AiUsage(
            promptTokens = prompt,
            completionTokens = completion,
            filterTokensSaved = savings,
            model = model,
            provider = provider,
            durationMs = duration,
        )
    }

    @Transactional
    override fun deleteAll(
        kind: String,
        analysisId: Long,
    ) {
        repo.deleteByKindAndAnalysisId(kind, analysisId)
    }
}
