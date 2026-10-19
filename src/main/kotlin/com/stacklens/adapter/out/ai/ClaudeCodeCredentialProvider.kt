package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.ClaudeCodeCredentialAccessPort
import com.stacklens.application.port.out.ai.ClaudeCodeCredentialStorePort
import com.stacklens.application.port.out.ai.ClaudeCodeOAuthRefreshPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.model.ClaudeCodeCredential
import com.stacklens.domain.util.exceptionDiagnostic
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class ClaudeCodeCredentialProvider(
    private val stores: List<ClaudeCodeCredentialStorePort>,
    private val refreshPort: ClaudeCodeOAuthRefreshPort,
    private val clock: ClockPort,
    private val expirySkewMillis: Long = DEFAULT_EXPIRY_SKEW_MILLIS,
    private val refreshCooldownMillis: Long = DEFAULT_REFRESH_COOLDOWN_MILLIS,
) : ClaudeCodeCredentialAccessPort {
    private val log = LoggerFactory.getLogger(ClaudeCodeCredentialProvider::class.java)

    private val refreshBlockedUntilMillis = ConcurrentHashMap<String, Long>()

    override fun accessToken(): String? {
        val now = clock.epochMillis()
        for (store in stores) {
            val credential = readQuietly(store) ?: continue
            if (!credential.needsRefresh(now, expirySkewMillis)) return credential.accessToken
            refreshIfNeeded(store, credential, now)?.let { return it }
        }
        return null
    }

    private fun readQuietly(store: ClaudeCodeCredentialStorePort): ClaudeCodeCredential? =
        runCatching { store.read() }
            .onFailure { e -> log.warn("Claude Code credential store {} failed to read: {}", store.sourceLabel, exceptionDiagnostic(e)) }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() }

    private fun refreshIfNeeded(
        store: ClaudeCodeCredentialStorePort,
        credential: ClaudeCodeCredential,
        now: Long,
    ): String? {
        if (!credential.isRefreshable) {
            log.debug("Claude Code credential store {} holds an expired token without a refresh token", store.sourceLabel)
            return null
        }
        if (isCoolingDown(store.sourceLabel, now)) return null
        val refreshed =
            runCatching { refreshPort.refresh(credential) }
                .onFailure { e -> log.warn("Claude Code OAuth refresh threw for store {}: {}", store.sourceLabel, exceptionDiagnostic(e)) }
                .getOrNull()
                ?.takeIf { it.accessToken.isNotBlank() }
        if (refreshed == null) {
            refreshBlockedUntilMillis[store.sourceLabel] = now + refreshCooldownMillis
            log.warn("Claude Code login token from {} is expired and could not be refreshed – falling through to the next source", store.sourceLabel)
            return null
        }
        refreshBlockedUntilMillis.remove(store.sourceLabel)
        store.write(refreshed)
        log.info("Claude Code login token from {} refreshed", store.sourceLabel)
        return refreshed.accessToken
    }

    private fun isCoolingDown(
        sourceLabel: String,
        now: Long,
    ): Boolean = refreshBlockedUntilMillis[sourceLabel]?.let { blockedUntil -> now < blockedUntil } ?: false

    companion object {
        const val DEFAULT_EXPIRY_SKEW_MILLIS: Long = 300_000L

        const val DEFAULT_REFRESH_COOLDOWN_MILLIS: Long = 300_000L
    }
}
