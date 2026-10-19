package com.stacklens.application.port.inbound.workspace

interface WorkspaceFileUseCase {
    fun saveText(request: TextFileSaveRequest): WorkspaceWriteResult

    fun probeAccess(path: String): WorkspaceAccessView

    fun homeDirectory(): String

    fun readBytes(path: String): ByteArray?

    fun readText(path: String): String?

    fun fileInfo(path: String): WorkspaceFileInfo?

    fun browseDirectory(path: String?): WorkspaceDirectoryListing?

    fun copyFile(request: WorkspaceFileCopyRequest): WorkspaceWriteResult
}

data class TextFileSaveRequest(
    val directory: String,
    val filename: String,
    val content: String,
    val allowedRoot: String? = null,
    val requireExistingFile: Boolean = false,
    val allowedExtensions: Set<String> = emptySet(),
    val fallbackFilename: String? = null,
    val enforceJsonExtension: Boolean = false,
    val canonicalizeDirectory: Boolean = false,
)

sealed interface WorkspaceWriteResult {
    data class Saved(
        val path: String,
    ) : WorkspaceWriteResult

    data class Rejected(
        val reason: WorkspaceWriteRejection,
    ) : WorkspaceWriteResult
}

enum class WorkspaceWriteRejection {
    DIRECTORY_NOT_FOUND,
    PATH_OUTSIDE_ALLOWED_ROOT,
    TARGET_NOT_A_FILE,
    EXTENSION_NOT_EDITABLE,
    WRITE_FAILED,
}

data class WorkspaceAccessView(
    val path: String,
    val exists: Boolean,
    val isDirectory: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean,
    val probeOk: Boolean,
    val probeError: String?,
)

data class WorkspaceFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val isFile: Boolean,
)

data class WorkspaceDirectoryEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val size: Long,
)

data class WorkspaceDirectoryListing(
    val path: String,
    val parentPath: String?,
    val entries: List<WorkspaceDirectoryEntry>,
)

data class WorkspaceFileCopyRequest(
    val sourcePath: String,
    val targetDirectory: String,
    val targetFilename: String,
)
