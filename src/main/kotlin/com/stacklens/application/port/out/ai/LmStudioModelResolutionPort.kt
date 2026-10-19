package com.stacklens.application.port.out.ai

import com.stacklens.domain.model.AiConfig

interface LmStudioModelResolutionPort {
    fun resolveLmStudioModel(
        requestedModel: String?,
        requestedLmStudioUrl: String?,
        existing: AiConfig,
    ): String
}
