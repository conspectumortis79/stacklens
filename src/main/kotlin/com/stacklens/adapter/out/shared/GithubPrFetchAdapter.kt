package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient

class GithubPrFetchAdapter(
    private val diffTextBuilder: MrDiffTextBuilder,
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 120_000,
) {
    private val log = LoggerFactory.getLogger(GithubPrFetchAdapter::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GhPrResponse(
        val title: String = "",
        val body: String? = null,
        val head: GhRef? = null,
        val base: GhRef? = null,
        val user: GhUser? = null,
        val state: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GhRef(
        val ref: String = "",
        val sha: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GhUser(
        val login: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GhFile(
        val filename: String = "",
        val patch: String? = null,
        val status: String = "",
    )

    fun fetch(
        mrUrl: String,
        path: String,
        githubConfigs: List<GithubRepoConfig>,
        apiBaseUrl: String,
    ): MrFetchPort.MrData? {
        val parts = path.trimStart('/').split("/")
        if (parts.size < 4 || parts[2] != "pull") {
            log.warn("[MrFetch] Invalid GitHub PR URL: {}", mrUrl)
            return null
        }
        val owner = parts[0]
        val repo = parts[1]
        val prNumber = parts[3].toLongOrNull() ?: return null.also { log.warn("[MrFetch] No PR number in URL: {}", mrUrl) }

        val matchingConfig =
            githubConfigs.firstOrNull {
                it.owner.equals(owner, ignoreCase = true) && it.repo.equals(repo, ignoreCase = true)
            }
        if (matchingConfig == null && githubConfigs.size > 1) {
            log.warn(
                "[MrFetch] no GitHub configuration owns {}/{} – calling without a token instead of using an unrelated configuration",
                owner,
                repo,
            )
        }
        val token = matchingConfig?.accessToken ?: githubConfigs.singleOrNull()?.accessToken

        val client = buildClient(token, apiBaseUrl)

        val pr =
            client
                .get()
                .uri("/repos/$owner/$repo/pulls/$prNumber")
                .retrieve()
                .bodyToMono<GhPrResponse>()
                .block() ?: return null

        val files =
            client
                .get()
                .uri("/repos/$owner/$repo/pulls/$prNumber/files?per_page=100")
                .retrieve()
                .bodyToMono<List<GhFile>>()
                .block() ?: emptyList()

        val changedFiles = files.map { "${it.status.uppercase()}: ${it.filename}" }
        val diffText = diffTextBuilder.build(files.map { it.filename to (it.patch ?: "") })

        return MrFetchPort.MrData(
            mrUrl = mrUrl,
            platform = "GitHub",
            title = pr.title,
            description = pr.body.orEmpty(),
            sourceBranch = pr.head?.ref.orEmpty(),
            targetBranch = pr.base?.ref.orEmpty(),
            authorName = pr.user?.login.orEmpty(),
            state = pr.state,
            diffText = diffText,
            changedFiles = changedFiles,
            diffBaseSha = pr.base?.sha.orEmpty(),
            diffStartSha = pr.base?.sha.orEmpty(),
            diffHeadSha = pr.head?.sha.orEmpty(),
        )
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
                .codecs { it.defaultCodecs().maxInMemorySize(20 * 1024 * 1024) }

        if (!token.isNullOrBlank()) {
            builder.defaultHeader("Authorization", "Bearer $token")
        }

        return builder.build()
    }
}
