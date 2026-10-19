package com.stacklens.application.port.out.ai

fun interface ClaudeCodeTokenLookupPort {
    fun resolveToken(): String?

    fun tokenCandidateSources(): List<() -> String?> = listOf({ resolveToken() })
}
