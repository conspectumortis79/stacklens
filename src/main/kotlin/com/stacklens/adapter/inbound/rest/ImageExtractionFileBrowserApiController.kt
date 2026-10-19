package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Base64

@RestController
@RequestMapping("/api/image-extraction/files")
class ImageExtractionFileBrowserApiController(
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) {
    private val allowedExtensions: Set<String> = ImageMimeTypes.fileBrowserExtensions
    private val maxReadBytes = 16L * 1024L * 1024L

    @GetMapping("/browse")
    fun browseFiles(
        @RequestParam(required = false) path: String?,
        @RequestParam(required = false, defaultValue = "false") directoriesOnly: Boolean,
        @RequestParam(required = false) fileExtensions: String?,
    ): ResponseEntity<Any> {
        val listing =
            workspaceFiles.browseDirectory(path)
                ?: return ResponseEntity.badRequest().body(
                    mapOf("error" to i18n.t("tooling.be_dir_not_found")),
                )
        val extensions =
            if (fileExtensions == null) {
                allowedExtensions.toList()
            } else {
                fileExtensions
                    .split(",")
                    .map { it.trim().lowercase().trimStart('.') }
                    .filter { it.isNotEmpty() }
            }
        val entries =
            listing.entries
                .mapNotNull { entry -> safeEntry(entry, directoriesOnly, extensions) }
                .sortedWith(compareBy({ it["type"] != "directory" }, { (it["name"] as String).lowercase() }))
        return ResponseEntity.ok(
            mapOf(
                "currentPath" to listing.path,
                "parentPath" to listing.parentPath,
                "items" to entries,
            ),
        )
    }

    @GetMapping("/read", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun readFile(
        @RequestParam("path") path: String,
    ): ResponseEntity<Any> {
        if (path.isBlank()) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to i18n.t("tooling.be_dir_not_found")))
        }
        val info = workspaceFiles.fileInfo(path)
        if (info == null || !info.isFile) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to i18n.t("tooling.be_dir_not_found")))
        }
        val ext = info.name.substringAfterLast('.', "").lowercase()
        if (ext !in allowedExtensions) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to i18n.t("tooling.be_dir_not_found")))
        }
        if (info.size > maxReadBytes) {
            return ResponseEntity
                .status(413)
                .body(mapOf("error" to i18n.t("tooling.be_dir_not_found")))
        }
        return try {
            val bytes = workspaceFiles.readBytes(path) ?: error("read_failed")
            val mime = ImageMimeTypes.mimeFor(ext)!!
            ResponseEntity.ok(
                mapOf(
                    "name" to info.name,
                    "path" to info.path,
                    "size" to bytes.size.toLong(),
                    "mimeType" to mime,
                    "base64Data" to Base64.getEncoder().encodeToString(bytes),
                ),
            )
        } catch (e: Exception) {
            ResponseEntity
                .internalServerError()
                .body(mapOf("error" to (e.message ?: "read_failed")))
        }
    }

    private fun safeEntry(
        entry: WorkspaceDirectoryEntry,
        directoriesOnly: Boolean,
        extensions: List<String>,
    ): Map<String, Any>? =
        when {
            entry.isDirectory -> mapOf("name" to entry.name, "path" to entry.path, "type" to "directory")
            !directoriesOnly && entry.isFile -> {
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                if (ext in extensions) {
                    mapOf(
                        "name" to entry.name,
                        "path" to entry.path,
                        "type" to "file",
                        "size" to entry.size,
                    )
                } else {
                    null
                }
            }
            else -> null
        }
}
