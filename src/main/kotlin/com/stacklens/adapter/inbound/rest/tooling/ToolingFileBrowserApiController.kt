package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File

@RestController
@RequestMapping("/api/tooling/files")
class ToolingFileBrowserApiController(
    private val i18n: I18nUseCase,
) {
    @GetMapping("/browse")
    fun browseFiles(
        @RequestParam(required = false) path: String?,
        @RequestParam(required = false, defaultValue = "false") directoriesOnly: Boolean,
        @RequestParam(required = false) fileExtensions: String?,
    ): ResponseEntity<*> {
        val rootPath = path?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
        val rootDir = File(rootPath)

        if (!rootDir.exists() || !rootDir.isDirectory) {
            return ResponseEntity.badRequest().body(mapOf("error" to ToolingApiSupport.tr(i18n, "tooling.be_dir_not_found")))
        }

        val extensions = fileExtensions?.split(",")?.map { it.trim().lowercase().trimStart('.') }?.filter { it.isNotEmpty() }
        val parentPath = rootDir.parentFile?.absolutePath
        val entries = ToolingApiSupport.listDirectoryEntries(rootDir, directoriesOnly, extensions)
        val items = entries.sortedWith(compareBy({ it["type"] != "directory" }, { (it["name"] as String).lowercase() }))

        return ResponseEntity.ok(
            mapOf(
                "currentPath" to rootDir.absolutePath,
                "parentPath" to parentPath,
                "items" to items,
            ),
        )
    }
}
