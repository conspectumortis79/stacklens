package com.stacklens.adapter.out.ai

import com.stacklens.domain.util.quietly
import org.slf4j.Logger
import java.io.File

object RepoFileToolExecutor {
    private val LIST_EXCLUDES: Set<String> =
        setOf("vendor", "node_modules", ".git", ".idea", ".gradle", "build", "target", ".stacklens", ".DS_Store")

    private fun File.ensureParentDir() {
        parentFile?.mkdirs()
    }

    private fun runOrError(
        label: String,
        path: String,
        block: () -> String,
    ): String = runCatching(block).getOrElse { "Error $label $path: ${it.message}" }

    fun execute(
        toolName: String,
        args: Map<*, *>,
        repoDir: File,
        log: Logger,
    ): String =
        when (toolName) {
            "list", "ls", "list_directory", "list_dir" -> {
                val rawPath = (args["path"] as? String)?.takeIf { it.isNotBlank() } ?: "."
                val recursive = args["recursive"] as? Boolean ?: false
                val dir = resolveFilePath(repoDir, rawPath) ?: return "Error: path traversal not allowed: $rawPath"
                if (!dir.exists()) {
                    return "Error: directory not found: $rawPath. Use list with path=\"\" first to see what exists."
                }
                if (!dir.isDirectory) return "Error: not a directory: $rawPath"
                runOrError("listing", rawPath) {
                    val rootPath = repoDir.canonicalFile.toPath()
                    val entries =
                        if (recursive) {
                            dir
                                .walkTopDown()
                                .onEnter { it.name !in LIST_EXCLUDES }
                                .filter { it != dir && it.name !in LIST_EXCLUDES }
                                .map { it.canonicalFile.toPath() }
                                .map {
                                    rootPath.relativize(it).toString().replace(File.separatorChar, '/') +
                                        if (it.toFile().isDirectory) "/" else ""
                                }.sorted()
                                .toList()
                        } else {
                            (dir.listFiles() ?: emptyArray())
                                .filter { it.name !in LIST_EXCLUDES }
                                .map { it.canonicalFile.toPath() }
                                .map {
                                    rootPath.relativize(it).toString().replace(File.separatorChar, '/') +
                                        if (it.toFile().isDirectory) "/" else ""
                                }.sorted()
                        }
                    log.info("[RepoFileTools] list {} (recursive={}) → {} entries", rawPath, recursive, entries.size)
                    if (entries.isEmpty()) "(empty directory)" else entries.joinToString("\n")
                }
            }

            "read" -> {
                val path = args["path"] as? String ?: return "Error: 'path' argument required"
                val file = resolveFilePath(repoDir, path) ?: return "Error: path traversal not allowed: $path"
                if (!file.exists()) return "Error: file not found: $path"
                runOrError("reading file", path) {
                    val content = file.readText()
                    log.info("[RepoFileTools] read {} ({} chars)", path, content.length)
                    content
                }
            }

            "write" -> {
                val path = args["path"] as? String ?: return "Error: 'path' argument required"
                val content = args["content"] as? String ?: return "Error: 'content' argument required"
                val file = resolveFilePath(repoDir, path) ?: return "Error: path traversal not allowed: $path"
                runOrError("writing", path) {
                    file.ensureParentDir()
                    file.writeText(content)
                    log.info("[RepoFileTools] wrote {} ({} chars)", path, content.length)
                    "OK: written ${content.length} chars to $path"
                }
            }

            "delete" -> {
                val path = args["path"] as? String ?: return "Error: 'path' argument required"
                val file = resolveFilePath(repoDir, path) ?: return "Error: path traversal not allowed: $path"
                if (!file.exists()) {
                    return "Error: file not found: $path. Use the `list` tool (recursive=true) on the parent " +
                        "directory to discover the actual paths before retrying."
                }
                runOrError("deleting", path) {
                    val parent = file.parentFile
                    val deletedCount =
                        if (file.isDirectory) {
                            var n = 0
                            file.walkBottomUp().forEach { if (it.delete()) n++ }
                            n
                        } else {
                            if (file.delete()) 1 else 0
                        }
                    val prunedDirs = pruneEmptyParents(parent, repoDir, log)
                    log.info(
                        "[RepoFileTools] deleted {} ({} entries, {} empty parent dir(s) pruned)",
                        path,
                        deletedCount,
                        prunedDirs,
                    )
                    val suffix = if (prunedDirs > 0) " + $prunedDirs empty parent dir(s) removed" else ""
                    "OK: deleted $path ($deletedCount entries$suffix)"
                }
            }

            else -> "Error: unknown tool '$toolName'"
        }

    fun resolveFilePath(
        repoDir: File,
        path: String,
    ): File? {
        val sanitised = path.trimStart('/', '\\')
        val resolved = repoDir.resolve(sanitised).canonicalFile
        val root = repoDir.canonicalFile
        return if (resolved.path.startsWith(root.path + File.separator) || resolved.path == root.path) {
            resolved
        } else {
            null
        }
    }

    private fun pruneEmptyParents(
        start: File?,
        repoDir: File,
        log: Logger,
    ): Int {
        if (start == null) return 0
        val rootCanonical = repoDir.canonicalFile
        var current: File? = start.canonicalFile
        var pruned = 0
        val ignorable = setOf(".DS_Store", "Thumbs.db", ".gitkeep")
        while (current != null && current != rootCanonical) {
            if (!current.exists() || !current.isDirectory) {
                current = current.parentFile?.canonicalFile
                continue
            }
            if (!current.toPath().startsWith(rootCanonical.toPath())) break
            val children = current.listFiles() ?: emptyArray()
            val nonIgnorable = children.filter { it.name !in ignorable }
            if (nonIgnorable.isNotEmpty()) break
            children.forEach { quietly { it.delete() } }
            val parent = current.parentFile?.canonicalFile
            if (current.delete()) {
                log.info("[RepoFileTools] pruned empty directory {}", current.relativeTo(rootCanonical).path)
                pruned++
            } else {
                break
            }
            current = parent
        }
        return pruned
    }
}
