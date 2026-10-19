package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.domain.model.GitlabConfig
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.net.URI

class GitlabMrSuggestionAdapter(
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 120_000,
) {
    private val log = LoggerFactory.getLogger(GitlabMrSuggestionAdapter::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GlDiscussionResponse(
        val id: String = "",
        @JsonProperty("notes") val notes: List<GlNote> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GlNote(
        @JsonProperty("web_url") val webUrl: String? = null,
    )

    fun post(
        request: MrSuggestionPort.PostRequest,
        uri: URI,
    ): String {
        val path = uri.path?.takeIf { it.isNotBlank() } ?: error("No path in GitLab URL")
        val failure = "Cannot parse MR IID from URL: ${request.mrUrl}"
        val match = Regex("""/merge_requests/(\d+)(?:/.*)?/?$""").find(path) ?: error(failure)
        val mrIid = match.groupValues[1].toLongOrNull() ?: error(failure)
        val projectPath = path.substringBefore("/-/merge_requests").trimEnd('/').trimStart('/')

        val config = resolveGitlabConfig(request, uri)

        val client = buildClient(config)

        val body = suggestionBody(request.suggestedCode)

        val lineCode = "${request.diffBaseSha}_0_${request.lineNumber}"
        val requestBody =
            mapOf(
                "body" to body,
                "position" to
                    mapOf(
                        "position_type" to "text",
                        "base_sha" to request.diffBaseSha,
                        "start_sha" to request.diffStartSha.ifBlank { request.diffBaseSha },
                        "head_sha" to request.diffHeadSha,
                        "old_path" to request.filePath,
                        "new_path" to request.filePath,
                        "new_line" to request.lineNumber,
                        "line_code" to lineCode,
                    ),
            )

        val response =
            client
                .post()
                .uri("/api/v4/projects/{project}/merge_requests/{iid}/discussions", projectPath, mrIid)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono<GlDiscussionResponse>()
                .block()

        val webUrl = response?.notes?.firstOrNull()?.webUrl
        log.info("[MrSuggestion] GitLab suggestion posted: {}", webUrl)
        return webUrl ?: "${config.baseUrl.trimEnd('/')}/$projectPath/-/merge_requests/$mrIid"
    }

    private fun resolveGitlabConfig(
        request: MrSuggestionPort.PostRequest,
        uri: URI,
    ): GitlabConfig {
        val baseUrl = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
        val resolved =
            request.gitlabConfigs.firstOrNull { it.id == request.gitConfigId }
                ?: request.gitlabConfigs.firstOrNull { it.baseUrl.trimEnd('/') == baseUrl.trimEnd('/') }
                ?: request.gitlabConfigs.singleOrNull()
        return resolved
            ?: error(
                "No GitLab config available for ${request.mrUrl} – " +
                    "${request.gitlabConfigs.size} configs are registered and none matches the requested id or the URL host",
            )
    }

    private fun buildClient(config: GitlabConfig): WebClient {
        val httpClient =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected { conn -> conn.addHandlerLast(ReadTimeoutHandler(readTimeoutMs / 1000)) }
        return WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(config.baseUrl.trimEnd('/'))
            .defaultHeader("PRIVATE-TOKEN", config.accessToken)
            .defaultHeader("Accept", "application/json")
            .codecs { it.defaultCodecs().maxInMemorySize(4 * 1024 * 1024) }
            .build()
    }
}
