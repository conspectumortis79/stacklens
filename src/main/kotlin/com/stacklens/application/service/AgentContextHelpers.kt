package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.MockupImage
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import com.stacklens.application.port.out.process.GitCommandPort
import java.io.File

internal object AgentContextHelpers {
    sealed class GitDiffResult {
        data class Success(
            val paths: List<String>,
        ) : GitDiffResult()

        data class Failure(
            val message: String,
        ) : GitDiffResult()
    }

    fun buildMockupSection(mockupImages: List<MockupImage>): String {
        val validImages = mockupImages.filter { it.base64.isNotBlank() && it.mimeType.isNotBlank() }
        if (validImages.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("## Frontend Mockup (Faye visual reference)")
            appendLine()
            if (validImages.size == 1) {
                appendLine("A frontend mockup has been provided by the developer as a visual reference.")
                appendLine("Use it to guide the styling, layout, and component structure of all frontend files.")
            } else {
                appendLine("${validImages.size} frontend mockup images have been provided by the developer as visual references.")
                appendLine("Use them to guide the styling, layout, and component structure of all frontend files.")
            }
            appendLine()
            validImages.forEachIndexed { idx, img ->
                val label = if (validImages.size == 1) "" else " ${idx + 1}"
                if (img.mimeType == "image/svg+xml") {
                    appendLine("### Mockup$label (SVG source)")
                    appendLine("```svg")
                    val svgText =
                        String(
                            java.util.Base64
                                .getDecoder()
                                .decode(img.base64),
                            Charsets.UTF_8,
                        )
                    appendLine(svgText.take(100_000))
                    append("```")
                } else {
                    appendLine("### Mockup$label (base64 image, type: ${img.mimeType})")
                    append("data:${img.mimeType};base64,${img.base64}")
                }
                appendLine()
            }
        }
    }

    fun buildMockupSection(
        mockupBase64: String?,
        mockupMimeType: String?,
    ): String =
        buildMockupSection(
            listOfNotNull(
                mockupBase64
                    ?.takeIf { it.isNotBlank() }
                    ?.let { b64 -> MockupImage(b64, mockupMimeType ?: "") },
            ),
        )

    fun readFilesAsContext(
        paths: List<String>,
        repoRoot: File,
        fileAttributes: FileAttributes,
        fileContentStore: FileContentStore,
    ): String {
        val sb = StringBuilder()
        val canonicalRoot =
            runCatching { fileAttributes.canonicalFile(repoRoot) }.getOrElse { repoRoot }
        for (path in paths.take(20)) {
            val f =
                try {
                    fileAttributes.canonicalFile(canonicalRoot.resolve(path))
                } catch (_: Exception) {
                    continue
                }
            if (!f.path.startsWith(canonicalRoot.path + File.separator)) continue
            if (!fileAttributes.exists(f) || !fileAttributes.isFile(f)) continue
            val ext = f.extension.ifBlank { "txt" }
            sb.appendLine("### $path")
            sb.appendLine("```$ext")
            sb.appendLine(fileContentStore.readText(f).take(4000))
            sb.appendLine("```")
            sb.appendLine()
        }
        return sb.toString().take(30_000)
    }

    fun extractSniffAffectedPaths(
        sniffOutput: String,
        repoRoot: File,
        fileAttributes: FileAttributes,
    ): List<String> {
        val canonicalRoot =
            runCatching { fileAttributes.canonicalFile(repoRoot) }.getOrElse { repoRoot }
        return sniffOutput
            .lines()
            .filter { it.startsWith("FILE: ") }
            .mapNotNull { line ->
                val abs = line.removePrefix("FILE: ").trim().removeSuffix(" (source: standard)")
                try {
                    val canonicalFile = fileAttributes.canonicalFile(File(abs))
                    if (!canonicalFile.path.startsWith(canonicalRoot.path + File.separator)) {
                        null
                    } else {
                        canonicalFile.relativeTo(canonicalRoot).path
                    }
                } catch (_: Exception) {
                    null
                }
            }.distinct()
    }

    fun gitDiffPaths(
        baseSha: String?,
        repoRoot: File,
        processPort: GitCommandPort,
    ): GitDiffResult =
        try {
            val gitBin = processPort.resolveGitBinary()
            val sha = baseSha?.takeIf { it.length == 40 }
            val args =
                if (sha != null) {
                    listOf(gitBin, "diff", "--name-only", sha, "HEAD")
                } else {
                    listOf(gitBin, "diff", "--name-only", "HEAD")
                }
            val result = processPort.run(args, timeoutSec = 10L, workDir = repoRoot)
            val paths =
                result.stdout
                    .lines()
                    .filter { line ->
                        line.isNotBlank() &&
                            (
                                line.endsWith(".php") ||
                                    line.endsWith(".yaml") ||
                                    line.endsWith(".yml") ||
                                    line.endsWith(".xml") ||
                                    line.endsWith(".json")
                            )
                    }.take(15)
            GitDiffResult.Success(paths)
        } catch (e: Exception) {
            GitDiffResult.Failure(e.message ?: e.javaClass.simpleName)
        }
}
