package com.stacklens.application.port.inbound.prompt

data class PromptGroupSummary(
    val label: String,
    val mainKey: String,
)

data class PromptContent(
    val key: String,
    val content: String,
    val isCustom: Boolean,
    val defaultContent: String,
)

interface PromptManagementReadUseCase {
    fun listKeys(
        lang: String,
        prefix: String,
    ): List<String>

    fun listGroups(
        lang: String,
        prefix: String,
    ): List<PromptGroupSummary>

    fun getPrompt(
        key: String,
        lang: String,
    ): PromptContent
}

interface PromptManagementWriteUseCase {
    fun savePrompt(
        key: String,
        content: String,
    )

    fun deletePrompt(
        key: String,
        lang: String,
    ): String
}

interface PromptManagementUseCase :
    PromptManagementReadUseCase,
    PromptManagementWriteUseCase
