package com.stacklens.adapter.out.filesystem

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.UiPreferencesPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

@Component
open class FileUiPreferencesAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${stacklens.data-dir:./data}") private val dataDir: String,
) : UiPreferencesPort {
    private val log = LoggerFactory.getLogger(FileUiPreferencesAdapter::class.java)

    @Synchronized
    override fun getAll(): Map<String, String> {
        val file = uiPrefsFile()
        if (!file.exists() || !file.isFile) return emptyMap()
        return try {
            val root = objectMapper.readTree(file)
            if (!root.isObject) return emptyMap()
            root.properties().associate { (key, value) -> key to value.asText() }
        } catch (e: Exception) {
            log.warn("Could not read UI preferences from {}: {}", file.absolutePath, e.message)
            emptyMap()
        }
    }

    @Synchronized
    override fun replaceAll(preferences: Map<String, String>) {
        try {
            val file = uiPrefsFile()
            file.parentFile?.mkdirs()
            objectMapper.writeValue(file, preferences)
        } catch (e: Exception) {
            log.warn("Could not write UI preferences: {}", e.message)
        }
    }

    internal open fun uiPrefsFile(): File = File(dataDir, FILE_NAME)

    companion object {
        const val FILE_NAME: String = "ui-prefs.json"
    }
}
