package com.stacklens.application.port.out.ai

fun interface ClaudeCodeCredentialAccessPort {
    fun accessToken(): String?
}
