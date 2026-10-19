package com.stacklens.application.port.out.ai

import com.stacklens.domain.model.AiTokenUsageEntity
import com.stacklens.domain.model.AiUsage

interface AiTokenUsagePort {
    fun record(
        kind: String,
        analysisId: Long,
        callIndex: Int,
        usage: AiUsage,
    )

    fun findAll(
        kind: String,
        analysisId: Long,
    ): List<AiTokenUsageEntity>

    fun aggregate(
        kind: String,
        analysisId: Long,
    ): AiUsage?

    fun deleteAll(
        kind: String,
        analysisId: Long,
    )
}
