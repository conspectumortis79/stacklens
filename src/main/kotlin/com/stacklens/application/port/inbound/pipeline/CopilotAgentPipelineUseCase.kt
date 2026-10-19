package com.stacklens.application.port.inbound.pipeline

interface CopilotAgentPipelineUseCase :
    CopilotAgentPipelineReadUseCase,
    CopilotAgentPipelineWriteUseCase {
    fun previewWriteFiles(id: Long): PipelineWritePreview?

    fun writeFiles(
        id: Long,
        nativeWriter: Boolean?,
    ): PipelineWriteOutcome?
}

data class PipelineWritePreview(
    val repoPath: String?,
    val files: List<PipelineWriteFile>,
    val errorKey: String? = null,
)

data class PipelineWriteFile(
    val path: String,
    val size: Int,
)

data class PipelineWriteError(
    val path: String? = null,
    val code: PipelineWriteErrorCode? = null,
    val detail: String? = null,
    val message: String? = null,
)

enum class PipelineWriteErrorCode {
    PATH_TRAVERSAL,
    DIR_CREATE_FAILED,
    WRITE_FAILED,
    SECURITY_DENIED,
}

data class PipelineWriteOutcome(
    val written: List<String> = emptyList(),
    val deleted: List<String>? = null,
    val errors: List<PipelineWriteError> = emptyList(),
    val infoKey: String? = null,
    val errorKey: String? = null,
    val errorArg: String? = null,
    val showWritePermissionHint: Boolean = false,
)
