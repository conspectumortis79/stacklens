package com.stacklens.application.service.ai

interface AiCaller {
    fun execute(
        model: String,
        prompt: String,
        timeoutSec: Long,
    ): String
}
