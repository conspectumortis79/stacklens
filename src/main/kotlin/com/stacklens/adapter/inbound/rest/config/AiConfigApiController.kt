package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.AiConfigDto
import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.inbound.config.AiConfigUseCase
import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.config.AiConnectionTestUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/config")
class AiConfigApiController(
    private val configManagement: AiConfigUseCase,
    private val connectionTest: AiConnectionTestUseCase,
    private val dtoMapper: AiConfigDtoMapper,
) {
    @GetMapping("/ai")
    fun getAiConfig(): AiConfigDto = dtoMapper.toDto(configManagement.getAiConfig())

    @PostMapping("/ai/test-token")
    fun testAiToken(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.GITHUB_TOKEN, dto.toRequest())

    @PostMapping("/ai/copilot-models")
    fun fetchCopilotModels(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.COPILOT_MODELS, dto.toRequest())

    @PostMapping("/ai/test-model")
    fun testCopilotModel(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.COPILOT_MODEL, dto.toRequest())

    @PostMapping("/ai/test-ollama")
    fun testOllamaConnection(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.OLLAMA, dto.toRequest())

    @PostMapping("/ai/test-openai")
    fun testOpenAiConnection(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.OPENAI, dto.toRequest())

    @PostMapping("/ai/test-lmstudio")
    fun testLmStudioConnection(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.LMSTUDIO, dto.toRequest())

    @PostMapping("/ai/test-openrouter")
    fun testOpenRouterConnection(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.OPENROUTER, dto.toRequest())

    @PostMapping("/ai/test-claudecode")
    fun testClaudeCodeConnection(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.CLAUDE_CODE, dto.toRequest())

    @PostMapping("/ai/test-compatible")
    fun testCompatibleConnection(
        @RequestBody(required = false) dto: AiConfigDto?,
    ): Map<String, Any> = connectionTest.testConnection(AiConnectionTestTarget.COMPATIBLE, dto.toRequest())

    @PutMapping("/ai")
    fun saveAiConfig(
        @RequestBody dto: AiConfigDto,
    ): AiConfigDto {
        val saved = configManagement.saveAiConfigFrom(dto.toSaveRequest())
        return dtoMapper.toDto(saved, saved.model.orEmpty())
    }

    private fun AiConfigDto?.toRequest(): AiConnectionTestRequest =
        AiConnectionTestRequest(
            model = this?.model,
            githubToken = this?.githubToken,
            ollamaUrl = this?.ollamaUrl,
            lmStudioUrl = this?.lmStudioUrl,
            openAiApiKey = this?.openAiApiKey,
            openRouterApiKey = this?.openRouterApiKey,
            claudeCodeApiKey = this?.claudeCodeApiKey,
            compatibleApiKey = this?.compatibleApiKey,
            compatibleBaseUrl = this?.compatibleBaseUrl,
        )

    private fun AiConfigDto.toSaveRequest(): AiConfigSaveRequest =
        AiConfigSaveRequest(
            githubToken = githubToken,
            openAiApiKey = openAiApiKey,
            openAiReasoningEffort = openAiReasoningEffort,
            model = model,
            aiProvider = aiProvider,
            ollamaUrl = ollamaUrl,
            ollamaNumCtx = ollamaNumCtx,
            lmStudioUrl = lmStudioUrl,
            openRouterApiKey = openRouterApiKey,
            claudeCodeApiKey = claudeCodeApiKey,
            compatibleApiKey = compatibleApiKey,
            compatibleBaseUrl = compatibleBaseUrl,
            explicitClearGithubToken = explicitClearGithubToken,
            explicitClearOpenAiKey = explicitClearOpenAiKey,
            explicitClearOpenRouterKey = explicitClearOpenRouterKey,
            explicitClearClaudeCodeKey = explicitClearClaudeCodeKey,
            explicitClearCompatibleKey = explicitClearCompatibleKey,
        )
}
