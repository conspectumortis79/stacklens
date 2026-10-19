package com.stacklens.adapter.out.ai

import com.stacklens.domain.model.ClaudeCodeTokenKind
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ClaudeHttpTransport(
    private val httpClient: HttpClient,
    private val httpSendRetry: HttpSendRetry,
    private val aiHttpClientExecutor: AiHttpClientExecutor,
    private val anthropicVersion: String,
) {
    private val log = LoggerFactory.getLogger(ClaudeHttpTransport::class.java)

    fun sendWithAuthFallback(
        baseUrl: String,
        token: String,
        requestBody: String,
        timeoutOverride: Long,
        isCancelled: (() -> Boolean)?,
    ): String {
        if (ClaudeCodeTokenKind.isOauthAccessToken(token)) {
            val resp = send(baseUrl, token, requestBody, timeoutOverride, useBearer = true, isCancelled = isCancelled)
            if (resp.statusCode() in 200..299) return resp.body()
            throw ClaudeCodeRejectException(resp.statusCode(), resp.body())
        }
        val first = send(baseUrl, token, requestBody, timeoutOverride, useBearer = false, isCancelled = isCancelled)
        if (first.statusCode() in 200..299) return first.body()
        if (first.statusCode() != 401) {
            throw ClaudeCodeRejectException(first.statusCode(), first.body())
        }
        log.warn(
            "ClaudeCode x-api-key was rejected (HTTP 401), retrying with Authorization: Bearer for token={}",
            maskToken(token),
        )
        val retry = send(baseUrl, token, requestBody, timeoutOverride, useBearer = true, isCancelled = isCancelled)
        if (retry.statusCode() in 200..299) return retry.body()
        throw ClaudeCodeRejectException(retry.statusCode(), retry.body())
    }

    fun maskToken(token: String): String {
        if (token.length <= 8) return "***"
        val prefix =
            when {
                token.startsWith("sk-ant-") -> "sk-ant-"
                token.startsWith("sk-") -> "sk-"
                else -> token.take(4)
            }
        return "$prefix...${token.takeLast(4)}"
    }

    private fun send(
        baseUrl: String,
        token: String,
        requestBody: String,
        timeoutOverride: Long,
        useBearer: Boolean,
        isCancelled: (() -> Boolean)?,
    ): HttpResponse<String> {
        val builder =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${baseUrl.trimEnd('/')}/v1/messages"))
                .timeout(Duration.ofSeconds(timeoutOverride))
                .header("anthropic-version", anthropicVersion)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        if (useBearer) {
            builder.header("Authorization", "Bearer $token")
        } else {
            builder.header("x-api-key", token)
        }
        val request = builder.build()
        return httpSendRetry.sendWithRetry(opName = "ClaudeCode.messages") {
            aiHttpClientExecutor.sendWithTimeout(
                httpClient,
                request,
                HttpResponse.BodyHandlers.ofString(),
                timeoutOverride,
                isCancelled,
            )
        }
    }
}
