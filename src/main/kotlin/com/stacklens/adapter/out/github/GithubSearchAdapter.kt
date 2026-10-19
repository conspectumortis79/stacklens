package com.stacklens.adapter.out.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.application.port.out.search.GithubSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.SearchResult
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

class
GithubSearchAdapter(
    private val webClientBuilder: WebClient.Builder,
    private val defaultApiBase: String = "https://api.github.com",
) : GithubSearchPort {
    private val log = LoggerFactory.getLogger(GithubSearchAdapter::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TextMatch(
        val fragment: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CodeItem(
        val path: String = "",
        @JsonProperty("html_url") val htmlUrl: String = "",
        @JsonProperty("text_matches") val textMatches: List<TextMatch> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CodeSearchResponse(
        @JsonProperty("total_count") val totalCount: Int = 0,
        val items: List<CodeItem> = emptyList(),
    )

    override fun searchKeyword(
        config: GithubRepoConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        if (!config.active) return emptyList()

        val baseUrl = (config.apiBase?.takeIf { it.isNotBlank() } ?: defaultApiBase).trimEnd('/')

        val httpClient =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .doOnConnected { conn ->
                    conn.addHandlerLast(ReadTimeoutHandler(15, TimeUnit.SECONDS))
                }

        val clientBuilder =
            webClientBuilder
                .clone()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github.text-match+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")

        if (!config.accessToken.isNullOrBlank()) {
            clientBuilder.defaultHeader("Authorization", "Bearer ${config.accessToken}")
        }

        val client = clientBuilder.build()

        val q = URLEncoder.encode("$keyword repo:${config.owner}/${config.repo}", StandardCharsets.UTF_8)

        val resp =
            client
                .get()
                .uri("/search/code?q=$q")
                .retrieve()
                .bodyToMono<CodeSearchResponse>()
                .timeout(Duration.ofSeconds(15))
                .onErrorResume { e ->
                    log.warn("[GitHub][Session {}] {}/{}: {}", sessionId, config.owner, config.repo, e.message)
                    Mono.just(CodeSearchResponse())
                }.block() ?: CodeSearchResponse()

        return resp.items.map { item ->
            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.GITHUB,
                resultType = ResultType.FILE,
                configName = config.name,
                repoName = "${config.owner}/${config.repo}",
                repoUrl = item.htmlUrl,
                filePath = item.path,
                branch = config.branch,
                codeSnippet =
                    item.textMatches
                        .firstOrNull()
                        ?.fragment
                        ?.take(500),
            )
        }
    }
}
