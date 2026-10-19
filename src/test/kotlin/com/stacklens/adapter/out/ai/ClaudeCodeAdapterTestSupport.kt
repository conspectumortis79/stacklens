package com.stacklens.adapter.out.ai

internal fun ClaudeCodeAdapter.parseMessagesResponse(json: String): String = parseMessages(json).text
