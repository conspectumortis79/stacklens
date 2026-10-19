package com.stacklens.application.port.out.ai

import java.io.File

interface RepoToolPort {
    fun executeWithStreamingPerLine(
        model: String,
        prompt: String,
        timeoutOverride: Long? = null,
        repoDir: File? = null,
        isCancelled: (() -> Boolean)? = null,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment> = emptyList(),
    ): String
}
