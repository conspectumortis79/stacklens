package com.stacklens.adapter.inbound.rest.grafana

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/grafana-analyse")
class GrafanaFileBrowserController(
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @GetMapping("/files/browse")
    fun browseFiles(
        @RequestParam(required = false) path: String?,
    ): ResponseEntity<*> {
        val listing =
            workspaceFiles.browseDirectory(path)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to tr("grafana_analysis.be_dir_not_found")))
        val items =
            listing.entries
                .filter { it.isDirectory }
                .sortedBy { it.name.lowercase() }
                .map { mapOf("name" to it.name, "path" to it.path, "type" to "directory") }
        return ResponseEntity.ok(
            mapOf(
                "currentPath" to listing.path,
                "parentPath" to listing.parentPath,
                "items" to items,
            ),
        )
    }

    data class SaveDashboardJsonRequest(
        val content: String = "",
        val path: String = "",
        val filename: String = "",
    )

    @PostMapping("/save-dashboard-json")
    fun saveDashboardJson(
        @RequestBody req: SaveDashboardJsonRequest,
    ): ResponseEntity<*> {
        if (req.content.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to tr("grafana_analysis.be_no_json_content")))
        if (req.path.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to tr("grafana_analysis.be_no_path_specified")))
        val result =
            workspaceFiles.saveText(
                TextFileSaveRequest(
                    directory = req.path,
                    filename = req.filename,
                    content = req.content,
                    canonicalizeDirectory = true,
                    fallbackFilename = "grafana-dashboard.json",
                    enforceJsonExtension = true,
                ),
            )
        return when (result) {
            is WorkspaceWriteResult.Saved -> ResponseEntity.ok(mapOf("savedPath" to result.path))
            is WorkspaceWriteResult.Rejected -> rejectionResponse(req, result.reason)
        }
    }

    private fun rejectionResponse(
        req: SaveDashboardJsonRequest,
        reason: WorkspaceWriteRejection,
    ): ResponseEntity<Map<String, String>> =
        when (reason) {
            WorkspaceWriteRejection.DIRECTORY_NOT_FOUND ->
                ResponseEntity.badRequest().body(mapOf("error" to tr("grafana_analysis.be_dir_not_found_path", req.path)))
            else ->
                ResponseEntity.internalServerError().body(mapOf("error" to tr("grafana_analysis.be_save_failed", reason.name)))
        }
}
