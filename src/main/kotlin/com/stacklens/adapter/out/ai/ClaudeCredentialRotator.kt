package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.util.exceptionDiagnostic
import org.slf4j.LoggerFactory

class ClaudeCredentialRotator(
    private val tokenLookup: ClaudeCodeTokenLookupPort,
    private val transport: ClaudeHttpTransport,
) {
    private val log = LoggerFactory.getLogger(ClaudeCredentialRotator::class.java)

    @Volatile
    private var preferredToken: String? = null

    fun sendWithCredentialRotation(
        baseUrl: String,
        requestBody: String,
        timeoutSeconds: Long,
        isCancelled: (() -> Boolean)?,
    ): String {
        var attempted = 0
        var lastReject: ClaudeCodeRejectException? = null
        for (token in tokenCandidates()) {
            attempted++
            try {
                val body = transport.sendWithAuthFallback(baseUrl, token, requestBody, timeoutSeconds, isCancelled)
                preferredToken = token
                return body
            } catch (reject: ClaudeCodeRejectException) {
                if (!ClaudeCodeRejections.isCredentialFault(reject.status, reject.responseBody)) throw reject
                lastReject = reject
                log.warn(
                    "ClaudeCode credential {} was rejected with HTTP {} ({}) — trying the next stored credential",
                    transport.maskToken(token),
                    reject.status,
                    reject.responseBody.take(160),
                )
            }
        }
        if (attempted == 0) error("No Claude Code API key set.")
        val reject = lastReject!!
        if (ClaudeCodeRejections.isBilling(reject.status, reject.responseBody)) {
            throw StackLensFailureException(
                StackLensFailure.AiCreditsExhausted(attempted, reject.responseBody.take(ERROR_DETAIL_LIMIT)),
                reject,
            )
        }
        throw reject
    }

    fun tokenCandidates(): Sequence<String> {
        val sources: List<() -> String?> =
            listOf(
                { preferredToken },
                { runCatching { tokenLookup.resolveToken() }.getOrNull() },
            ) +
                tokenLookup
                    .tokenCandidateSources()
                    .map { source ->
                        { runCatching { source() }.onFailure { e -> log.warn("ClaudeCode credential source failed: {}", exceptionDiagnostic(e)) }.getOrNull() }
                    }
        return sequence {
            for (source in sources) {
                val token = source()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                yield(token)
            }
        }.distinct()
    }

    private companion object {
        const val ERROR_DETAIL_LIMIT: Int = 200
    }
}
