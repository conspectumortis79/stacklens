package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.application.port.out.MrSuggestionPort
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.net.URI

class GithubPrSuggestionAdapter(
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 120_000,
) {
    private val log = LoggerFactory.getLogger(GithubPrSuggestionAdapter::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GhCommentResponse(
        @JsonProperty("html_url") val htmlUrl: String? = null,
    )

    fun post(
        request: MrSuggestionPort.PostRequest,
        uri: URI,
        apiBaseUrl: String,
    ): String {
        val parts = uri.path.trimStart('/').split("/")
        require(parts.size >= 4 && parts[2] == "pull") {
            "Cannot parse GitHub PR URL: ${request.mrUrl}"
        }
        val owner = parts[0]
        val repo = parts[1]
        val prNumber =
            parts[3].toLongOrNull()
                ?: error("Cannot parse PR number from URL: ${request.mrUrl}")

        val token = resolveGithubToken(request)
        val client = buildClient(token, apiBaseUrl)

        val requestBody =
            mapOf(
                "body" to suggestionBody(request.suggestedCode),
                "path" to request.filePath,
                "line" to request.lineNumber,
                "side" to "RIGHT",
                "commit_id" to request.diffHeadSha,
            )

        val response =
            client
                .post()
                .uri("/repos/$owner/$repo/pulls/$prNumber/comments")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<GhCommentResponse>()
                .block()

        val htmlUrl = response?.htmlUrl
        log.info("[MrSuggestion] GitHub suggestion posted: {}", htmlUrl)
        return htmlUrl ?: "https://github.com/$owner/$repo/pull/$prNumber"
    }

    private fun resolveGithubToken(request: MrSuggestionPort.PostRequest): String? {
        val configs = request.githubConfigs
        val requestedId = request.gitConfigId
        if (requestedId == null) return configs.firstOrNull()?.accessToken
        val id: Long = requestedId
        return configs.firstOrNull { it.id == id }?.accessToken
    }

    private fun buildClient(
        token: String?,
        apiBaseUrl: String,
    ): WebClient {
        val httpClient =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected { conn -> conn.addHandlerLast(ReadTimeoutHandler(readTimeoutMs / 1000)) }
        val builder =
            WebClient
                .builder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(apiBaseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .codecs { it.defaultCodecs().maxInMemorySize(4 * 1024 * 1024) }
        if (!token.isNullOrBlank()) {
            builder.defaultHeader("Authorization", "Bearer $token")
        }
        return builder.build()
    }
}
