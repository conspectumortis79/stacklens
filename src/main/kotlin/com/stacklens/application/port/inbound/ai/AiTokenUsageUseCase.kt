package com.stacklens.application.port.inbound.ai

import com.stacklens.domain.model.AiUsage

interface AiTokenUsageUseCase {
    fun getUsage(
        kind: String,
        analysisId: Long,
    ): AiUsage?
}
