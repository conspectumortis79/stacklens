package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.ClaudeCodeCredentialAccessPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClaudeCodeTokenLookupPortAdapterTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private var credentialCalls: Int = 0

    private fun adapter(
        storedKey: String?,
        credential: String? = null,
        oauthToken: String? = null,
        apiKey: String? = null,
    ): ClaudeCodeTokenLookupPortAdapter {
        whenever(aiConfigRepo.findAiConfig()) doReturn storedKey?.let { AiConfig(claudeCodeApiKey = it) }
        return ClaudeCodeTokenLookupPortAdapter(
            aiConfigRepo,
            ClaudeCodeTestSupport.settings(apiKey = apiKey, oauthToken = oauthToken),
            ClaudeCodeCredentialAccessPort {
                credentialCalls++
                credential
            },
        )
    }

    @Test
    fun `candidate sources keep the stored first order and stay lazy until asked`() {
        val lookup =
            adapter(
                storedKey = "stored-key",
                credential = "store-token",
                oauthToken = "oauth-env",
                apiKey = "api-env",
            )

        val sources = lookup.tokenCandidateSources()
        assertEquals(0, credentialCalls)
        assertEquals(listOf("stored-key", "oauth-env", "api-env", "store-token"), sources.map { it() })
        assertEquals(1, credentialCalls)
    }

    @Test
    fun `every candidate is offered even when the stored key is dead`() {
        val lookup = adapter(storedKey = "dead-key", credential = "login-token", oauthToken = null, apiKey = null)

        assertEquals(listOf("dead-key", "login-token"), lookup.tokenCandidateSources().mapNotNull { it() })
        assertEquals("dead-key", lookup.resolveToken())
    }

    @Test
    fun `a repository that throws is skipped by resolveToken`() {
        whenever(aiConfigRepo.findAiConfig()) doThrow IllegalStateException("database is locked")

        val lookup =
            ClaudeCodeTokenLookupPortAdapter(
                aiConfigRepo,
                ClaudeCodeTestSupport.settings(oauthToken = "oauth-env"),
                ClaudeCodeCredentialAccessPort { null },
            )

        assertEquals("oauth-env", lookup.resolveToken())
    }

    @Test
    fun `a credential source that throws is skipped by resolveToken`() {
        whenever(aiConfigRepo.findAiConfig()) doReturn null
        val lookup =
            ClaudeCodeTokenLookupPortAdapter(
                aiConfigRepo,
                ClaudeCodeTestSupport.settings(),
                ClaudeCodeCredentialAccessPort { error("keychain locked") },
            )

        assertNull(lookup.resolveToken())
        assertEquals(4, lookup.tokenCandidateSources().size)
        assertTrue(lookup.tokenCandidateSources().all { source -> runCatching { source() }.getOrElse { null } == null })
    }

    @Test
    fun `the stored key wins over every other source and is trimmed`() {
        val token =
            adapter(
                storedKey = "  stored-key  ",
                credential = "store-token",
                oauthToken = "oauth-env",
                apiKey = "api-env",
            ).resolveToken()

        assertEquals("stored-key", token)
        assertEquals(0, credentialCalls)
    }

    @Test
    fun `a blank stored key is no key at all`() {
        assertEquals("oauth-env", adapter(storedKey = "   ", oauthToken = "oauth-env").resolveToken())
    }

    @Test
    fun `no ai config row still allows the configured token sources`() {
        whenever(aiConfigRepo.findAiConfig()) doReturn null

        val lookup = ClaudeCodeTokenLookupPortAdapter(aiConfigRepo, ClaudeCodeTestSupport.settings(oauthToken = "oauth-env"), ClaudeCodeCredentialAccessPort { null })

        assertEquals("oauth-env", lookup.resolveToken())
    }

    @Test
    fun `the oauth token beats the plain api key and the credential stores`() {
        val token = adapter(storedKey = null, credential = "store-token", oauthToken = "oauth-env", apiKey = "api-env").resolveToken()

        assertEquals("oauth-env", token)
        assertEquals(0, credentialCalls)
    }

    @Test
    fun `the api key is used when no oauth token is configured`() {
        assertEquals("api-env", adapter(storedKey = null, credential = "store-token", apiKey = "api-env").resolveToken())
    }

    @Test
    fun `the claude code login is the last source`() {
        assertEquals("store-token", adapter(storedKey = null, credential = "store-token").resolveToken())
        assertEquals(1, credentialCalls)
    }

    @Test
    fun `a configured token of only whitespace is skipped`() {
        assertEquals("store-token", adapter(storedKey = null, credential = "store-token", oauthToken = "  ", apiKey = "\t").resolveToken())
    }

    @Test
    fun `an empty credential store yields nothing`() {
        assertNull(adapter(storedKey = null, credential = "   ").resolveToken())
        assertNull(adapter(storedKey = null).resolveToken())
    }

    @Test
    fun `a failing credential store does not break the lookup`() {
        whenever(aiConfigRepo.findAiConfig()) doReturn AiConfig(claudeCodeApiKey = null)
        val lookup =
            ClaudeCodeTokenLookupPortAdapter(
                aiConfigRepo,
                ClaudeCodeTestSupport.settings(),
                ClaudeCodeCredentialAccessPort { throw IllegalStateException("keychain locked") },
            )

        assertNull(lookup.resolveToken())
    }

    @Test
    fun `a whitespace credential from the store is trimmed away`() {
        assertEquals("store-token", adapter(storedKey = null, credential = "  store-token  ").resolveToken())
    }
}
