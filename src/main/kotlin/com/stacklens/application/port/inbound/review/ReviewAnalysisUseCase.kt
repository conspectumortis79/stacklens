package com.stacklens.application.port.inbound.review

import com.stacklens.domain.model.ReviewAnalysis

interface ReviewAnalysisReadUseCase {
    fun getAnalysis(id: Long): ReviewAnalysis?

    fun getAllAnalyses(): List<ReviewAnalysis>

    fun jiraConfigExists(id: Long): Boolean

    fun firstActiveJiraConfigId(): Long?

    fun resolveLanguage(): String
}

interface ReviewAnalysisWriteUseCase {
    fun startAnalysis(
        jiraSprintUrl: String,
        jiraConfigId: Long,
        analysisInstructions: String,
    ): ReviewAnalysis

    fun startAnalysisWithPptx(
        jiraSprintUrl: String,
        jiraConfigId: Long,
        analysisInstructions: String,
        pptxFileName: String,
        pptxBytes: ByteArray,
    ): ReviewAnalysis

    fun startAnalysisWithPptxPath(
        jiraSprintUrl: String,
        jiraConfigId: Long,
        analysisInstructions: String,
        pptxServerPath: String,
    ): ReviewAnalysis

    fun deleteAnalysis(id: Long)

    fun deleteAllAnalyses()
}

interface ReviewAnalysisUseCase :
    ReviewAnalysisReadUseCase,
    ReviewAnalysisWriteUseCase
