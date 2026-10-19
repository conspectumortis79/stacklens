package com.stacklens.adapter.out.ai

import org.slf4j.Logger
import java.io.File

object RepoFileMarkdownExtractor {
    fun extract(
        response: String,
        repoDir: File,
        log: Logger,
    ): Int {
        var count = 0

        val fileBlockRegex =
            Regex(
                """(?m)^#{2,3}\s+([^\s#🗑️][^\n]*\.[a-zA-Z0-9]{1,10})\s*\n```[a-zA-Z0-9_\-]*\n([\s\S]*?)```""",
            )
        for (match in fileBlockRegex.findAll(response)) {
            val rawPath = match.groupValues[1].trim()
            val content = match.groupValues[2]
            if (rawPath.startsWith("✅") || rawPath.startsWith("⚠")) continue
            if (rawPath.contains(' ') && !rawPath.contains('/') && !rawPath.contains('\\')) continue

            val file =
                RepoFileToolExecutor.resolveFilePath(repoDir, rawPath) ?: run {
                    log.warn("[RepoFileTools] markdown-extract: skipped '{}' (path traversal)", rawPath)
                    continue
                }
            try {
                file.parentFile!!.mkdirs()
                file.writeText(content)
                log.info("[RepoFileTools] markdown-extract: wrote {} ({} chars)", rawPath, content.length)
                count++
            } catch (e: Exception) {
                log.warn("[RepoFileTools] markdown-extract: failed to write {}: {}", rawPath, e.message)
            }
        }

        val deletionSectionRegex =
            Regex(
                """(?mi)^#{2,3}\s*.*[Dd]elet.*\n```[^\n]*\n([\s\S]*?)```""",
            )
        val deletionMatches = deletionSectionRegex.findAll(response).toList()
        for (sectionMatch in deletionMatches) {
            val block = sectionMatch.groupValues[1]
            for (line in block.lines()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("DELETED ")) continue

                val rawPath = trimmed.substring("DELETED ".length)
                val file =
                    RepoFileToolExecutor.resolveFilePath(repoDir, rawPath) ?: run {
                        log.warn("[RepoFileTools] markdown-extract: skipped deletion '{}' (path traversal)", rawPath)
                        continue
                    }
                if (!file.exists()) continue
                if (file.delete()) {
                    log.info("[RepoFileTools] markdown-extract: deleted {}", rawPath)
                    count++
                } else {
                    log.warn("[RepoFileTools] markdown-extract: could not delete {}", rawPath)
                }
            }
        }

        return count
    }
}
