
package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.JsonReaderPort
import com.stacklens.application.port.out.TranslationCatalogPort
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader

class ClasspathTranslationCatalogAdapter(
    private val resourceLoader: ResourceLoader,
    private val jsonCodec: JsonReaderPort,
) : TranslationCatalogPort {
    private val log = LoggerFactory.getLogger(ClasspathTranslationCatalogAdapter::class.java)

    override fun load(language: String): Map<String, String> {
        val resource = resourceLoader.getResource("classpath:static/i18n/$language.json")
        if (!resource.exists()) return emptyMap()
        return try {
            val tree = jsonCodec.decodeMap(resource.inputStream)
            flatten(tree, "")
        } catch (e: Exception) {
            log.error(
                "Failed to load i18n messages for lang='{}' — falling back to key passthrough: {}",
                language,
                e.message,
            )
            emptyMap()
        }
    }

    private fun flatten(
        map: Map<String, Any?>,
        prefix: String,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((k, v) in map) {
            val fullKey = if (prefix.isEmpty()) k else "$prefix.$k"
            when (v) {
                is String -> result[fullKey] = v
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    result.putAll(flatten(v as Map<String, Any?>, fullKey))
                }
                else -> Unit
            }
        }
        return result
    }
}
