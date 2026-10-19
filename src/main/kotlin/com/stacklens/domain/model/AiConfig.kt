package com.stacklens.domain.model
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "ai_config")
data class AiConfig(
    @Id
    val id: Long = 1L,
    @Column(name = "github_token", length = 512)
    var githubToken: String? = null,
    @Column(name = "model", length = 100)
    var model: String? = null,
    @Column(name = "language", length = 10, nullable = false)
    var language: String = "en",
    @Column(name = "available_models", length = 2000)
    var availableModels: String? = null,
    @Column(name = "ai_provider", length = 20, nullable = false, columnDefinition = "varchar(20) default 'copilot'")
    var aiProvider: String = "copilot",
    @Column(name = "ollama_url", length = 256)
    var ollamaUrl: String? = null,
    @Column(name = "ollama_num_ctx", nullable = false, columnDefinition = "integer default 32768")
    var ollamaNumCtx: Int = 32768,
    @Column(name = "lm_studio_url", length = 256)
    var lmStudioUrl: String? = null,
    @Column(name = "openai_api_key", length = 512)
    var openAiApiKey: String? = null,
    @Column(name = "openai_reasoning_effort", length = 20)
    var openAiReasoningEffort: String? = null,
    @Column(name = "openrouter_api_key", length = 512)
    var openRouterApiKey: String? = null,
    @Column(name = "claude_code_api_key", length = 512)
    var claudeCodeApiKey: String? = null,
    @Column(name = "compatible_api_key", length = 512)
    var compatibleApiKey: String? = null,
    @Column(name = "compatible_base_url", length = 256)
    var compatibleBaseUrl: String? = null,
)

fun AiConfig.mergeImportedSettings(
    imported: AiConfig,
    replaceAll: Boolean,
) {
    if (replaceAll) {
        githubToken = imported.githubToken
        model = imported.model
    } else {
        if (!imported.githubToken.isNullOrBlank()) githubToken = imported.githubToken
        if (!imported.model.isNullOrBlank()) model = imported.model
    }
    aiProvider = imported.aiProvider
    ollamaUrl = imported.ollamaUrl
    ollamaNumCtx = imported.ollamaNumCtx
    lmStudioUrl = imported.lmStudioUrl
    openAiApiKey = imported.openAiApiKey
    openAiReasoningEffort = imported.openAiReasoningEffort
    openRouterApiKey = imported.openRouterApiKey
    claudeCodeApiKey = imported.claudeCodeApiKey
    compatibleApiKey = imported.compatibleApiKey
    compatibleBaseUrl = imported.compatibleBaseUrl
}
