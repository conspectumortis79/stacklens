package com.stacklens.domain.model

data class ClaudeCodeCredential(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMillis: Long? = null,
    val scopes: List<String> = emptyList(),
    val clientId: String? = null,
) {
    val isRefreshable: Boolean
        get() = !refreshToken.isNullOrBlank() && accessToken.isNotBlank()

    fun needsRefresh(
        nowMillis: Long,
        skewMillis: Long,
    ): Boolean = expiresAtMillis?.let { it - nowMillis <= skewMillis } ?: false
}
