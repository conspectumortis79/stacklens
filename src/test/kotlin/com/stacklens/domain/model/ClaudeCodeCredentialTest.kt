package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaudeCodeCredentialTest {
    @Test
    fun `a credential with a refresh token and a usable access token can be refreshed`() {
        assertTrue(ClaudeCodeCredential(accessToken = "sk-ant-oat01-live", refreshToken = "sk-ant-ort01-live").isRefreshable)
    }

    @Test
    fun `a credential without a refresh token cannot be refreshed`() {
        assertFalse(ClaudeCodeCredential(accessToken = "sk-ant-oat01-live").isRefreshable)
        assertFalse(ClaudeCodeCredential(accessToken = "sk-ant-oat01-live", refreshToken = "").isRefreshable)
        assertFalse(ClaudeCodeCredential(accessToken = "sk-ant-oat01-live", refreshToken = "   ").isRefreshable)
    }

    @Test
    fun `a credential with an unusable access token cannot be refreshed`() {
        assertFalse(ClaudeCodeCredential(accessToken = "", refreshToken = "sk-ant-ort01-live").isRefreshable)
        assertFalse(ClaudeCodeCredential(accessToken = "   ", refreshToken = "sk-ant-ort01-live").isRefreshable)
    }

    @Test
    fun `a token is due for refresh once the skew window covers the expiry`() {
        val credential = ClaudeCodeCredential(accessToken = "t", expiresAtMillis = 1_000_000L)

        assertTrue(credential.needsRefresh(nowMillis = 900_000L, skewMillis = 100_000L))
        assertFalse(credential.needsRefresh(nowMillis = 899_999L, skewMillis = 100_000L))
    }

    @Test
    fun `a token without an expiry is never due for refresh`() {
        assertEquals(false, ClaudeCodeCredential(accessToken = "t").needsRefresh(nowMillis = 1L, skewMillis = 1L))
    }
}
