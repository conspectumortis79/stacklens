
package com.stacklens.application.port.out.prompt

class PromptContext(
    private val prompts: Map<String, String>,
) {
    fun get(
        key: String,
        vars: Map<String, String> = emptyMap(),
    ): String {
        val template = prompts[key] ?: key
        return vars.entries.fold(template) { acc, (k, v) -> acc.replace("{$k}", v) }
    }
}

data class PromptGroup(
    val label: String,
    val mainKey: String,
)

interface PromptReaderPort {
    fun get(
        lang: String,
        key: String,
        vars: Map<String, String> = emptyMap(),
    ): String

    fun context(lang: String): PromptContext
}

interface PromptLoaderPort : PromptReaderPort {
    fun getCustom(key: String): String?

    fun getDefault(
        lang: String,
        key: String,
    ): String

    fun saveCustom(
        key: String,
        content: String,
    )

    fun deleteCustom(key: String)

    fun listKeys(
        lang: String,
        prefix: String,
    ): List<String>

    fun listGroups(
        lang: String,
        prefix: String,
    ): List<PromptGroup>
}
