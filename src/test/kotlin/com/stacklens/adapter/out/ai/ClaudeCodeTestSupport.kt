package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.ClaudeCodeCredentialStorePort
import com.stacklens.application.port.out.ai.ClaudeCodeOAuthRefreshPort
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.config.ClaudeCodeSettingsPort
import com.stacklens.domain.model.ClaudeCodeCredential

object ClaudeCodeTestSupport {
    const val DEFAULT_MODEL: String = "claude-sonnet-4-5"

    fun tokenLookup(token: String?): ClaudeCodeTokenLookupPort = ClaudeCodeTokenLookupPort { token }

    fun lookupOfStoredKey(aiConfigRepo: AiConfigPort): ClaudeCodeTokenLookupPort = ClaudeCodeTokenLookupPort { aiConfigRepo.findAiConfig()?.claudeCodeApiKey }

    fun settings(
        defaultModel: String = DEFAULT_MODEL,
        apiKey: String? = null,
        oauthToken: String? = null,
        oauthTokenUrl: String = "https://oauth.test/v1/oauth/token",
        oauthClientId: String? = "client-test",
    ): ClaudeCodeSettingsPort =
        object : ClaudeCodeSettingsPort {
            override fun defaultModel(): String = defaultModel

            override fun configuredApiKey(): String? = apiKey

            override fun configuredOauthToken(): String? = oauthToken

            override fun oauthTokenUrl(): String = oauthTokenUrl

            override fun oauthClientId(): String? = oauthClientId
        }
}

class RecordingCredentialStore(
    override val sourceLabel: String = "recording-store",
    private var credential: ClaudeCodeCredential? = null,
    private var readFailure: Throwable? = null,
) : ClaudeCodeCredentialStorePort {
    val written: MutableList<ClaudeCodeCredential> = mutableListOf()

    var readCount: Int = 0
        private set

    fun seed(credential: ClaudeCodeCredential?): RecordingCredentialStore = apply { this.credential = credential }

    fun failRead(failure: Throwable?): RecordingCredentialStore = apply { readFailure = failure }

    override fun read(): ClaudeCodeCredential? {
        readCount++
        readFailure?.let { throw it }
        return credential
    }

    override fun write(credential: ClaudeCodeCredential) {
        written += credential
        this.credential = credential
    }
}

class StubOAuthRefreshPort(
    private var result: ClaudeCodeCredential? = null,
    private var failure: Throwable? = null,
) : ClaudeCodeOAuthRefreshPort {
    val requestedCredentials: MutableList<ClaudeCodeCredential> = mutableListOf()

    var calls: Int = 0
        private set

    fun respondWith(credential: ClaudeCodeCredential?): StubOAuthRefreshPort = apply { result = credential }

    fun failWith(failure: Throwable?): StubOAuthRefreshPort = apply { this.failure = failure }

    override fun refresh(credential: ClaudeCodeCredential): ClaudeCodeCredential? {
        calls++
        requestedCredentials += credential
        failure?.let { throw it }
        return result
    }
}
