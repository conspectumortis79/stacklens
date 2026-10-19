package com.stacklens.application.service.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileEntry
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataFileUseCase
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataReadResult
import com.stacklens.application.port.inbound.dockertest.DockerDemoDataWriteResult
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.out.file.FileListingReadWriteDeletePort
import com.stacklens.application.stereotype.ApplicationService
import java.io.File

@ApplicationService
class DockerDemoDataFileService(
    private val fileSystem: FileListingReadWriteDeletePort,
) : DockerDemoDataFileUseCase {
    private val editableExtensions = setOf("py", "json", "sql", "yml", "yaml")
    private val skipDirNames = setOf("wheels", "__pycache__", ".git", "node_modules")
    private val maxDepth = 5
    private val unsafeFilenameCharacters = Regex("[^a-zA-Z0-9_\\-.]")

    override fun listDataFiles(composeFile: String?): List<DockerDemoDataFileEntry> {
        val compose = composeFile ?: return emptyList()
        val baseDir = resolveBaseDir(compose) ?: return emptyList()
        val composeCanonical =
            try {
                fileSystem.canonicalFile(File(compose))
            } catch (e: Exception) {
                null
            }
        val result = mutableListOf<DockerDemoDataFileEntry>()
        collectDataFiles(baseDir, baseDir, result, 0)
        return result.filter { it.path != composeCanonical?.path }
    }

    override fun readDataFile(
        composeFile: String?,
        path: String,
    ): DockerDemoDataReadResult {
        val baseDir = resolveBaseDir(composeFile) ?: return DockerDemoDataReadResult.ComposeNotFound
        val target = fileSystem.canonicalFile(File(path))
        if (!isInside(target, baseDir)) {
            return DockerDemoDataReadResult.PathOutsideBase
        }
        if (!fileSystem.isFile(target)) {
            return DockerDemoDataReadResult.NotAFile
        }
        if (target.extension.lowercase() !in editableExtensions) {
            return DockerDemoDataReadResult.ExtensionNotEditable
        }
        return DockerDemoDataReadResult.Read(fileSystem.readText(target))
    }

    override fun writeDataFile(
        composeFile: String?,
        path: String,
        content: String,
    ): DockerDemoDataWriteResult {
        val baseDir = resolveBaseDir(composeFile) ?: return DockerDemoDataWriteResult.ComposeNotFound
        val targetDirectory = File(path).parent ?: ""
        val filename = File(path).name
        return saveDataFile(targetDirectory, filename, content, baseDir)
    }

    private fun saveDataFile(
        directory: String,
        filename: String,
        content: String,
        allowedRoot: File,
    ): DockerDemoDataWriteResult {
        val target = File(File(directory), filename.replace(unsafeFilenameCharacters, "_"))
        val canonicalTarget =
            try {
                fileSystem.canonicalFile(target)
            } catch (e: Exception) {
                return DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT)
            }
        val canonicalRoot =
            try {
                fileSystem.canonicalFile(allowedRoot)
            } catch (e: Exception) {
                return DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT)
            }
        if (!isInside(canonicalTarget, canonicalRoot)) {
            return DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT)
        }
        if (!fileSystem.exists(canonicalTarget) || !fileSystem.isFile(canonicalTarget)) {
            return DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE)
        }
        if (canonicalTarget.extension.lowercase() !in editableExtensions) {
            return DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE)
        }
        return try {
            fileSystem.writeFile(canonicalTarget, content)
            DockerDemoDataWriteResult.Saved(canonicalTarget.absolutePath)
        } catch (e: Exception) {
            DockerDemoDataWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED)
        }
    }

    private fun collectDataFiles(
        baseDir: File,
        dir: File,
        result: MutableList<DockerDemoDataFileEntry>,
        depth: Int,
    ) {
        if (depth > maxDepth) return
        val entries = fileSystem.listEntries(dir)
        for (entry in entries.sortedBy { it.name }) {
            if (fileSystem.isDirectory(entry)) {
                if (entry.name !in skipDirNames) {
                    collectDataFiles(baseDir, entry, result, depth + 1)
                }
            } else if (fileSystem.isFile(entry)) {
                val ext = entry.extension.lowercase()
                if (ext in editableExtensions) {
                    val canonical = fileSystem.canonicalFile(entry)
                    val rel = canonical.path.removePrefix(baseDir.path).trimStart(File.separatorChar)
                    result.add(DockerDemoDataFileEntry(path = canonical.path, label = rel))
                }
            }
        }
    }

    private fun resolveBaseDir(composeFile: String?): File? {
        val parent = composeFile?.let { File(it).parentFile } ?: return null
        return try {
            fileSystem.canonicalFile(parent)
        } catch (e: Exception) {
            null
        }
    }

    private fun isInside(
        target: File,
        root: File,
    ): Boolean = target.path == root.path || target.path.startsWith(root.path + File.separator)
}
