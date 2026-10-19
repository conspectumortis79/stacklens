package com.stacklens.application.port.out.config

interface ClaudeCodeSettingsPort {
    fun defaultModel(): String

    fun configuredApiKey(): String?

    fun configuredOauthToken(): String?

    fun oauthTokenUrl(): String

    fun oauthClientId(): String?
}
