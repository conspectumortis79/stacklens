package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.tooling.ToolingImportUseCase
import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileCopyRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/tooling")
class ToolingImportExportApiController(
    private val toolingRead: ToolingReadUseCase,
    private val toolingImport: ToolingImportUseCase,
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) {
    @PostMapping("/import")
    fun import(
        @RequestParam("name") name: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<*> {
        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_name_required")))
        }
        val originalFilename = ToolingApiSupport.resolveOriginalFilename(file)
        if (file.isEmpty || !originalFilename.endsWith(".jar", ignoreCase = true)) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_invalid_jar_upload")))
        }

        return try {
            val tool = toolingImport.importTool(name, file.bytes, originalFilename)
            ResponseEntity.ok(mapOf("id" to tool.id, "status" to tool.status))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_import_failed", e.message.orEmpty())),
            )
        }
    }

    @PostMapping("/import-base64")
    fun importBase64(
        @RequestBody req: Base64ImportRequest,
    ): ResponseEntity<*> {
        if (req.name.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_name_required")))
        }
        if (!req.filename.endsWith(".jar", ignoreCase = true)) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_invalid_jar_upload")))
        }
        return try {
            val base64Data = if (req.base64.contains(",")) req.base64.substringAfter(",") else req.base64
            val bytes =
                java.util.Base64
                    .getDecoder()
                    .decode(base64Data)
            val tool = toolingImport.importTool(req.name, bytes, req.filename)
            ResponseEntity.ok(mapOf("id" to tool.id, "status" to tool.status))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_import_failed", e.message.orEmpty())),
            )
        }
    }

    @PostMapping("/import-local")
    fun importLocal(
        @RequestBody req: LocalImportRequest,
    ): ResponseEntity<*> {
        if (req.name.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_name_required")))
        }
        val info = workspaceFiles.fileInfo(req.path)
        if (info == null || !info.isFile || !info.name.endsWith(".jar", ignoreCase = true)) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_invalid_jar_path")))
        }

        return try {
            val bytes = workspaceFiles.readBytes(req.path) ?: error("read_failed")
            val tool = toolingImport.importTool(req.name, bytes, info.name)
            ResponseEntity.ok(mapOf("id" to tool.id, "status" to tool.status))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_import_failed", e.message.orEmpty())),
            )
        }
    }

    @GetMapping("/{id}/export")
    fun export(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        val tool =
            toolingRead.getTool(id)
                ?: return ResponseEntity.notFound().build()

        val jarPath = tool.jarPath ?: return ResponseEntity.badRequest().build()
        val bytes = workspaceFiles.readBytes(jarPath) ?: return ResponseEntity.notFound().build()

        val resource: Resource = ByteArrayResource(bytes)
        val headers = HttpHeaders()
        headers.add(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"tool-${tool.id}-${tool.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.jar\"",
        )

        return ResponseEntity
            .ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }

    @PostMapping("/{id}/save-to-path")
    fun saveToPath(
        @PathVariable id: Long,
        @RequestBody req: SaveToPathRequest,
    ): ResponseEntity<*> {
        val tool =
            toolingRead.getTool(id)
                ?: return ResponseEntity.notFound().build<Any>()
        val jarPath =
            tool.jarPath
                ?: return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_no_jar")))
        val jarInfo = workspaceFiles.fileInfo(jarPath)
        if (jarInfo == null || !jarInfo.isFile) {
            return ResponseEntity.status(404).body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_jar_not_found")))
        }
        val safeName = tool.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val result =
            workspaceFiles.copyFile(
                WorkspaceFileCopyRequest(
                    sourcePath = jarPath,
                    targetDirectory = req.path,
                    targetFilename = "tool-${tool.id}-$safeName.jar",
                ),
            )
        return when (result) {
            is WorkspaceWriteResult.Saved -> ResponseEntity.ok(mapOf("savedPath" to result.path))
            is WorkspaceWriteResult.Rejected ->
                if (result.reason == WorkspaceWriteRejection.DIRECTORY_NOT_FOUND) {
                    ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_target_dir_not_found")))
                } else {
                    ResponseEntity.internalServerError().body(
                        mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_save_failed", result.reason.name)),
                    )
                }
        }
    }

    data class Base64ImportRequest(
        val name: String,
        val filename: String,
        val base64: String,
    )

    data class LocalImportRequest(
        val name: String,
        val path: String,
    )

    data class SaveToPathRequest(
        val path: String,
    )
}
