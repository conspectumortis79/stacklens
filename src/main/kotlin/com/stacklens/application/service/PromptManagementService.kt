
package com.stacklens.application.service

import com.stacklens.application.port.inbound.prompt.PromptContent
import com.stacklens.application.port.inbound.prompt.PromptGroupSummary
import com.stacklens.application.port.inbound.prompt.PromptManagementUseCase
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class PromptManagementService(
    private val promptLoader: PromptLoaderPort,
) : PromptManagementUseCase {
    override fun listKeys(
        lang: String,
        prefix: String,
    ): List<String> = promptLoader.listKeys(lang, prefix)

    override fun listGroups(
        lang: String,
        prefix: String,
    ): List<PromptGroupSummary> = promptLoader.listGroups(lang, prefix).map { PromptGroupSummary(it.label, it.mainKey) }

    override fun getPrompt(
        key: String,
        lang: String,
    ): PromptContent {
        val custom = promptLoader.getCustom(key)
        val default = promptLoader.getDefault(lang, key)
        val effectivelyCustom = custom != null && custom.trim() != default.trim()
        return PromptContent(
            key = key,
            content = if (effectivelyCustom) custom else default,
            isCustom = effectivelyCustom,
            defaultContent = default,
        )
    }

    override fun savePrompt(
        key: String,
        content: String,
    ) {
        val default = promptLoader.getDefault("de", key)
        if (content.trim() == default.trim()) {
            promptLoader.deleteCustom(key)
        } else {
            promptLoader.saveCustom(key, content)
        }
    }

    override fun deletePrompt(
        key: String,
        lang: String,
    ): String {
        promptLoader.deleteCustom(key)
        return promptLoader.getDefault(lang, key)
    }
}
