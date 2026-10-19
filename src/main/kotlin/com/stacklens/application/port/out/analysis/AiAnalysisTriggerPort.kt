package com.stacklens.application.port.out.analysis

import com.stacklens.domain.model.AiAnalysis

interface AiAnalysisTriggerPort {
    fun startAnalysis(
        sessionId: Long,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ): AiAnalysis
}
