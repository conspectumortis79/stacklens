package com.stacklens.application.port.out.repo

import java.io.File

const val UNBOUNDED_REPO_DEPTH: Int = Int.MAX_VALUE

sealed interface RepoFileRead {
    data class Available(
        val text: String,
    ) : RepoFileRead

    data object Unreadable : RepoFileRead

    data object Missing : RepoFileRead

    fun renderedOrNull(): String? =
        when (this) {
            is Available -> text
            Unreadable -> RepoFileRead.UNREADABLE_MARKER
            Missing -> null
        }

    companion object {
        const val UNREADABLE_MARKER: String = "(unreadable)"
    }
}

data class RepoDockerfile(
    val relativePath: String,
    val content: RepoFileRead,
)

interface RepoTextReader {
    fun readText(
        root: File,
        path: String,
        maxLen: Int,
    ): RepoFileRead
}

interface RepoListing {
    fun listTopLevel(
        root: File,
        limit: Int,
    ): List<String>

    fun listDirectories(
        root: File,
        maxDepth: Int,
        limit: Int,
    ): List<String>

    fun hasDirectory(
        root: File,
        relativePath: String,
    ): Boolean

    fun findFiles(
        root: File,
        extension: String,
        skipDirectoryNamed: String,
        limit: Int,
    ): List<String>
}

interface RepoDockerfileReader {
    fun readFirstDockerfile(
        root: File,
        maxDepth: Int,
        maxLen: Int,
    ): RepoDockerfile?
}

interface RepoReader :
    RepoTextReader,
    RepoListing,
    RepoDockerfileReader
