package com.stacklens.application.port.inbound.logging

import com.stacklens.domain.model.LoggingFixAnalysis

data class LoggingFixStartRequest(
    val logText: String,
    val selectedCategories: List<String> = emptyList(),
    val localRepoConfigId: Long? = null,
    val language: String = "de",
)

interface LoggingFixLifecycleUseCase {
    fun startFix(
        logText: String,
        selectedCategories: List<String>,
        localRepoConfigId: Long?,
        language: String,
    ): Long

    fun getAnalysis(id: Long): LoggingFixAnalysis?

    fun listRecent(limit: Int = 10): List<LoggingFixAnalysis>

    fun cancel(id: Long): CancelOutcome

    fun delete(id: Long): DeleteOutcome

    fun deleteAllFinished(): Int
}

interface LoggingFixStageOutputUseCase {
    fun getStageOutputs(id: Long): StageOutputs?
}

interface LoggingFixFileWriteUseCase {
    fun getWriteFilesPreview(id: Long): WriteFilesPreview?

    fun writeFiles(id: Long): WriteFilesResult
}

interface LoggingFixValidationUseCase {
    fun validateStartRequest(request: LoggingFixStartRequest): List<String>
}

interface LoggingFixUseCase :
    LoggingFixLifecycleUseCase,
    LoggingFixStageOutputUseCase,
    LoggingFixFileWriteUseCase,
    LoggingFixValidationUseCase

sealed class CancelOutcome {
    data object Cancelled : CancelOutcome()

    data object NotFound : CancelOutcome()

    data object AlreadyFinished : CancelOutcome()
}

sealed class DeleteOutcome {
    data object Deleted : DeleteOutcome()

    data object NotFound : DeleteOutcome()

    data object StillRunning : DeleteOutcome()
}

data class StageOutputs(
    val id: Long,
    val status: String,
    val currentStage: String,
    val stages: Map<String, StageState>,
    val errorMessage: String?,
    val repoPath: String?,
)

data class StageState(
    val name: String,
    val label: String,
    val status: String,
    val output: String,
)

data class WriteFilesPreview(
    val files: List<WriteFileEntry>,
    val repoPath: String?,
)

data class WriteFileEntry(
    val path: String,
    val content: String,
    val description: String?,
)

data class WriteFilesResult(
    val success: Boolean,
    val error: String? = null,
    val writtenFiles: List<String> = emptyList(),
)
