
package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptGroup
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class
PromptLoader(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    @Value("\${stacklens.data-dir:./data}") private val dataDir: String,
) : PromptLoaderPort {
    private val log = LoggerFactory.getLogger(PromptLoader::class.java)
    private val customCache: ConcurrentHashMap<String, String> by lazy { loadCustomPrompts() }
    private val parsedCache: ConcurrentHashMap<String, Map<String, String>> = ConcurrentHashMap()

    override fun get(
        lang: String,
        key: String,
        vars: Map<String, String>,
    ): String {
        val template: String =
            customCache[key]
                ?: loadFile(lang)[key]
                ?: loadFile("en")[key]
                ?: run {
                    log.error(
                        "Prompt key '{}' not found in custom overrides, language '{}' prompts, or 'en' fallback — returning raw key. " +
                            "Check that the prompt file exists, the language code is correct, and no stray custom-prompts.json entry shadows it.",
                        key,
                        lang,
                    )
                    key
                }
        return vars.entries.fold(template) { acc, (k, v) -> acc.replace("{$k}", v) }
    }

    override fun getCustom(key: String): String? = customCache[key]

    override fun getDefault(
        lang: String,
        key: String,
    ): String =
        loadFile(lang)[key]
            ?: loadFile("en")[key]
            ?: run {
                log.error(
                    "Prompt key '{}' not found in language '{}' or 'en' fallback for getDefault — returning raw key.",
                    key,
                    lang,
                )
                key
            }

    override fun saveCustom(
        key: String,
        content: String,
    ) {
        customCache[key] = content
        persistCustomPrompts()
    }

    override fun deleteCustom(key: String) {
        customCache.remove(key)
        persistCustomPrompts()
    }

    override fun listKeys(
        lang: String,
        prefix: String,
    ): List<String> {
        val dot = "$prefix."
        val allKeys =
            buildMap<String, String> {
                putAll(loadFile("en"))
                if (lang != "en") putAll(loadFile(lang))
                putAll(customCache)
            }.keys
        return allKeys.filter { it.startsWith(dot) }.sorted()
    }

    override fun listGroups(
        lang: String,
        prefix: String,
    ): List<PromptGroup> {
        val dot = "$prefix."
        val sectionPrefix = "$prefix._section."

        val values: Map<String, String> =
            buildMap {
                putAll(loadFile("en"))
                if (lang != "en") putAll(loadFile(lang))
                putAll(customCache)
            }

        val enKeys = loadFile("en").keys.filter { it.startsWith(dot) }
        val extraKeys = values.keys.filter { it.startsWith(dot) && it !in enKeys.toHashSet() }
        val allKeys = enKeys + extraKeys
        val groups = mutableListOf<PromptGroup>()
        var pendingLabel: String? = null
        for (key in allKeys) {
            if (key.startsWith(sectionPrefix)) {
                pendingLabel = values[key]?.takeIf { it.isNotBlank() }
                    ?: key.removePrefix(sectionPrefix)
            } else if (pendingLabel != null) {
                groups += PromptGroup(label = pendingLabel, mainKey = key)
                pendingLabel = null
            }
        }

        if (groups.isEmpty()) {
            return allKeys
                .filter { !it.startsWith(sectionPrefix) }
                .map { PromptGroup(label = it.removePrefix(dot), mainKey = it) }
        }
        return groups
    }

    override fun context(lang: String): PromptContext {
        val merged =
            buildMap<String, String> {
                putAll(loadFile("en"))
                if (lang != "en") putAll(loadFile(lang))
                putAll(customCache)
            }
        return PromptContext(merged)
    }

    private fun loadFile(lang: String): Map<String, String> = parsedCache.getOrPut(lang) { parseLanguagePrompts(lang) }

    private fun parseLanguagePrompts(lang: String): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        for (area in PROMPT_AREAS) {
            val resource = resourceLoader.getResource("classpath:prompts/$lang/$area.md")
            if (!resource.exists()) continue
            try {
                val content = String(resource.inputStream.readAllBytes(), Charsets.UTF_8)
                parseMdPromptFile(content).forEach { (k, v) -> merged[k] = v }
            } catch (e: Exception) {
                log.warn(
                    "Failed to read prompt file 'classpath:prompts/{}/{}.md' — skipping its keys. " +
                        "If many keys later log 'Prompt key not found', this is the upstream cause.",
                    lang,
                    area,
                    e,
                )
            }
        }
        return merged
    }

    private fun parseMdPromptFile(content: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val lines = content.lines()
        var currentKey: String? = null
        val currentLines = mutableListOf<String>()
        val keyPattern = Regex("^[a-zA-Z][a-zA-Z0-9_-]*(?:\\.[a-zA-Z0-9_-]+)+$")

        fun flush() {
            val key = currentKey ?: return
            result[key] = currentLines.joinToString("\n").trimEnd()
        }

        for (line in lines) {
            if (line.startsWith("## ")) {
                val candidate = line.removePrefix("## ").trim()
                if (keyPattern.matches(candidate)) {
                    flush()
                    currentKey = candidate
                    currentLines.clear()
                    continue
                }
            }
            if (currentKey != null) currentLines.add(line)
        }
        flush()
        return result
    }

    companion object {
        private val PROMPT_AREAS =
            listOf(
                "common",
                "commontools",
                "grafana",
                "codeanalysis",
                "codeqa",
                "sql",
                "tooling",
                "review",
                "search",
                "loadtest",
                "securityanalysis",
                "mranalysis",
                "logging",
                "ticketrefinement",
                "ticketcreation",
                "sprintreview",
                "ticketsummary",
                "pipeline",
                "kichat",
                "imageextraction",
            )
    }

    private fun loadCustomPrompts(): ConcurrentHashMap<String, String> {
        val file = File(dataDir, "custom-prompts.json")
        if (!file.exists()) return ConcurrentHashMap()
        return try {
            ConcurrentHashMap(objectMapper.readValue<Map<String, String>>(file))
        } catch (_: Exception) {
            ConcurrentHashMap()
        }
    }

    private fun persistCustomPrompts() {
        File(dataDir).mkdirs()
        val file = File(dataDir, "custom-prompts.json")
        objectMapper.writeValue(file, customCache.toMap())
    }
}
