package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.repo.RepoDockerfile
import com.stacklens.application.port.out.repo.RepoFileRead
import com.stacklens.application.port.out.repo.RepoReader
import java.io.File

internal class DirectRepoReader : RepoReader {
    override fun readText(
        root: File,
        path: String,
        maxLen: Int,
    ): RepoFileRead {
        val f = File(root, path)
        if (!f.exists()) return RepoFileRead.Missing
        return try {
            RepoFileRead.Available(f.readText().take(maxLen))
        } catch (_: Exception) {
            RepoFileRead.Unreadable
        }
    }

    override fun listTopLevel(
        root: File,
        limit: Int,
    ): List<String> {
        val entries = root.listFiles() ?: return emptyList()
        return entries.sortedBy { it.name }.take(limit).map { it.name }
    }

    override fun listDirectories(
        root: File,
        maxDepth: Int,
        limit: Int,
    ): List<String> =
        root
            .walkTopDown()
            .maxDepth(maxDepth)
            .filter { it.isDirectory }
            .take(limit)
            .map { it.path }
            .toList()

    override fun hasDirectory(
        root: File,
        relativePath: String,
    ): Boolean = File(root, relativePath).isDirectory

    override fun findFiles(
        root: File,
        extension: String,
        skipDirectoryNamed: String,
        limit: Int,
    ): List<String> =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == extension }
            .filterNot { file ->
                file
                    .relativeTo(root)
                    .path
                    .split(File.separator)
                    .any { it == skipDirectoryNamed }
            }.take(limit)
            .map { it.path }
            .toList()

    override fun readFirstDockerfile(
        root: File,
        maxDepth: Int,
        maxLen: Int,
    ): RepoDockerfile? {
        val found =
            root
                .walkTopDown()
                .maxDepth(maxDepth)
                .filter { it.isFile && it.name.startsWith("Dockerfile") }
                .take(1)
                .firstOrNull() ?: return null
        return RepoDockerfile(
            relativePath = found.relativeTo(root).path,
            content = readText(root, found.relativeTo(root).path, maxLen),
        )
    }
}
