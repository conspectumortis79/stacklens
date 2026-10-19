package com.stacklens.application.service.ai

import com.stacklens.application.port.inbound.ai.AiTokenUsageUseCase
import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.AiUsage

@ApplicationService
class AiTokenUsageService(
    private val usagePort: AiTokenUsagePort,
) : AiTokenUsageUseCase {
    override fun getUsage(
        kind: String,
        analysisId: Long,
    ): AiUsage? = usagePort.aggregate(kind, analysisId)
}
