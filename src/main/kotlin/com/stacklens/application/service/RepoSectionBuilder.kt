package com.stacklens.application.service

import com.stacklens.application.port.out.repo.RepoReader
import com.stacklens.application.port.out.repo.UNBOUNDED_REPO_DEPTH
import java.io.File

internal class RepoSectionBuilder(
    private val reader: RepoReader,
) {
    private val sections: List<(StringBuilder, File) -> Unit> =
        listOf(
            ::appendTopLevel,
            ::appendComposerJson,
            ::appendDockerCompose,
            ::appendDockerfiles,
            ::appendSourceTree,
            ::appendSamplePhpFiles,
            ::appendMakefileTargets,
        )

    fun build(
        root: File,
        repoPath: String,
        shellFallbackBanner: String? = null,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Repository: $repoPath")
        if (shellFallbackBanner != null) {
            sb.appendLine(shellFallbackBanner)
        }
        sb.appendLine()

        sections.forEach { it(sb, root) }
        return sb.toString().take(20000)
    }

    private fun appendTopLevel(
        sb: StringBuilder,
        root: File,
    ) {
        sb.appendLine("## Directory structure (top-level)")
        reader
            .listTopLevel(root, Int.MAX_VALUE)
            .forEach { name -> sb.appendLine("  $name") }
        sb.appendLine()
    }

    private fun appendComposerJson(
        sb: StringBuilder,
        root: File,
    ) {
        val content = reader.readText(root, "composer.json", 3000).renderedOrNull() ?: return
        sb.appendLine("## composer.json")
        sb.appendLine(content)
        sb.appendLine()
    }

    private fun appendDockerCompose(
        sb: StringBuilder,
        root: File,
    ) {
        val candidates = listOf("docker-compose.yml", "docker-compose.yaml", "docker/docker-compose.yml")
        for (candidate in candidates) {
            val content = reader.readText(root, candidate, 2000).renderedOrNull() ?: continue
            val headerName = candidate.substringAfterLast('/')
            sb.appendLine("## $headerName")
            sb.appendLine(content)
            sb.appendLine()
            return
        }
    }

    private fun appendDockerfiles(
        sb: StringBuilder,
        root: File,
    ) {
        val found = reader.readFirstDockerfile(root, UNBOUNDED_REPO_DEPTH, 1000) ?: return
        val dockerfileContent = found.content.renderedOrNull() ?: return
        sb.appendLine("## ${found.relativePath}")
        sb.appendLine(dockerfileContent)
        sb.appendLine()
    }

    private fun appendSourceTree(
        sb: StringBuilder,
        root: File,
    ) {
        val candidates = listOf("src", "app", "src/main/php")
        val srcDir =
            candidates
                .firstOrNull { reader.hasDirectory(root, it) } ?: return
        sb.appendLine("## Source tree (${srcDir.substringAfterLast('/')}/)")
        reader
            .listDirectories(root, UNBOUNDED_REPO_DEPTH, 50)
            .filter { it.startsWith(File(root, srcDir).path) }
            .forEach { d -> sb.appendLine("  $d/") }
        sb.appendLine()
    }

    private fun appendSamplePhpFiles(
        sb: StringBuilder,
        root: File,
    ) {
        val phpFiles = reader.findFiles(root, extension = "php", skipDirectoryNamed = "vendor", limit = 6)
        if (phpFiles.isEmpty()) return
        sb.appendLine("## Sample PHP files")
        phpFiles.forEach { f ->
            sb.appendLine("### $f")
            val rel = f.removePrefix(root.path).trimStart('/')
            val content = reader.readText(root, rel, 1500).renderedOrNull()
            if (content != null) sb.appendLine(content)
            sb.appendLine()
        }
    }

    private fun appendMakefileTargets(
        sb: StringBuilder,
        root: File,
    ) {
        val content = reader.readText(root, "Makefile", 1000).renderedOrNull() ?: return
        sb.appendLine("## Makefile targets")
        sb.appendLine(content)
        sb.appendLine()
    }
}
