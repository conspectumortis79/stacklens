package com.stacklens.adapter.out.knowledge.confluence

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.knowledge.ConfluenceHierarchyPort
import com.stacklens.application.port.out.knowledge.ConfluencePageSnapshot
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.util.exceptionDiagnostic
import com.stacklens.shared.confluenceCloudPageUrl
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.bearerOrBasicAuthHeader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.util.ArrayDeque

@Component
class ConfluenceHierarchyAdapter(
    private val httpFactory: ReactiveHttpClientFactory,
    private val linearizer: ConfluenceHtmlLinearizer = ConfluenceHtmlLinearizer(),
    private val calendarMacroDetector: ConfluenceCalendarMacroDetector = ConfluenceCalendarMacroDetector(),
    private val calendarFetcher: ConfluenceCalendarFetcher = ConfluenceCalendarFetcher(httpFactory),
) : ConfluenceHierarchyPort {
    private val log = LoggerFactory.getLogger(ConfluenceHierarchyAdapter::class.java)

    override fun ensureHostMatches(
        config: ConfluenceConfig,
        rootPageUrl: String,
    ) {
        val baseHost = hostOf(config.baseUrl) ?: error("Confluence base URL is not a valid absolute URL: ${config.baseUrl}")
        val pageHost = hostOf(rootPageUrl) ?: error("Confluence page URL is not a valid absolute URL: $rootPageUrl")
        require(baseHost.equals(pageHost, ignoreCase = true)) {
            "Confluence page host '$pageHost' does not match the configured base URL host '$baseHost'. " +
                "Pick a page that lives on the configured Confluence instance."
        }
    }

    override fun walkHierarchy(
        config: ConfluenceConfig,
        rootPageUrl: String,
        includeChildren: Boolean,
        maxDepth: Int,
        maxPages: Int,
    ): Sequence<ConfluencePageSnapshot> =
        sequence {
            val baseUrl = config.baseUrl.trimEnd('/')
            val client =
                httpFactory
                    .builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Accept", "application/json")
                    .defaultHeader("Authorization", bearerOrBasicAuthHeader(config.username, config.apiToken))
                    .build()
            val wikiPath = if (baseUrl.contains("/wiki")) "" else "/wiki"
            val apiBase = "$baseUrl$wikiPath/rest/api"

            val rootId =
                extractPageId(config, rootPageUrl, apiBase, client)
                    ?: return@sequence
            val queue: ArrayDeque<PageCursor> = ArrayDeque()
            queue.add(PageCursor(id = rootId, depth = 0))
            val seen = HashSet<String>()
            var emitted = 0

            while (queue.isNotEmpty()) {
                if (emitted >= maxPages) {
                    log.warn("Confluence walk hit maxPages={}, stopping", maxPages)
                    break
                }
                val cursor = queue.removeFirst()
                if (!seen.add(cursor.id)) continue
                val page =
                    loadPage(client, apiBase, cursor.id, cursor.depth == 0)
                        ?: continue
                val bodyView = page.bodyView ?: ""
                val bodyStorage = page.bodyStorage ?: ""
                val raw = bodyView.ifBlank { bodyStorage }
                val calendarRefs =
                    (
                        calendarMacroDetector.detect(bodyView) +
                            calendarMacroDetector.detect(bodyStorage)
                    ).distinctBy { it.calendarId }
                val calendarEvents = fetchCalendarEvents(config, calendarRefs)
                val calendarTitles = calendarRefs.associate { it.calendarId to it.title }
                val linearized = linearizer.linearize(raw, calendarEvents, calendarTitles)
                yield(
                    ConfluencePageSnapshot(
                        pageId = page.id,
                        title = page.title.ifBlank { "(untitled)" },
                        sourceUri = buildPageUri(config, page),
                        plainText = linearized.text,
                        depth = cursor.depth,
                        tables = linearized.tables,
                        calendars = linearized.calendars,
                    ),
                )
                emitted++
                if (!includeChildren) continue
                if (cursor.depth >= maxDepth) continue
                for (childId in loadChildPageIds(client, apiBase, cursor.id)) {
                    queue.addLast(PageCursor(id = childId, depth = cursor.depth + 1))
                }
            }
        }

    private fun loadPage(
        client: WebClient,
        apiBase: String,
        pageId: String,
        isRoot: Boolean,
    ): PageResponse? {
        val url = "$apiBase/content/$pageId?expand=body.view,body.storage,space,version"
        return runCatching {
            client
                .get()
                .uri(url)
                .retrieve()
                .onStatus({ it.isError }) { resp ->
                    resp.bodyToMono(String::class.java).defaultIfEmpty("").map { body ->
                        log.error(
                            "Confluence page fetch failed: pageId={} url={} status={} body={}",
                            pageId,
                            url,
                            resp.statusCode().value(),
                            body,
                        )
                        org.springframework.web.reactive.function.client.WebClientResponseException
                            .create(
                                resp.statusCode().value(),
                                resp.statusCode().toString(),
                                resp.headers().asHttpHeaders(),
                                body.toByteArray(),
                                null,
                            )
                    }
                }.bodyToMono<JsonNode>()
                .block(Duration.ofSeconds(30))
        }.getOrElse { e ->
            log.error(
                "Confluence page fetch failed: pageId={} url={} err={}",
                pageId,
                url,
                e.toString(),
            )
            if (isRoot) {
                throw IllegalStateException(
                    "Confluence root page fetch failed for $url — cannot start hierarchy walk. " +
                        "Cause: ${exceptionDiagnostic(e)}",
                    e,
                )
            }
            null
        }?.let { PageResponse.from(it) }
    }

    private fun loadChildPageIds(
        client: WebClient,
        apiBase: String,
        pageId: String,
    ): List<String> {
        val ids = mutableListOf<String>()
        var start = 0
        val limit = 50
        while (true) {
            val url = "$apiBase/content/$pageId/child/page?limit=$limit&start=$start"
            val node =
                runCatching {
                    client
                        .get()
                        .uri(url)
                        .retrieve()
                        .onStatus({ it.isError }) { resp ->
                            resp.bodyToMono(String::class.java).defaultIfEmpty("").map { body ->
                                log.error(
                                    "Confluence child-page fetch failed: parentId={} url={} status={} body={}",
                                    pageId,
                                    url,
                                    resp.statusCode().value(),
                                    body,
                                )
                                org.springframework.web.reactive.function.client.WebClientResponseException
                                    .create(
                                        resp.statusCode().value(),
                                        resp.statusCode().toString(),
                                        resp.headers().asHttpHeaders(),
                                        body.toByteArray(),
                                        null,
                                    )
                            }
                        }.bodyToMono<JsonNode>()
                        .block(Duration.ofSeconds(30))
                }.getOrElse { e ->
                    if (e !is org.springframework.web.reactive.function.client.WebClientResponseException) {
                        log.error("Confluence child-page fetch transport/parse error: parentId={} url={} err={}", pageId, url, e.toString())
                    }
                    return ids
                }
            val results = node!!.path("results")
            if (!results.isArray) return ids
            results.forEach { child ->
                child
                    .path("id")
                    .asText(null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(ids::add)
            }
            if (results.size() < limit) return ids
            start += limit
        }
    }

    internal fun extractPageId(
        config: ConfluenceConfig,
        pageUrl: String,
        apiBase: String,
        client: WebClient,
    ): String? {
        val queryStart = pageUrl.indexOf('?')
        val query =
            if (queryStart >= 0) {
                val queryStr = pageUrl.substring(queryStart + 1)
                queryStr
                    .split('&')
                    .mapNotNull { part ->
                        val eq = part.indexOf('=')
                        if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
                    }.toMap()
            } else {
                emptyMap()
            }
        query["pageId"]?.takeIf { it.isNotBlank() }?.let { return it }
        val pageMatch = Regex("/pages/(\\d+)").find(pageUrl)
        if (pageMatch != null) return pageMatch.groupValues[1]
        val title = query["title"] ?: return null
        val node =
            runCatching {
                client
                    .get()
                    .uri { ub ->
                        ub
                            .path("$apiBase/content")
                            .queryParam("title", title)
                            .queryParam("limit", 1)
                            .queryParam("expand", "version")
                            .build()
                    }.retrieve()
                    .bodyToMono<JsonNode>()
                    .onErrorResume { Mono.empty() }
                    .block(Duration.ofSeconds(15))
            }.getOrNull() ?: return null
        val first = node.path("results").firstOrNull() ?: return null
        return first.path("id").asText(null)
    }

    private fun buildPageUri(
        config: ConfluenceConfig,
        page: PageResponse,
    ): String {
        val spaceKey = page.spaceKey
        val pageId = page.id
        if (spaceKey.isNullOrBlank() || pageId.isBlank()) return config.baseUrl
        return confluenceCloudPageUrl(config.baseUrl, spaceKey, pageId)
    }

    private fun hostOf(url: String): String? =
        try {
            URI(url).host?.lowercase()
        } catch (_: URISyntaxException) {
            null
        }

    private fun fetchCalendarEvents(
        config: ConfluenceConfig,
        calendarRefs: List<com.stacklens.application.port.out.knowledge.CalendarReference>,
    ): List<com.stacklens.application.port.out.knowledge.CalendarEvent> {
        if (calendarRefs.isEmpty()) return emptyList()
        val now = java.time.Instant.now()
        val rangeStart = now.minus(30, java.time.temporal.ChronoUnit.DAYS)
        val rangeEnd = now.plus(90, java.time.temporal.ChronoUnit.DAYS)
        val all = mutableListOf<com.stacklens.application.port.out.knowledge.CalendarEvent>()
        for (ref in calendarRefs) {
            val rawEvents =
                runCatching {
                    calendarFetcher.fetchEvents(
                        config = config,
                        calendarId = ref.calendarId,
                        rangeStart = rangeStart,
                        rangeEnd = rangeEnd,
                    )
                }.getOrElse { e ->
                    log.warn(
                        "Calendar fetch failed for id={}: {}",
                        ref.calendarId,
                        e.message,
                    )
                    emptyList()
                }
            val resolvedTitle = ref.title.ifBlank { ref.calendarId }
            all +=
                rawEvents.map { ev ->
                    ev.copy(
                        title = ev.title.ifBlank { resolvedTitle },
                    )
                }
        }
        return all
    }

    private data class PageCursor(
        val id: String,
        val depth: Int,
    )

    private data class PageResponse(
        val id: String,
        val title: String,
        val spaceKey: String?,
        val bodyView: String?,
        val bodyStorage: String?,
    ) {
        companion object {
            fun from(node: JsonNode): PageResponse {
                val body = node.path("body")
                return PageResponse(
                    id = node.path("id").asText(""),
                    title = node.path("title").asText(""),
                    spaceKey = node.path("space").path("key").asText(null),
                    bodyView = body.path("view").path("value").asText(null),
                    bodyStorage = body.path("storage").path("value").asText(null),
                )
            }
        }
    }
}
