package com.stacklens.application.port.inbound.analysis

import com.stacklens.domain.model.AiAnalysis

interface AiAnalysisReadUseCase {
    fun getLatestAnalysis(sessionId: Long): AiAnalysis?

    fun sessionExists(sessionId: Long): Boolean
}

interface AiAnalysisWriteUseCase {
    fun saveDiagram(
        path: String,
        filename: String,
        content: String,
    ): String

    fun startAnalysis(
        sessionId: Long,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ): AiAnalysis
}

interface AiAnalysisUseCase :
    AiAnalysisReadUseCase,
    AiAnalysisWriteUseCase
