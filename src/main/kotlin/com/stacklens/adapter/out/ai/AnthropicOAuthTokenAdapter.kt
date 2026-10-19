package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.ClaudeCodeOAuthRefreshPort
import com.stacklens.application.port.out.config.ClaudeCodeSettingsPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.domain.model.ClaudeCodeCredential
import com.stacklens.domain.util.exceptionDiagnostic
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class AnthropicOAuthTokenAdapter(
    private val settings: ClaudeCodeSettingsPort,
    private val objectMapper: ObjectMapper,
    private val clock: ClockPort,
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build(),
) : ClaudeCodeOAuthRefreshPort {
    private val log = LoggerFactory.getLogger(AnthropicOAuthTokenAdapter::class.java)

    override fun refresh(credential: ClaudeCodeCredential): ClaudeCodeCredential? {
        val refreshToken = credential.refreshToken?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val clientId = (credential.clientId?.trim() ?: settings.oauthClientId()?.trim())?.takeIf { it.isNotEmpty() }
        if (clientId == null) {
            log.warn("Claude Code OAuth refresh skipped: no client id in the credential store and none configured")
            return null
        }
        val url = settings.oauthTokenUrl()
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(refreshToken, clientId, credential)))
                .build()
        return runCatching { send(request, url, credential) }
            .getOrElse { e ->
                if (e is InterruptedException) Thread.currentThread().interrupt()
                log.warn("Claude Code OAuth refresh request to {} failed: {}", url, exceptionDiagnostic(e))
                null
            }
    }

    internal fun buildRequestBody(
        refreshToken: String,
        clientId: String,
        credential: ClaudeCodeCredential,
    ): String {
        val body =
            linkedMapOf(
                "grant_type" to GRANT_TYPE,
                "refresh_token" to refreshToken,
                "client_id" to clientId,
            )
        if (credential.scopes.isNotEmpty()) body["scope"] = credential.scopes.joinToString(" ")
        return objectMapper.writeValueAsString(body)
    }

    private fun send(
        request: HttpRequest,
        url: String,
        credential: ClaudeCodeCredential,
    ): ClaudeCodeCredential? {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in SUCCESS_RANGE) {
            log.warn("Claude Code OAuth refresh rejected (HTTP {}): {}", response.statusCode(), response.body().take(MAX_LOG_CHARS))
            return null
        }
        return parseResponse(response.body(), credential)
            .also {
                if (it == null) log.warn("Claude Code OAuth refresh returned an unusable payload from {}", url)
            }
    }

    private fun parseResponse(
        body: String,
        credential: ClaudeCodeCredential,
    ): ClaudeCodeCredential? {
        val root: JsonNode =
            runCatching { objectMapper.readTree(body) }
                .getOrNull()
                ?.takeIf { it.isObject }
                ?: return null
        val accessToken =
            root
                .get(FIELD_ACCESS_TOKEN)
                ?.asText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return null
        return ClaudeCodeCredential(
            accessToken = accessToken,
            refreshToken =
                root
                    .get(FIELD_REFRESH_TOKEN)
                    ?.asText()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: credential.refreshToken,
            expiresAtMillis = expiresAtMillis(root, credential),
            scopes = scopes(root, credential),
            clientId = credential.clientId ?: settings.oauthClientId(),
        )
    }

    private fun expiresAtMillis(
        root: JsonNode,
        credential: ClaudeCodeCredential,
    ): Long? {
        val expiresIn = root.get(FIELD_EXPIRES_IN)
        if (expiresIn == null || !expiresIn.isNumber) return null
        return clock.epochMillis() + expiresIn.asLong() * MILLIS_PER_SECOND
    }

    private fun scopes(
        root: JsonNode,
        credential: ClaudeCodeCredential,
    ): List<String> {
        val scope = root.get(FIELD_SCOPE)?.asText()?.trim()
        if (scope.isNullOrEmpty()) return credential.scopes
        return scope.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        const val GRANT_TYPE: String = "refresh_token"

        private const val FIELD_ACCESS_TOKEN = "access_token"

        private const val FIELD_REFRESH_TOKEN = "refresh_token"

        private const val FIELD_EXPIRES_IN = "expires_in"

        private const val FIELD_SCOPE = "scope"

        private const val MAX_LOG_CHARS = 200

        private const val MILLIS_PER_SECOND = 1000L

        private const val CONNECT_TIMEOUT_SECONDS = 15L

        private const val DEFAULT_TIMEOUT_SECONDS = 30L

        private val SUCCESS_RANGE = 200..299
    }
}
