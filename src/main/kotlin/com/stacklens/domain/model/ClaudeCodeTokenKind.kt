package com.stacklens.domain.model

object ClaudeCodeTokenKind {
    const val OAUTH_ACCESS_TOKEN_PREFIX: String = "sk-ant-oat"

    fun isOauthAccessToken(token: String): Boolean = token.startsWith(OAUTH_ACCESS_TOKEN_PREFIX)
}
