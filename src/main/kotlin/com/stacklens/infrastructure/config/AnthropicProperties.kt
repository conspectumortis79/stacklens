package com.stacklens.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "anthropic")
data class AnthropicProperties(
    val apiKey: String = "",
    val oauthToken: String = "",
    val model: String = "",
    val oauthTokenUrl: String = DEFAULT_OAUTH_TOKEN_URL,
    val oauthClientId: String = DEFAULT_OAUTH_CLIENT_ID,
) {
    companion object {
        const val DEFAULT_OAUTH_TOKEN_URL: String = "https://platform.claude.com/v1/oauth/token"

        const val DEFAULT_OAUTH_CLIENT_ID: String = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    }
}
