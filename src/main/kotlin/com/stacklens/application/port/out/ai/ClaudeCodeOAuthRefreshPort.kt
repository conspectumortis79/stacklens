package com.stacklens.application.port.out.ai

import com.stacklens.domain.model.ClaudeCodeCredential

interface ClaudeCodeOAuthRefreshPort {
    fun refresh(credential: ClaudeCodeCredential): ClaudeCodeCredential?
}
