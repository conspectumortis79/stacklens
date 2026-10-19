package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.web.multipart.MultipartFile
import java.io.File

object ToolingApiSupport {
    fun tr(
        i18n: I18nUseCase,
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    fun resolveOriginalFilename(file: MultipartFile): String = file.originalFilename ?: ""

    fun listDirectoryEntries(
        rootDir: File,
        directoriesOnly: Boolean,
        extensions: List<String>?,
    ): List<Map<String, Any>> = rootDir.listFiles()?.mapNotNull { safeFileEntry(it, directoriesOnly, extensions) } ?: emptyList()

    fun safeFileEntry(
        file: File,
        directoriesOnly: Boolean,
        extensions: List<String>?,
    ): Map<String, Any>? =
        try {
            if (file.isHidden) {
                null
            } else if (file.isDirectory) {
                mapOf("name" to file.name, "path" to file.absolutePath, "type" to "directory")
            } else if (!directoriesOnly && file.isFile) {
                val ext = file.extension.lowercase()
                val allowed = extensions ?: listOf("jar")
                if (ext in allowed) mapOf("name" to file.name, "path" to file.absolutePath, "type" to "file") else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
}
