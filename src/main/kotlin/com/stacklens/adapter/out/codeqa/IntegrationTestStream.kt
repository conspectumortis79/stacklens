package com.stacklens.adapter.out.codeqa

import com.stacklens.domain.model.CodeQaAnalysis

data class IntegrationTestStreamPlan(
    val services: List<String>,
    val testCommands: List<String>,
    val projectTypes: List<String>,
    val testStrategy: String,
    val testTargets: List<String>,
    val reasoning: String,
    val waitSeconds: Int,
)

interface IntegrationTestStream {
    fun header(): String

    fun environmentBanner(): String

    fun emit(
        sb: StringBuilder,
        text: String,
    )

    fun emitStarted(
        sb: StringBuilder,
        branch: String,
        targetBranch: String,
        changedFiles: Int,
        tmpDir: String,
        os: String,
    )

    fun emitStep(
        sb: StringBuilder,
        index: Int,
        name: String,
        state: String,
        detail: String?,
    )

    fun emitPlan(
        sb: StringBuilder,
        plan: IntegrationTestStreamPlan,
    )

    fun emitWaiting(
        sb: StringBuilder,
        seconds: Int,
        services: List<String>,
    )

    fun emitService(
        sb: StringBuilder,
        name: String,
        state: String,
    )

    fun emitTestsRunning(
        sb: StringBuilder,
        commands: List<String>,
    )

    fun emitTestRunning(
        sb: StringBuilder,
        index: Int,
        command: String,
    )

    fun emitTestFinished(
        sb: StringBuilder,
        index: Int,
        command: String,
        exitCode: Int,
    )

    fun emitTestsSummary(
        sb: StringBuilder,
        passed: Int,
        total: Int,
        results: List<Pair<String, Boolean>>,
    )

    fun emitError(
        sb: StringBuilder,
        message: String,
    )

    fun appendTestLog(
        analysis: CodeQaAnalysis,
        message: String,
    )
}
