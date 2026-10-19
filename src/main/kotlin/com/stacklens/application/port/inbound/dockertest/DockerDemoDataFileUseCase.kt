package com.stacklens.application.port.inbound.dockertest

import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection

interface DockerDemoDataFileUseCase {
    fun listDataFiles(composeFile: String?): List<DockerDemoDataFileEntry>

    fun readDataFile(
        composeFile: String?,
        path: String,
    ): DockerDemoDataReadResult

    fun writeDataFile(
        composeFile: String?,
        path: String,
        content: String,
    ): DockerDemoDataWriteResult
}

data class DockerDemoDataFileEntry(
    val path: String,
    val label: String,
)

sealed interface DockerDemoDataReadResult {
    data class Read(
        val content: String,
    ) : DockerDemoDataReadResult

    data object ComposeNotFound : DockerDemoDataReadResult

    data object PathOutsideBase : DockerDemoDataReadResult

    data object NotAFile : DockerDemoDataReadResult

    data object ExtensionNotEditable : DockerDemoDataReadResult
}

sealed interface DockerDemoDataWriteResult {
    data class Saved(
        val path: String,
    ) : DockerDemoDataWriteResult

    data object ComposeNotFound : DockerDemoDataWriteResult

    data class Rejected(
        val reason: WorkspaceWriteRejection,
    ) : DockerDemoDataWriteResult
}
