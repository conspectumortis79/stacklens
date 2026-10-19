package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.model.AiConfig

@ApplicationService
class GithubTokenSecretField : AiConfigSecretField {
    override val id: String = "github"

    override fun submission(request: AiConfigSaveRequest): AiSecretSubmission = AiSecretSubmission(request.githubToken, request.explicitClearGithubToken)

    override fun read(config: AiConfig): String? = config.githubToken

    override fun write(
        config: AiConfig,
        value: String?,
    ) {
        config.githubToken = value
    }
}

@ApplicationService
class OpenAiSecretField : AiConfigSecretField {
    override val id: String = "openai"

    override fun submission(request: AiConfigSaveRequest): AiSecretSubmission = AiSecretSubmission(request.openAiApiKey, request.explicitClearOpenAiKey)

    override fun read(config: AiConfig): String? = config.openAiApiKey

    override fun write(
        config: AiConfig,
        value: String?,
    ) {
        config.openAiApiKey = value
    }
}

@ApplicationService
class OpenRouterSecretField : AiConfigSecretField {
    override val id: String = "openrouter"

    override fun submission(request: AiConfigSaveRequest): AiSecretSubmission = AiSecretSubmission(request.openRouterApiKey, request.explicitClearOpenRouterKey)

    override fun read(config: AiConfig): String? = config.openRouterApiKey

    override fun write(
        config: AiConfig,
        value: String?,
    ) {
        config.openRouterApiKey = value
    }
}

@ApplicationService
class ClaudeCodeSecretField : AiConfigSecretField {
    override val id: String = "claudecode"

    override fun submission(request: AiConfigSaveRequest): AiSecretSubmission = AiSecretSubmission(request.claudeCodeApiKey, request.explicitClearClaudeCodeKey)

    override fun read(config: AiConfig): String? = config.claudeCodeApiKey

    override fun write(
        config: AiConfig,
        value: String?,
    ) {
        config.claudeCodeApiKey = value
    }
}

@ApplicationService
class CompatibleSecretField : AiConfigSecretField {
    override val id: String = "compatible"

    override fun submission(request: AiConfigSaveRequest): AiSecretSubmission = AiSecretSubmission(request.compatibleApiKey, request.explicitClearCompatibleKey)

    override fun read(config: AiConfig): String? = config.compatibleApiKey

    override fun write(
        config: AiConfig,
        value: String?,
    ) {
        config.compatibleApiKey = value
    }
}

@ApplicationService
@ManuallyWired
class AiConfigSecretFieldRegistry
    constructor(
        private val fields: List<AiConfigSecretField>,
    ) {
        constructor() : this(
            listOf(
                GithubTokenSecretField(),
                OpenAiSecretField(),
                OpenRouterSecretField(),
                ClaudeCodeSecretField(),
                CompatibleSecretField(),
            ),
        )

        fun fields(): List<AiConfigSecretField> = fields
    }
