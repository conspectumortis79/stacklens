package com.stacklens.application.service.workspace

import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceAccessView
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileCopyRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileInfo
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import com.stacklens.application.port.out.file.FileDeletion
import com.stacklens.application.port.out.file.FileListing
import com.stacklens.application.stereotype.ApplicationService
import java.io.File

@ApplicationService
class WorkspaceFileService(
    private val fileAttributes: FileAttributes,
    private val fileContentStore: FileContentStore,
    private val fileDeletion: FileDeletion,
    private val fileListing: FileListing,
) : WorkspaceFileUseCase {
    private val unsafeFilenameCharacters = Regex("[^a-zA-Z0-9_\\-.]")

    override fun saveText(request: TextFileSaveRequest): WorkspaceWriteResult {
        val targetDirectory =
            try {
                val directory = File(request.directory)
                if (request.canonicalizeDirectory) {
                    fileAttributes.canonicalFile(directory)
                } else {
                    directory
                }
            } catch (e: Exception) {
                return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND)
            }
        if (request.allowedRoot == null && (!fileAttributes.exists(targetDirectory) || !fileAttributes.isDirectory(targetDirectory))) {
            return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND)
        }
        val requestedName = if (request.filename.isBlank()) request.fallbackFilename ?: request.filename else request.filename
        var safeName = requestedName.replace(unsafeFilenameCharacters, "_")
        if (request.enforceJsonExtension && !safeName.endsWith(".json")) {
            safeName += ".json"
        }
        var target = File(targetDirectory, safeName)
        if (request.allowedRoot != null) {
            val canonicalTarget =
                try {
                    fileAttributes.canonicalFile(target)
                } catch (e: Exception) {
                    return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT)
                }
            val canonicalRoot =
                try {
                    fileAttributes.canonicalFile(File(request.allowedRoot))
                } catch (e: Exception) {
                    return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT)
                }
            if (!(canonicalTarget.path == canonicalRoot.path || canonicalTarget.path.startsWith(canonicalRoot.path + File.separator))) {
                return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT)
            }
            target = canonicalTarget
        }
        if (request.requireExistingFile && (!fileAttributes.exists(target) || !fileAttributes.isFile(target))) {
            return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE)
        }
        if (request.allowedExtensions.isNotEmpty() && target.extension.lowercase() !in request.allowedExtensions) {
            return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE)
        }
        return try {
            fileContentStore.writeFile(target, request.content)
            WorkspaceWriteResult.Saved(target.absolutePath)
        } catch (e: Exception) {
            WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED)
        }
    }

    override fun homeDirectory(): String = fileAttributes.homeDirectory().absolutePath

    override fun readBytes(path: String): ByteArray? {
        val file = File(path)
        if (!fileAttributes.exists(file) || !fileAttributes.isFile(file)) {
            return null
        }
        return fileContentStore.readBytes(file)
    }

    override fun readText(path: String): String? {
        val file = File(path)
        if (!fileAttributes.exists(file) || !fileAttributes.isFile(file)) {
            return null
        }
        return fileContentStore.readText(file)
    }

    override fun fileInfo(path: String): WorkspaceFileInfo? =
        try {
            val file = File(path)
            if (!fileAttributes.exists(file)) {
                null
            } else {
                WorkspaceFileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    size = fileAttributes.size(file),
                    isDirectory = fileAttributes.isDirectory(file),
                    isFile = fileAttributes.isFile(file),
                )
            }
        } catch (e: Exception) {
            null
        }

    override fun browseDirectory(path: String?): WorkspaceDirectoryListing? {
        val root = if (path.isNullOrBlank()) File(homeDirectory()) else File(path)
        if (!fileAttributes.exists(root) || !fileAttributes.isDirectory(root)) {
            return null
        }
        val entries =
            fileListing.listEntries(root).mapNotNull { file ->
                try {
                    if (file.isHidden) {
                        null
                    } else {
                        WorkspaceDirectoryEntry(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = fileAttributes.isDirectory(file),
                            isFile = fileAttributes.isFile(file),
                            size = fileAttributes.size(file),
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }
        return WorkspaceDirectoryListing(
            path = root.absolutePath,
            parentPath = root.parentFile?.absolutePath,
            entries = entries,
        )
    }

    override fun copyFile(request: WorkspaceFileCopyRequest): WorkspaceWriteResult {
        val directory = File(request.targetDirectory)
        if (!fileAttributes.exists(directory) || !fileAttributes.isDirectory(directory)) {
            return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND)
        }
        val source = File(request.sourcePath)
        if (!fileAttributes.exists(source) || !fileAttributes.isFile(source)) {
            return WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE)
        }
        return try {
            val target = File(directory, request.targetFilename)
            fileContentStore.writeBytes(target, fileContentStore.readBytes(source))
            WorkspaceWriteResult.Saved(target.absolutePath)
        } catch (e: Exception) {
            WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED)
        }
    }

    override fun probeAccess(path: String): WorkspaceAccessView {
        val root =
            try {
                fileAttributes.canonicalFile(File(path))
            } catch (e: Exception) {
                return WorkspaceAccessView(path, false, false, false, false, false, e.message)
            }
        val exists = fileAttributes.exists(root)
        val isDirectory = fileAttributes.isDirectory(root)
        val canRead = fileAttributes.canRead(root)
        val canWrite = fileAttributes.canWrite(root)
        if (!exists || !canWrite) {
            return WorkspaceAccessView(root.path, exists, isDirectory, canRead, canWrite, false, null)
        }
        val probeDirectory = File(root, ".stacklens")
        val probeDirectoryExisted = fileAttributes.exists(probeDirectory)
        val probeFile = File(probeDirectory, ".write-probe-${System.currentTimeMillis()}")
        var probeError: String? = null
        val probeOk =
            try {
                fileContentStore.writeFile(probeFile, "ok")
                fileDeletion.deleteFile(probeFile)
                true
            } catch (e: Exception) {
                probeError = e.message
                false
            } finally {
                if (!probeDirectoryExisted && fileAttributes.isEmptyDirectory(probeDirectory)) {
                    fileDeletion.deleteDirectoryRecursively(probeDirectory)
                }
            }
        return WorkspaceAccessView(root.path, exists, isDirectory, canRead, canWrite, probeOk, probeError)
    }
}
