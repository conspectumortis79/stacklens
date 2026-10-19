package com.stacklens.application.service.logging

import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptGroup
import com.stacklens.application.port.out.prompt.PromptLoaderPort
import java.io.File

class StubCopilot(
    private val response: String,
) : CopilotStreamingPort {
    override fun resolveToken(): String? = null

    override fun resolveModel(): String = "stub"

    override fun resolveLanguage(): String = "de"

    override fun isReady(): Boolean = true

    override fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        attachments: List<AiAttachment>,
    ): String = response

    override fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String = response

    override fun executeWithStreamingPerLine(
        model: String,
        prompt: String,
        timeoutOverride: Long?,
        repoDir: File?,
        isCancelled: (() -> Boolean)?,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment>,
    ): String = response
}

class StubPromptLoader(
    private val onGet: (lang: String, key: String, vars: Map<String, String>) -> String = { _, _, _ -> "p" },
) : PromptLoaderPort {
    override fun get(
        lang: String,
        key: String,
        vars: Map<String, String>,
    ): String = onGet(lang, key, vars)

    override fun context(lang: String) = throw UnsupportedOperationException()

    override fun getCustom(key: String): String? = null

    override fun getDefault(
        lang: String,
        key: String,
    ): String = ""

    override fun saveCustom(
        key: String,
        content: String,
    ) {}

    override fun deleteCustom(key: String) {}

    override fun listKeys(
        lang: String,
        prefix: String,
    ): List<String> = emptyList()

    override fun listGroups(
        lang: String,
        prefix: String,
    ): List<PromptGroup> = emptyList()
}
