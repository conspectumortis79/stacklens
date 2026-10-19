package com.stacklens.adapter.out.confluence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.shared.confluenceCloudPageUrl
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration

open class ConfluenceClient(
    @Value("\${webclient.connect-timeout:30000}") private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.confluence-read-timeout:30000}") private val readTimeoutMs: Int = 30_000,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(connectTimeoutMs, readTimeoutMs),
) : ConfluenceSearchPort {
    private val log = LoggerFactory.getLogger(ConfluenceClient::class.java)

    internal fun <T> Mono<T>.blockWithTimeout(label: String = ""): T? =
        try {
            this
                .timeout(Duration.ofMillis(readTimeoutMs.toLong()))
                .onErrorResume { e ->
                    log.warn("[ConfluenceClient] {} failed: {}", label, e)
                    Mono.empty()
                }.block()
        } catch (e: Exception) {
            log.warn("[ConfluenceClient] {} aborted: {}", label, e.message)
            null
        }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ContentSearchResponse(
        val results: List<ContentItem> = emptyList(),
        val start: Int = 0,
        val limit: Int = 25,
        val size: Int = 0,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ContentItem(
        val id: String = "",
        val type: String = "page",
        val title: String = "",
        val space: SpaceRef? = null,
        @JsonProperty("_links") val links: LinkMap? = null,
        val body: BodyMap? = null,
        val excerpt: String? = null,
        val children: ChildrenMap? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SpaceRef(
        val key: String = "",
        val name: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LinkMap(
        val webui: String? = null,
        val base: String? = null,
        val download: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BodyMap(
        val view: BodyContent? = null,
        val storage: BodyContent? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChildrenMap(
        val attachment: AttachmentContainer? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AttachmentContainer(
        val results: List<AttachmentItem> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AttachmentItem(
        val id: String = "",
        val title: String = "",
        @JsonProperty("_links") val links: LinkMap? = null,
        val metadata: AttachmentMeta? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AttachmentMeta(
        val mediaType: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BodyContent(
        val value: String? = null,
    )

    override fun searchKeyword(
        config: ConfluenceConfig,
        keyword: String,
        websiteFilters: List<String>,
        sessionId: Long,
        offset: Int,
    ): List<SearchResult> {
        val client = buildClient(config)
        val results = mutableListOf<SearchResult>()
        val activeRoots = websiteFilters.map { it.trim() }.filter { it.isNotBlank() }

        val spaceFilter =
            if (config.spaceKeyList().isNotEmpty()) {
                " AND space IN (${config.spaceKeyList().joinToString(",") { "\"$it\"" }})"
            } else {
                ""
            }
        val esc = keyword.replace("\"", "\\\"")
        val cql = "text ~ \"$esc\"$spaceFilter"

        log.info("[Confluence:{}] Searching '{}' with CQL: {} (timeout={}ms)", config.name, keyword, cql, readTimeoutMs)

        var start = offset
        val limit = 25

        val expandFields =
            if (config.cloudMode) {
                "space"
            } else {
                "space,body.view,body.storage,children.attachment,metadata.labels"
            }

        val response =
            try {
                client
                    .get()
                    .uri { ub ->
                        ub
                            .path(contentSearchPath(config))
                            .queryParam("cql", cql)
                            .queryParam("start", start)
                            .queryParam("limit", limit)
                            .queryParam("expand", expandFields)
                            .build()
                    }.retrieve()
                    .bodyToMono<ContentSearchResponse>()
                    .onErrorResume { ex ->
                        val body =
                            (ex as? org.springframework.web.reactive.function.client.WebClientResponseException)
                                ?.responseBodyAsString
                                ?.take(500) ?: ""
                        log.warn("[Confluence:{}] Search error (start={}): {} | body: {}", config.name, start, ex.message, body)
                        Mono.just(ContentSearchResponse())
                    }.blockWithTimeout("searchKeyword(${config.name}, start=$start)")
                    ?: ContentSearchResponse()
            } catch (e: Exception) {
                log.warn("[Confluence:{}] Search error (start={}): {}", config.name, start, e.message)
                ContentSearchResponse()
            }

        for (item in response.results) {
            val pageUrl = buildPageUrl(config, item)
            if (!matchesSearchRoot(config.searchRootUrl, pageUrl)) continue
            if (!matchesAnySearchRoot(activeRoots, pageUrl)) continue

            val excerpt = extractExcerpt(item, keyword)

            val rType =
                if (item.type == "blogpost") {
                    ResultType.CONFLUENCE_BLOGPOST
                } else {
                    ResultType.CONFLUENCE_PAGE
                }

            results +=
                SearchResult(
                    sessionId = sessionId,
                    keyword = keyword,
                    sourceType = SourceType.CONFLUENCE,
                    resultType = rType,
                    configName = config.name,
                    repoName = item.space?.let { it.name.ifBlank { it.key } } ?: "Unknown Space",
                    filePath = item.title,
                    repoUrl = pageUrl,
                    codeSnippet = excerpt,
                )

            results += findDrawIoAttachmentResults(client, config, item, keyword, sessionId, pageUrl)
        }

        return results
    }

    internal fun buildClient(config: ConfluenceConfig): WebClient =
        httpFactory.buildBasicAuth(
            baseUrl = config.baseUrl,
            username = config.username,
            password = config.apiToken,
        )

    internal fun buildPageUrl(
        config: ConfluenceConfig,
        item: ContentItem,
    ): String {
        val webuiPath = item.links?.webui ?: ""
        if (webuiPath.startsWith("http")) return webuiPath

        if (config.cloudMode) {
            val spaceKey = item.space?.key
            val pageId = item.id
            if (!spaceKey.isNullOrBlank() && pageId.isNotBlank()) {
                return confluenceCloudPageUrl(config.baseUrl, spaceKey, pageId)
            }
        }

        return "${config.baseUrl.trimEnd('/')}$webuiPath"
    }

    internal fun matchesSearchRoot(
        searchRootUrl: String?,
        pageUrl: String,
    ): Boolean {
        if (searchRootUrl.isNullOrBlank()) return true
        val root = searchRootUrl.trim().trimEnd('/').lowercase()
        val page = pageUrl.trim().trimEnd('/').lowercase()
        return page == root || page.startsWith("$root/")
    }

    internal fun matchesAnySearchRoot(
        searchRoots: List<String>,
        pageUrl: String,
    ): Boolean {
        if (searchRoots.isEmpty()) return true
        val page = pageUrl.trim().trimEnd('/').lowercase()
        return searchRoots.any { rootRaw ->
            val root = rootRaw.trim().trimEnd('/').lowercase()
            page == root || page.startsWith("$root/")
        }
    }

    internal fun extractExcerpt(
        item: ContentItem,
        keyword: String,
    ): String {
        val raw =
            item.body?.view?.value
                ?: item.body?.storage?.value
                ?: item.excerpt
                ?: ""

        val plain =
            raw
                .replace(HTML_TAG, " ")
                .replace(WHITESPACE, " ")
                .trim()

        if (plain.isBlank()) return "(No excerpt available)"

        val idx = plain.indexOf(keyword, ignoreCase = true)
        return if (idx >= 0) {
            val from = maxOf(0, idx - 150)
            val to = minOf(plain.length, idx + 300)
            (if (from > 0) "…" else "") + plain.substring(from, to) + (if (to < plain.length) "…" else "")
        } else {
            val preview = plain.take(300)
            (if (plain.length > 300) "$preview…" else preview)
        }
    }

    internal fun findDrawIoAttachmentResults(
        client: WebClient,
        config: ConfluenceConfig,
        item: ContentItem,
        keyword: String,
        sessionId: Long,
        pageUrl: String,
    ): List<SearchResult> {
        if (config.cloudMode) return emptyList()

        val attachments =
            item.children
                ?.attachment
                ?.results
                ?.takeIf { it.isNotEmpty() }
                ?: emptyList()
        val baseUrl = config.baseUrl.trimEnd('/')
        val results = mutableListOf<SearchResult>()

        for (a in attachments) {
            val lowerName = a.title.lowercase()
            val mediaType = a.metadata?.mediaType?.lowercase() ?: ""
            val looksLikeDrawIo =
                lowerName.endsWith(".drawio") ||
                    lowerName.endsWith(".drawio.xml") ||
                    mediaType.contains("drawio") ||
                    mediaType.contains("vnd.jgraph")
            if (!looksLikeDrawIo) continue

            val filenameHit = lowerName.contains(keyword.lowercase())
            val downloadPath = a.links?.download ?: a.links?.webui ?: a.links?.base
            val contentHit =
                if (filenameHit) {
                    false
                } else if (!downloadPath.isNullOrBlank()) {
                    val attachmentText = fetchAttachmentText(client, baseUrl, downloadPath)
                    attachmentText.contains(keyword, ignoreCase = true)
                } else {
                    false
                }

            if (!filenameHit && !contentHit) continue

            val why =
                if (contentHit) {
                    "draw.io diagram contains '$keyword'"
                } else {
                    "draw.io filename contains '$keyword'"
                }

            results +=
                SearchResult(
                    sessionId = sessionId,
                    keyword = keyword,
                    sourceType = SourceType.CONFLUENCE,
                    resultType = ResultType.CONFLUENCE_PAGE,
                    configName = config.name,
                    repoName = item.space?.let { it.name.ifBlank { it.key } } ?: "Unknown Space",
                    filePath = "${item.title} / ${a.title}",
                    repoUrl = pageUrl,
                    codeSnippet = why,
                )
        }

        return results
    }

    internal fun fetchAttachmentText(
        client: WebClient,
        baseUrl: String,
        pathOrUrl: String,
    ): String {
        val uri = if (pathOrUrl.startsWith("http")) pathOrUrl else "$baseUrl$pathOrUrl"
        return client
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono<String>()
            .onErrorResume { Mono.just("") }
            .blockWithTimeout("fetchAttachmentText($uri)")
            ?: ""
    }

    private companion object {
        private val HTML_TAG = Regex("<[^>]+>")
        private val WHITESPACE = Regex("\\s+")
    }
}
