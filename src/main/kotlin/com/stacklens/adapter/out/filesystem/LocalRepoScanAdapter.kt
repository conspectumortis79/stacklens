package com.stacklens.adapter.out.filesystem

import com.stacklens.adapter.out.shared.RepoScanRules
import com.stacklens.application.port.out.repo.CodeSummary
import com.stacklens.application.port.out.repo.KeywordHit
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.port.out.repo.PipelineSummary
import com.stacklens.domain.util.closingAuto
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset

class LocalRepoScanAdapter(
    private val rules: RepoScanRules = RepoScanRules(),
) : LocalRepoScanPort {
    private val log = LoggerFactory.getLogger(LocalRepoScanAdapter::class.java)

    override fun buildCodeSummary(
        repoPath: String,
        maxFiles: Int,
        maxPerFileSizeKb: Int,
    ): CodeSummary {
        val root = resolveRoot(repoPath)

        val allFiles = mutableListOf<File>()
        walkDirectory(root, root, allFiles)

        val prioritized =
            allFiles
                .sortedWith(compareByDescending<File> { rules.isPriorityFile(it.name) }.thenBy { it.length() })
                .take(maxFiles)

        val tree = buildTree(root, allFiles)

        val contents =
            buildString {
                for (file in prioritized) {
                    val relPath = file.relativeTo(root).path
                    val content = safeReadText(file, maxPerFileSizeKb * 1024)
                    appendLine("=== $relPath ===")
                    appendLine(content)
                    appendLine()
                }
            }

        log.info("[LocalRepoScan] {} – {} prioritised files, {} chars", root.name, prioritized.size, contents.length)
        return CodeSummary(tree, contents, prioritized.size, contents.length)
    }

    override fun buildPipelineSummary(
        repoPath: String,
        maxFiles: Int,
        maxPerFileSizeKb: Int,
    ): PipelineSummary {
        val root = resolveRoot(repoPath)

        val allFiles = mutableListOf<File>()
        walkDirectoryForPipeline(root, root, allFiles)

        val prioritized =
            allFiles
                .sortedWith(compareByDescending<File> { rules.isPriorityPipelineFile(it) }.thenBy { it.length() })
                .take(maxFiles)

        if (prioritized.isEmpty()) {
            log.info("[LocalRepoScan] {} – no pipeline / compose files found", root.name)
            return PipelineSummary(projectTree = "", fileContents = "", fileCount = 0, charCount = 0)
        }

        val tree = buildTree(root, prioritized)

        val contents =
            buildString {
                for (file in prioritized) {
                    val relPath = file.relativeTo(root).path
                    val content = safeReadText(file, maxPerFileSizeKb * 1024)
                    appendLine("=== $relPath ===")
                    appendLine(content)
                    appendLine()
                }
            }

        log.info("[LocalRepoScan] {} – {} pipeline files, {} chars", root.name, prioritized.size, contents.length)
        return PipelineSummary(tree, contents, prioritized.size, contents.length)
    }

    private fun walkDirectoryForPipeline(
        root: File,
        dir: File,
        result: MutableList<File>,
    ) {
        listFilesOrEmpty(dir).forEach { file ->
            when {
                file.isDirectory && (file.name !in rules.skipDirs || rules.isPipelineDirectory(file.name)) ->
                    walkDirectoryForPipeline(root, file, result)
                file.isFile && file.length() < 1_000_000L -> {
                    val rel =
                        file
                            .relativeTo(root)
                            .path
                            .replace('\\', '/')
                            .lowercase()
                    val inWorkflows = rel.contains(".github/workflows/")
                    val inCircleci =
                        rel.contains(".circleci/") &&
                            (rel.endsWith("/config.yml") || rel.endsWith("/config.yaml"))
                    if (rules.isPipelineFile(file.name) || inWorkflows || inCircleci) {
                        result += file
                    }
                }
            }
        }
    }

    internal fun listFilesOrEmpty(dir: File): Array<File> = dir.listFiles() ?: emptyArray()

    override fun searchKeyword(
        repoPath: String,
        keyword: String,
        maxHits: Int,
    ): List<KeywordHit> {
        val root = resolveRoot(repoPath)

        val allFiles = mutableListOf<File>()
        walkDirectory(root, root, allFiles)

        val hits = mutableListOf<KeywordHit>()
        for (file in allFiles) {
            if (hits.size >= maxHits) break
            val relPath = file.relativeTo(root).path
            try {
                val lines = file.useLines { it.toList() }
                lines.forEachIndexed { idx, line ->
                    if (hits.size < maxHits && line.contains(keyword, ignoreCase = true)) {
                        val start = maxOf(0, idx - 3)
                        val end = minOf(lines.size - 1, idx + 3)
                        val context = lines.subList(start, end + 1).joinToString("\n")
                        hits += KeywordHit(relPath, idx + 1, context)
                    }
                }
            } catch (_: Exception) {
            }
        }

        log.info("[LocalRepoScan] keyword='{}' → {} hits in {}", keyword, hits.size, root.name)
        return hits
    }

    private fun resolveRoot(path: String): File {
        val f = File(path)
        require(f.exists() && f.isDirectory) { "Not a valid directory: $path" }
        return f.canonicalFile
    }

    private fun walkDirectory(
        root: File,
        dir: File,
        result: MutableList<File>,
    ) {
        listFilesOrEmpty(dir).forEach { file ->
            when {
                file.isDirectory && file.name !in rules.skipDirs && !file.name.startsWith(".") ->
                    walkDirectory(root, file, result)
                file.isFile && rules.isCodeFile(file.name) && file.length() < 500_000L ->
                    result += file
            }
        }
    }

    private fun buildTree(
        root: File,
        files: List<File>,
    ): String =
        files
            .map { it.relativeTo(root).path }
            .sorted()
            .take(150)
            .joinToString("\n") { "  $it" }

    private fun safeReadText(
        file: File,
        maxBytes: Int,
    ): String =
        try {
            val capped = maxBytes.coerceAtLeast(0)
            val out = ByteArray(capped)
            val read =
                file.inputStream().closingAuto { input ->
                    var total = 0
                    while (total < capped) {
                        val n = input.read(out, total, capped - total)
                        if (n <= 0) break
                        total += n
                    }
                    total
                }
            val text = String(out, 0, read, Charset.forName("UTF-8"))
            if (file.length() > capped) "$text\n… [truncated]" else text
        } catch (_: Exception) {
            "[binary or unreadable]"
        }
}
