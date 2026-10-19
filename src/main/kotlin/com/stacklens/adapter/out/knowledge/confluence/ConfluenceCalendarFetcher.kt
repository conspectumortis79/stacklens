package com.stacklens.adapter.out.knowledge.confluence

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.knowledge.CalendarEvent
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.bearerOrBasicAuthHeader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.time.Instant

@Component
class ConfluenceCalendarFetcher(
    private val httpFactory: ReactiveHttpClientFactory,
) {
    private val log = LoggerFactory.getLogger(ConfluenceCalendarFetcher::class.java)

    fun fetchEvents(
        config: ConfluenceConfig,
        calendarId: String,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): List<CalendarEvent> {
        if (calendarId.isBlank()) return emptyList()
        val client =
            httpFactory
                .builder()
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Authorization", bearerOrBasicAuthHeader(config.username, config.apiToken))
                .build()
        val urlTemplates = buildCalendarUrlCandidates(config, calendarId, rangeStart, rangeEnd)
        for (url in urlTemplates) {
            val events = tryFetch(client, url, calendarId)
            if (events != null) {
                if (events.isNotEmpty()) {
                    log.info(
                        "Fetched {} calendar event(s) for calendarId={} url={}",
                        events.size,
                        calendarId,
                        url.substringBefore("/rest/"),
                    )
                } else {
                    log.info(
                        "Confluence calendar {} responded but contains no events in the requested range (url={})",
                        calendarId,
                        url.substringBefore("/rest/"),
                    )
                }
                return events
            }
        }
        log.warn(
            "Confluence calendar fetch failed for calendarId={}: tried {} endpoint(s), none responded successfully",
            calendarId,
            urlTemplates.size,
        )
        return emptyList()
    }

    private fun tryFetch(
        client: org.springframework.web.reactive.function.client.WebClient,
        url: String,
        calendarId: String,
    ): List<CalendarEvent>? =
        runCatching {
            client
                .get()
                .uri(url)
                .retrieve()
                .onStatus({ status -> status.isError }) { resp ->
                    resp.bodyToMono(String::class.java).defaultIfEmpty("").map { body ->
                        log.debug(
                            "Confluence calendar endpoint {} returned {} for calendarId={}",
                            url.substringAfterLast("/rest/"),
                            resp.statusCode().value(),
                            calendarId,
                        )
                        CalendarFetchException("HTTP ${resp.statusCode().value()}")
                    }
                }.bodyToMono<JsonNode>()
                .block(Duration.ofSeconds(30))
        }.getOrElse { e ->
            if (e !is CalendarFetchException) {
                log.debug(
                    "Confluence calendar endpoint {} errored for calendarId={}: {}",
                    url.substringAfterLast("/rest/"),
                    calendarId,
                    e.message,
                )
            }
            null
        }?.let { parseEvents(it, calendarId) }

    internal fun buildCalendarUrlCandidates(
        config: ConfluenceConfig,
        calendarId: String,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): List<String> {
        val base = config.baseUrl.trimEnd('/')
        val wikiPath = if (base.contains("/wiki")) "" else "/wiki"
        val query = "?start=${rangeStart.toEpochMilli()}&end=${rangeEnd.toEpochMilli()}"
        return listOf(
            "$base$wikiPath/rest/calendar-services/1.0/calendar/$calendarId/events$query",
            "$base$wikiPath/rest/calendar-plugin/1.0/calendar/$calendarId/events$query",
        )
    }

    internal fun parseEvents(
        node: JsonNode,
        calendarId: String,
    ): List<CalendarEvent> {
        val arrayNode: JsonNode =
            when {
                node.isArray -> node
                node.has("events") && node.get("events").isArray -> node.get("events")
                else -> return emptyList()
            }
        return arrayNode.mapNotNull { eventNode -> parseEvent(eventNode, calendarId) }
    }

    private fun parseEvent(
        eventNode: JsonNode,
        calendarId: String,
    ): CalendarEvent? =
        runCatching {
            CalendarEvent(
                calendarId = calendarId,
                id = eventNode.path("id").asText(""),
                title = eventNode.path("title").asText(""),
                start = parseInstant(eventNode.path("start")),
                end = parseInstant(eventNode.path("end")),
                description = eventNode.path("description").asText(null)?.takeIf { it.isNotBlank() },
                location = eventNode.path("location").asText(null)?.takeIf { it.isNotBlank() },
            )
        }.getOrElse { e ->
            log.warn(
                "Failed to parse Confluence calendar event: calendarId={} err={}",
                calendarId,
                e.message,
            )
            null
        }

    private fun parseInstant(node: JsonNode): Instant {
        if (node.isTextual) {
            val text = node.asText()
            if (text.isBlank()) return Instant.EPOCH
            return runCatching { Instant.parse(text) }
                .recoverCatching {
                    Instant.ofEpochMilli(text.toLong())
                }.getOrDefault(Instant.EPOCH)
        }
        if (node.isNumber) return Instant.ofEpochMilli(node.asLong())
        return Instant.EPOCH
    }

    private class CalendarFetchException(
        message: String,
    ) : RuntimeException(message)
}
