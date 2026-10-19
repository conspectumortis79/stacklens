package com.stacklens.application.port.inbound.analysis

import com.stacklens.domain.model.CodeAnalysis

interface CodeAnalysisUseCase :
    CodeAnalysisReadUseCase,
    CodeAnalysisWriteUseCase,
    AnalysisCrudUseCase<CodeAnalysis>
