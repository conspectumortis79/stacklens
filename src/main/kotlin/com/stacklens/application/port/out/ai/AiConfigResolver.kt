package com.stacklens.application.port.out.ai

interface AiConfigResolver {
    fun findOllamaUrl(): String?

    fun findOllamaNumCtx(): Int

    fun findModel(): String?

    fun findLanguage(): String?
}
