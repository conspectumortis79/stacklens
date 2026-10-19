package com.stacklens.adapter.out.textfilter

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Component
class TextFilterConfigAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${stacklens.data-dir:./data}") private val dataDir: String,
    @Value(TextFilterProperties.ENABLED_VALUE) private val defaultEnabled: Boolean = TextFilterProperties.ENABLED_DEFAULT,
) : TextFilterConfigPort {
    private val log = LoggerFactory.getLogger(TextFilterConfigAdapter::class.java)
    private val state = AtomicBoolean(defaultEnabled)

    init {
        loadFromDisk()?.let { state.set(it) }
        log.info(
            "[TextFilterConfig] initial enabled state: {} (source: {})",
            state.get(),
            if (loadFromDisk() != null) "ui-prefs.json" else "stacklens.ai.filter.enabled",
        )
    }

    override val enabled: Boolean get() = state.get()

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        val previous = state.getAndSet(enabled)
        if (previous == enabled) return
        try {
            persistToDisk(enabled)
            log.info("[TextFilterConfig] enabled state changed: {} -> {}", previous, enabled)
        } catch (e: Exception) {
            log.warn("[TextFilterConfig] failed to persist enabled={} : {}", enabled, e.message)
        }
    }

    private fun loadFromDisk(): Boolean? {
        val file = uiPrefsFile()
        if (!file.exists()) return null
        return try {
            val tree = objectMapper.readTree(file)
            val node = tree.get(KEY) ?: return null
            when {
                node.isBoolean -> node.booleanValue()
                node.isTextual -> node.asText().equals("true", ignoreCase = true)
                else -> null
            }
        } catch (e: Exception) {
            log.warn("[TextFilterConfig] could not read {} : {}", file.absolutePath, e.message)
            null
        }
    }

    private fun persistToDisk(enabled: Boolean) {
        val file = uiPrefsFile()
        file.parentFile?.mkdirs()
        val root =
            if (file.exists()) {
                try {
                    objectMapper.readTree(file)
                } catch (e: Exception) {
                    log.warn(
                        "[TextFilterConfig] could not parse existing {} — rewriting : {}",
                        file.absolutePath,
                        e.message,
                    )
                    objectMapper.createObjectNode()
                }
            } else {
                objectMapper.createObjectNode()
            }
        val mutableRoot =
            if (root is com.fasterxml.jackson.databind.node.ObjectNode) {
                root
            } else {
                objectMapper.createObjectNode()
            }
        mutableRoot.put(KEY, enabled)
        objectMapper.writeValue(file, mutableRoot)
    }

    internal fun uiPrefsFile(): File = File(dataDir, FILE_NAME)

    companion object {
        const val KEY: String = "text_filter_enabled"

        const val FILE_NAME: String = "ui-prefs.json"
    }
}
