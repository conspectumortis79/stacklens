package com.stacklens.application.port.inbound.code

import com.stacklens.domain.model.CodeQaAnalysis

interface CodeQaAnalysisReadUseCase {
    fun getAnalysis(id: Long): CodeQaAnalysis?

    fun getAllAnalyses(): List<CodeQaAnalysis>
}

interface CodeQaStreamingUseCase : CodeQaAnalysisReadUseCase {
    fun getStreamingOutput(id: Long): String?

    fun getIntegrationTestStreamingOutput(id: Long): String?

    fun registerStreamingChangeListener(
        id: Long,
        listener: () -> Unit,
    ): () -> Unit

    fun unregisterStreamingChangeListener(
        id: Long,
        listener: () -> Unit,
    )
}

interface CodeQaAnalysisWriteUseCase {
    fun startAnalysis(
        mrUrl: String,
        jiraTicketKey: String?,
        jiraConfigId: Long?,
        gitConfigId: Long? = null,
        gitPlatform: String? = null,
        additionalInstructions: String? = null,
        integrationTestEnabled: Boolean = false,
    ): CodeQaAnalysis

    fun deleteAnalysis(id: Long)

    fun deleteAllAnalyses()

    fun cancelAnalysis(id: Long)

    fun postSuggestion(
        analysisId: Long,
        filePath: String,
        lineNumber: Int,
        suggestedCode: String,
    ): String
}

interface CodeQaAnalysisUseCase :
    CodeQaStreamingUseCase,
    CodeQaAnalysisWriteUseCase
