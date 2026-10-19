package com.stacklens.application.port.inbound.loadtest

import com.stacklens.application.port.inbound.analysis.AnalysisCrudUseCase
import com.stacklens.domain.model.LoadTestAnalysis

interface LoadTestAnalysisUseCase :
    LoadTestAnalysisReadUseCase,
    LoadTestAnalysisStartUseCase,
    K6ExecutionUseCase,
    AnalysisCrudUseCase<LoadTestAnalysis>

data class K6StatusResult(
    val running: Boolean,
    val exitCode: Int?,
    val output: String,
    val error: String,
    val summaryJson: String? = null,
)
