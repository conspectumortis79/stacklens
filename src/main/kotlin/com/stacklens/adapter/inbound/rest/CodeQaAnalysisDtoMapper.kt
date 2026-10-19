package com.stacklens.adapter.inbound.rest

import com.stacklens.domain.model.CodeQaAnalysis

class CodeQaAnalysisDtoMapper {
    fun toStatusMap(analysis: CodeQaAnalysis): Map<String, Any?> =
        mapOf(
            "id" to analysis.id,
            "status" to analysis.status,
            "currentStep" to analysis.currentStep,
            "logOutput" to analysis.logOutput,
            "errorMessage" to analysis.errorMessage,
            "completedAt" to analysis.completedAt?.toString(),
            "integrationTestEnabled" to analysis.integrationTestEnabled,
            "integrationTestStatus" to analysis.integrationTestStatus,
        )

    fun toFullMap(analysis: CodeQaAnalysis): Map<String, Any?> =
        mapOf(
            "id" to analysis.id,
            "mrUrl" to analysis.mrUrl,
            "jiraTicketKey" to analysis.jiraTicketKey,
            "status" to analysis.status,
            "currentStep" to analysis.currentStep,
            "logOutput" to analysis.logOutput,
            "resultMarkdown" to analysis.resultMarkdown,
            "errorMessage" to analysis.errorMessage,
            "createdAt" to analysis.createdAt.toString(),
            "completedAt" to analysis.completedAt?.toString(),
            "diffBaseSha" to analysis.diffBaseSha,
            "diffStartSha" to analysis.diffStartSha,
            "diffHeadSha" to analysis.diffHeadSha,
            "integrationTestEnabled" to analysis.integrationTestEnabled,
            "integrationTestStatus" to analysis.integrationTestStatus,
            "integrationTestResult" to analysis.integrationTestResult,
        )

    fun toListItem(analysis: CodeQaAnalysis): Map<String, Any?> =
        mapOf(
            "id" to analysis.id,
            "mrUrl" to analysis.mrUrl,
            "jiraTicketKey" to analysis.jiraTicketKey,
            "status" to analysis.status,
            "createdAt" to analysis.createdAt.toString(),
            "completedAt" to analysis.completedAt?.toString(),
        )
}
