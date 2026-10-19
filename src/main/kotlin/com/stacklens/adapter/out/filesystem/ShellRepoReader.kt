package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.repo.RepoDockerfile
import com.stacklens.application.port.out.repo.RepoFileRead
import com.stacklens.application.port.out.repo.RepoReader
import com.stacklens.application.port.out.repo.ShellCommandRunner
import com.stacklens.application.port.out.repo.UNBOUNDED_REPO_DEPTH
import java.io.File

internal class ShellRepoReader(
    private val runCommand: ShellCommandRunner,
) : RepoReader {
    override fun readText(
        root: File,
        path: String,
        maxLen: Int,
    ): RepoFileRead {
        val target = "${root.path}/$path"
        val raw = runCommand("cat", target)
        if (raw != null) return RepoFileRead.Available(raw.take(maxLen))
        return if (existsViaShell(target)) RepoFileRead.Unreadable else RepoFileRead.Missing
    }

    override fun listTopLevel(
        root: File,
        limit: Int,
    ): List<String> {
        val out = runCommand("ls", "-1", root.path) ?: return emptyList()
        return out
            .lines()
            .filter { it.isNotBlank() }
            .take(limit)
    }

    override fun listDirectories(
        root: File,
        maxDepth: Int,
        limit: Int,
    ): List<String> {
        val out =
            runCommand(
                "sh",
                "-c",
                "find '${root.path}'${depthClause(maxDepth)} -type d 2>/dev/null | head -$limit",
            ) ?: return emptyList()
        return out.lines().filter { it.isNotBlank() }
    }

    override fun hasDirectory(
        root: File,
        relativePath: String,
    ): Boolean =
        runCommand(
            "sh",
            "-c",
            "if [ -d '${root.path}/$relativePath' ]; then echo present; fi",
        ) != null

    override fun findFiles(
        root: File,
        extension: String,
        skipDirectoryNamed: String,
        limit: Int,
    ): List<String> {
        val out =
            runCommand(
                "sh",
                "-c",
                "find '${root.path}' -type f -name '*.$extension' -not -path '*/$skipDirectoryNamed/*' 2>/dev/null | head -$limit",
            ) ?: return emptyList()
        return out.lines().filter { it.isNotBlank() }
    }

    override fun readFirstDockerfile(
        root: File,
        maxDepth: Int,
        maxLen: Int,
    ): RepoDockerfile? {
        val found =
            runCommand(
                "sh",
                "-c",
                "find '${root.path}'${depthClause(maxDepth)} -name 'Dockerfile*' 2>/dev/null | head -1",
            )?.lines()?.filter { it.isNotBlank() }?.firstOrNull() ?: return null
        return RepoDockerfile(
            relativePath = found.removePrefix(root.path).trimStart('/'),
            content = readText(root, found.removePrefix(root.path).trimStart('/'), maxLen),
        )
    }

    private fun depthClause(maxDepth: Int): String = if (maxDepth == UNBOUNDED_REPO_DEPTH) "" else " -maxdepth $maxDepth"

    private fun existsViaShell(target: String): Boolean =
        runCommand(
            "sh",
            "-c",
            "if [ -e '$target' ]; then echo present; fi",
        ) != null
}
