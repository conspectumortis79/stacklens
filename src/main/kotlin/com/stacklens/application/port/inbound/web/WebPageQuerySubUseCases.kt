package com.stacklens.application.port.inbound.web

interface ConfigPageQueryUseCase {
    fun getConfigPageData(): ConfigPageData
}

interface SearchPageQueryUseCase {
    fun getSuchePageData(): SuchePageData
}

interface ReviewPageQueryUseCase {
    fun getReviewAnalysePageData(): ReviewAnalysePageData
}

interface TicketPageQueryUseCase {
    fun getTicketImplementationPageData(): TicketImplementationPageData
}

interface CodeAnalysePageQueryUseCase {
    fun getCodeAnalysePageData(): CodeAnalysePageData
}

interface LastTestAnalysePageQueryUseCase {
    fun getLastTestAnalysePageData(): LastTestAnalysePageData
}

interface SecurityAnalysePageQueryUseCase {
    fun getSecurityAnalysePageData(): SecurityAnalysePageData
}

interface CodeQaPageQueryUseCase {
    fun getCodeQaAnalysisPageData(): CodeQaAnalysisPageData
}

interface PipelineAnalysePageQueryUseCase {
    fun getPipelineAnalysePageData(): PipelineAnalysePageData
}

interface LoggingAnalysePageQueryUseCase {
    fun getLoggingAnalysePageData(): LoggingAnalysePageData
}
