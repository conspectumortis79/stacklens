package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.domain.model.ReviewAnalysis

class ReviewAnalysisDtoMapper(
    private val decoding: ReviewAnalysisDecodingUseCase,
) {
    fun toSummaryMap(analysis: ReviewAnalysis): Map<String, Any?> =
        mapOf(
            "id" to analysis.id,
            "jiraSprintUrl" to analysis.jiraSprintUrl,
            "jiraConfigId" to analysis.jiraConfigId,
            "status" to analysis.status,
            "currentStep" to analysis.currentStep,
            "errorMessage" to analysis.errorMessage,
            "pptxFileName" to analysis.pptxFileName,
            "pptxSlideCount" to analysis.pptxSlideCount,
            "pptxDetectedLanguage" to analysis.pptxDetectedLanguage,
            "createdAt" to analysis.createdAt.toString(),
            "completedAt" to analysis.completedAt?.toString(),
        )

    fun toDetailMap(analysis: ReviewAnalysis): Map<String, Any?> {
        val sprintName = decoding.sprintName(analysis.sprintInfoJson)
        return mapOf(
            "id" to analysis.id,
            "jiraSprintUrl" to analysis.jiraSprintUrl,
            "jiraConfigId" to analysis.jiraConfigId,
            "analysisInstructions" to analysis.analysisInstructions,
            "status" to analysis.status,
            "currentStep" to analysis.currentStep,
            "errorMessage" to analysis.errorMessage,
            "resultMarkdown" to analysis.resultMarkdown,
            "processLog" to decoding.processLogEntries(analysis.processLogJson),
            "sprintName" to sprintName,
            "pptxFileName" to analysis.pptxFileName,
            "pptxSlideCount" to analysis.pptxSlideCount,
            "resultHtmlPath" to analysis.resultHtmlPath,
            "pptxQuestionsJson" to analysis.pptxQuestionsJson,
            "pptxAnswersJson" to analysis.pptxAnswersJson,
            "pptxDetectedLanguage" to analysis.pptxDetectedLanguage,
            "createdAt" to analysis.createdAt.toString(),
            "completedAt" to analysis.completedAt?.toString(),
        )
    }
}
