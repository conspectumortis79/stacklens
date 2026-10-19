package com.stacklens.application.port.inbound.analysis

import com.stacklens.domain.model.AnalysisRecord

interface AnalysisCrudUseCase<A : AnalysisRecord> {
    fun getAnalysis(analysisId: Long): A?

    fun getAllAnalyses(): List<A>

    fun deleteAnalysis(analysisId: Long)

    fun cancelAnalysis(analysisId: Long)
}
