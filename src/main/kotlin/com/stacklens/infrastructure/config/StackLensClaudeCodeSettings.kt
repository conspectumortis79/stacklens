package com.stacklens.infrastructure.config

import com.stacklens.application.port.out.config.ClaudeCodeSettingsPort
import com.stacklens.domain.enums.AiProvider
import org.springframework.stereotype.Component

@Component
class StackLensClaudeCodeSettings(
    private val properties: AnthropicProperties,
) : ClaudeCodeSettingsPort {
    override fun defaultModel(): String = properties.model.trim().takeIf { it.isNotEmpty() } ?: AiProvider.CLAUDECODE.defaultModel

    override fun configuredApiKey(): String? = properties.apiKey.trim().takeIf { it.isNotEmpty() }

    override fun configuredOauthToken(): String? = properties.oauthToken.trim().takeIf { it.isNotEmpty() }

    override fun oauthTokenUrl(): String = properties.oauthTokenUrl.trim().takeIf { it.isNotEmpty() } ?: AnthropicProperties.DEFAULT_OAUTH_TOKEN_URL

    override fun oauthClientId(): String? = properties.oauthClientId.trim().takeIf { it.isNotEmpty() }
}
