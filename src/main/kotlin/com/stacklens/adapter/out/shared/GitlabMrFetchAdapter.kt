package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GitlabConfig
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.net.URI

class GitlabMrFetchAdapter(
    private val diffTextBuilder: MrDiffTextBuilder,
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 120_000,
) {
    private val log = LoggerFactory.getLogger(GitlabMrFetchAdapter::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GlMrResponse(
        val title: String = "",
        val description: String? = null,
        @JsonProperty("source_branch") val sourceBranch: String = "",
        @JsonProperty("target_branch") val targetBranch: String = "",
        @JsonProperty("author") val author: GlAuthor? = null,
        val state: String = "",
        @JsonProperty("diff_refs") val diffRefs: GlDiffRefs? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GlDiffRefs(
        @JsonProperty("base_sha") val baseSha: String = "",
        @JsonProperty("start_sha") val startSha: String = "",
        @JsonProperty("head_sha") val headSha: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GlAuthor(
        val name: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GlDiffFile(
        @JsonProperty("old_path") val oldPath: String = "",
        @JsonProperty("new_path") val newPath: String = "",
        val diff: String = "",
        val deleted: Boolean = false,
        @JsonProperty("new_file") val newFile: Boolean = false,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class GlChangesResponse(
        val changes: List<GlDiffFile> = emptyList(),
        @JsonProperty("diff_refs") val diffRefs: GlDiffRefs? = null,
    )

    fun fetch(
        mrUrl: String,
        uri: URI,
        host: String,
        path: String,
        gitlabConfigs: List<GitlabConfig>,
    ): MrFetchPort.MrData? {
        val mrIidStr = path.substringAfter("/-/merge_requests/").substringBefore("/").trim()
        val mrIid = mrIidStr.toLongOrNull() ?: return null.also { log.warn("[MrFetch] No MR IID in URL: {}", mrUrl) }

        val projectPath = path.substringBefore("/-/merge_requests").trimStart('/')

        val baseUrl = "${uri.scheme}://$host${if (uri.port > 0) ":${uri.port}" else ""}"
        val baseUrlAlts =
            buildList {
                add(baseUrl.trimEnd('/'))
                if (host == "localhost") add(baseUrl.replace("://localhost", "://host.docker.internal").trimEnd('/'))
                if (host == "host.docker.internal") add(baseUrl.replace("://host.docker.internal", "://localhost").trimEnd('/'))
            }
        val config =
            gitlabConfigs.firstOrNull { it.resolvedUrls().first.trimEnd('/') in baseUrlAlts }
                ?: gitlabConfigs.firstOrNull { host.contains(extractDomain(it.baseUrl)) }
                ?: return null.also { log.warn("[MrFetch] No matching GitLab configuration for {}", baseUrl) }

        val client = buildClient(config)

        val mr =
            client
                .get()
                .uri("/api/v4/projects/{projectPath}/merge_requests/{mrIid}", projectPath, mrIid)
                .retrieve()
                .bodyToMono<GlMrResponse>()
                .block() ?: return null

        val changesResp =
            client
                .get()
                .uri("/api/v4/projects/{projectPath}/merge_requests/{mrIid}/changes", projectPath, mrIid)
                .retrieve()
                .bodyToMono<GlChangesResponse>()
                .block()

        val diffs = changesResp?.changes ?: emptyList()
        val changedFiles = diffs.map { if (it.deleted) "DELETED: ${it.oldPath}" else it.newPath }
        val diffText = diffTextBuilder.build(diffs.map { it.newPath to it.diff })

        val diffRefs = changesResp?.diffRefs

        return MrFetchPort.MrData(
            mrUrl = mrUrl,
            platform = "GitLab",
            title = mr.title,
            description = mr.description.orEmpty(),
            sourceBranch = mr.sourceBranch,
            targetBranch = mr.targetBranch,
            authorName = mr.author?.name.orEmpty(),
            state = mr.state,
            diffText = diffText,
            changedFiles = changedFiles,
            diffBaseSha = diffRefs?.baseSha.orEmpty(),
            diffStartSha = diffRefs?.startSha.orEmpty(),
            diffHeadSha = diffRefs?.headSha.orEmpty(),
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
            .baseUrl(config.resolvedUrls().first)
            .defaultHeader("PRIVATE-TOKEN", config.accessToken)
            .defaultHeader("Accept", "application/json")
            .codecs { it.defaultCodecs().maxInMemorySize(20 * 1024 * 1024) }
            .build()
    }

    private fun extractDomain(url: String): String =
        try {
            URI.create(url).host?.lowercase() ?: url
        } catch (_: Exception) {
            url
        }
}
