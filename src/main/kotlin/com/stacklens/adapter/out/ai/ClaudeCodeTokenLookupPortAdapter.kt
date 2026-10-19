package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.ClaudeCodeCredentialAccessPort
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.config.ClaudeCodeSettingsPort
import com.stacklens.domain.util.exceptionDiagnostic
import org.slf4j.LoggerFactory

class ClaudeCodeTokenLookupPortAdapter(
    private val aiConfigRepo: AiConfigPort,
    private val settings: ClaudeCodeSettingsPort,
    private val credentialAccess: ClaudeCodeCredentialAccessPort,
) : ClaudeCodeTokenLookupPort {
    private val log = LoggerFactory.getLogger(ClaudeCodeTokenLookupPortAdapter::class.java)

    override fun resolveToken(): String? =
        tokenCandidateSources()
            .asSequence()
            .mapNotNull { source -> runCatching { source() }.getOrNull() }
            .firstOrNull()

    override fun tokenCandidateSources(): List<() -> String?> =
        listOf({
            storedToken()
        }, { tokenOrNull(settings.configuredOauthToken()) }, { tokenOrNull(settings.configuredApiKey()) }, { credentialToken() })

    private fun storedToken(): String? =
        aiConfigRepo
            .findAiConfig()
            ?.claudeCodeApiKey
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun tokenOrNull(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun credentialToken(): String? =
        runCatching { credentialAccess.accessToken()?.trim()?.takeIf { it.isNotEmpty() } }
            .onFailure { e -> log.warn("Claude Code credential lookup failed: {}", exceptionDiagnostic(e)) }
            .getOrNull()
}
