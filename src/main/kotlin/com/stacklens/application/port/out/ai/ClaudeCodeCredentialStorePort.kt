package com.stacklens.application.port.out.ai

import com.stacklens.domain.model.ClaudeCodeCredential

interface ClaudeCodeCredentialStorePort {
    val sourceLabel: String

    fun read(): ClaudeCodeCredential?

    fun write(credential: ClaudeCodeCredential)
}
