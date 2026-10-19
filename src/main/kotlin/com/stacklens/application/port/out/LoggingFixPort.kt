package com.stacklens.application.port.out

import com.stacklens.domain.model.LoggingFixAnalysis
import java.time.LocalDateTime

interface LoggingFixPort {
    fun saveLoggingFixAnalysis(analysis: LoggingFixAnalysis): LoggingFixAnalysis

    fun findLoggingFixAnalysisById(id: Long): LoggingFixAnalysis?

    fun findRecentLoggingFixAnalyses(limit: Int): List<LoggingFixAnalysis>

    fun findAllByOrderByCreatedAtDesc(): List<LoggingFixAnalysis>

    fun deleteLoggingFixAnalysisById(id: Long)

    fun deleteAllLoggingFixAnalyses()

    fun deleteFinishedLoggingFixAnalyses(): Int
}

enum class LoggingFixStageColumn {
    DIAGNOSE,
    PLAN,
    CODE,
    TESTS,
}

interface LoggingFixPartialUpdatePort {
    fun updateStageDiagnose(
        id: Long,
        value: String?,
    ): Int

    fun updateStagePlan(
        id: Long,
        value: String?,
    ): Int

    fun updateStageCode(
        id: Long,
        value: String?,
    ): Int

    fun updateStageTests(
        id: Long,
        value: String?,
    ): Int

    fun appendStageOutput(
        id: Long,
        column: LoggingFixStageColumn,
        chunk: String,
    ): Int

    fun updateStatus(
        id: Long,
        status: String,
    ): Int

    fun updateStatusWithErrorMessage(
        id: Long,
        status: String,
        errorMessage: String?,
        completedAt: LocalDateTime,
    ): Int

    fun updateStatusAndCompletedAt(
        id: Long,
        status: String,
        completedAt: LocalDateTime,
    ): Int
}
