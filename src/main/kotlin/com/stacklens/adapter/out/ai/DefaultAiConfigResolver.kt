package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConfigResolver
import org.springframework.stereotype.Component

@Component
class DefaultAiConfigResolver(
    private val aiConfigRepo: AiConfigPort,
) : AiConfigResolver {
    override fun findOllamaUrl(): String? = aiConfigRepo.findAiConfig()?.ollamaUrl

    override fun findOllamaNumCtx(): Int = aiConfigRepo.findAiConfig()?.ollamaNumCtx ?: 0

    override fun findModel(): String? = aiConfigRepo.findAiConfig()?.model

    override fun findLanguage(): String? = aiConfigRepo.findAiConfig()?.language
}
