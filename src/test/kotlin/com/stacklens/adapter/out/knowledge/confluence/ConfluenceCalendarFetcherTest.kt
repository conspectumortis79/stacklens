package com.stacklens.adapter.out.knowledge.confluence

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ConfluenceCalendarFetcherTest {
    private val mapper = ObjectMapper()
    private lateinit var server: MockWebServer
    private lateinit var fetcher: ConfluenceCalendarFetcher

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = ConfluenceCalendarFetcher(ReactiveHttpClientFactory(5000, 10000))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): ConfluenceConfig =
        ConfluenceConfig(
            name = "demo",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
            cloudMode = true,
        )

    @Test
    fun `returns empty list when calendarId is blank`() {
        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(events).isEmpty()
    }

    @Test
    fun `returns empty list when server returns 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "999",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(events).isEmpty()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `returns empty list when server returns 500`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "999",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(events).isEmpty()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `parses wrapped events payload`() {
        val payload =
            """
            {
              "events": [
                {
                  "id": "evt-1",
                  "title": "On-call: Alice",
                  "start": "2026-08-25T08:00:00.000Z",
                  "end":   "2026-08-25T18:00:00.000Z",
                  "description": "Hotline 8-18",
                  "location": "Zürich"
                }
              ]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(payload),
        )

        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "cal-1",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )

        assertThat(events).hasSize(1)
        val e = events[0]
        assertThat(e.calendarId).isEqualTo("cal-1")
        assertThat(e.id).isEqualTo("evt-1")
        assertThat(e.title).isEqualTo("On-call: Alice")
        assertThat(e.start).isEqualTo(Instant.parse("2026-08-25T08:00:00.000Z"))
        assertThat(e.end).isEqualTo(Instant.parse("2026-08-25T18:00:00.000Z"))
        assertThat(e.description).isEqualTo("Hotline 8-18")
        assertThat(e.location).isEqualTo("Zürich")
    }

    @Test
    fun `parses bare array payload`() {
        val payload =
            """
            [
              {"id":"1","title":"A","start":"2026-08-25T08:00:00Z","end":"2026-08-25T18:00:00Z"},
              {"id":"2","title":"B","start":"2026-08-26T08:00:00Z","end":"2026-08-26T18:00:00Z"}
            ]
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(payload),
        )

        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "cal-x",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )

        assertThat(events).hasSize(2)
        assertThat(events.map { it.title }).containsExactly("A", "B")
    }

    @Test
    fun `parses epoch-millis instants as fallback`() {
        val payload =
            """
            {
              "events": [
                {"id":"1","title":"T","start":1700000000000,"end":1700003600000}
              ]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(payload),
        )

        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "cal-epoch",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )

        assertThat(events).hasSize(1)
        assertThat(events[0].start).isEqualTo(Instant.ofEpochMilli(1700000000000L))
        assertThat(events[0].end).isEqualTo(Instant.ofEpochMilli(1700003600000L))
    }

    @Test
    fun `treats blank description and location as null`() {
        val payload =
            """
            {
              "events": [
                {"id":"1","title":"T","start":"2026-08-25T08:00:00Z","end":"2026-08-25T18:00:00Z","description":"","location":"   "}
              ]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(payload),
        )

        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "cal",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )

        assertThat(events[0].description).isNull()
        assertThat(events[0].location).isNull()
    }

    @Test
    fun `skips individual events that fail to parse`() {
        val payload =
            """
            {
              "events": [
                {"id":"good","title":"OK","start":"2026-08-25T08:00:00Z","end":"2026-08-25T18:00:00Z"},
                {"id":"bad","title":"BAD","start":"not-a-date","end":"also-bad"},
                {"id":"also-good","title":"OK2","start":"2026-08-26T08:00:00Z","end":"2026-08-26T18:00:00Z"}
              ]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(payload),
        )

        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "cal",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )

        assertThat(events.map { it.id }).contains("good", "bad", "also-good")
    }

    @Test
    fun `falls back to Instant EPOCH when start is missing or unparseable`() {
        val payload =
            """
            {"events":[{"id":"1","title":"X","end":"2026-08-25T18:00:00Z"}]}
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(payload),
        )

        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "cal",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )

        assertThat(events).hasSize(1)
        assertThat(events[0].start).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun `buildCalendarUrlCandidates uses wiki path prefix when baseUrl already contains wiki`() {
        val cfg =
            ConfluenceConfig(
                name = "d",
                baseUrl = "${server.url("/").toString().trimEnd('/')}/wiki",
                username = "u",
                apiToken = "t",
                cloudMode = true,
            )
        val urls =
            fetcher.buildCalendarUrlCandidates(
                config = cfg,
                calendarId = "C",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(urls).allMatch { it.contains("/wiki/rest/") }
        assertThat(urls).noneMatch { it.contains("/wiki/wiki/") }
        assertThat(urls).anyMatch { it.contains("/rest/calendar-services/1.0/") }
        assertThat(urls).anyMatch { it.contains("/rest/calendar-plugin/1.0/") }
    }

    @Test
    fun `buildCalendarUrlCandidates inserts wiki path when baseUrl does not contain wiki`() {
        val cfg =
            ConfluenceConfig(
                name = "d",
                baseUrl = server.url("/").toString().trimEnd('/'),
                username = "u",
                apiToken = "t",
                cloudMode = true,
            )
        val urls =
            fetcher.buildCalendarUrlCandidates(
                config = cfg,
                calendarId = "C",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(urls).allMatch { it.contains("/wiki/rest/") }
    }

    @Test
    fun `buildCalendarUrlCandidates includes start and end query params as epoch millis`() {
        val urls =
            fetcher.buildCalendarUrlCandidates(
                config = config(),
                calendarId = "C",
                rangeStart = Instant.parse("2026-08-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-08-31T00:00:00Z"),
            )
        urls.forEach {
            assertThat(it).contains("start=${Instant.parse("2026-08-01T00:00:00Z").toEpochMilli()}")
            assertThat(it).contains("end=${Instant.parse("2026-08-31T00:00:00Z").toEpochMilli()}")
        }
    }

    @Test
    fun `buildCalendarUrlCandidates prefers the newer services endpoint first`() {
        val urls =
            fetcher.buildCalendarUrlCandidates(
                config = config(),
                calendarId = "C",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(urls[0]).contains("/rest/calendar-services/1.0/")
        assertThat(urls[1]).contains("/rest/calendar-plugin/1.0/")
    }

    @Test
    fun `fetchEvents falls back to the second endpoint when the first returns 404`() {
        var firstCalls = 0
        var secondCalls = 0
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/rest/calendar-services/1.0/calendar/dual/events") -> {
                            firstCalls++
                            MockResponse().setResponseCode(404)
                        }
                        path.contains("/rest/calendar-plugin/1.0/calendar/dual/events") -> {
                            secondCalls++
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """{"events":[
                                        {"id":"1","title":"Via SotM","start":"2026-08-25T08:00:00Z","end":"2026-08-25T18:00:00Z"}
                                      ]}""",
                                )
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "dual",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(events).hasSize(1)
        assertThat(events[0].title).isEqualTo("Via SotM")
        assertThat(firstCalls).isEqualTo(1)
        assertThat(secondCalls).isEqualTo(1)
    }

    @Test
    fun `fetchEvents returns empty when both endpoints fail`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(500)
            }
        val events =
            fetcher.fetchEvents(
                config = config(),
                calendarId = "broken",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )
        assertThat(events).isEmpty()
    }

    @Test
    fun `parseEvents returns empty for payload with neither events key nor array`() {
        val node: JsonNode = mapper.readTree("""{"foo":"bar"}""")
        assertThat(fetcher.parseEvents(node, "cal")).isEmpty()
    }

    @Test
    fun `parseEvents handles empty array`() {
        val node: JsonNode = mapper.readTree("""{"events":[]}""")
        assertThat(fetcher.parseEvents(node, "cal")).isEmpty()
    }

    @Test
    fun `parseEvents handles bare empty array`() {
        val node: JsonNode = mapper.readTree("""[]""")
        assertThat(fetcher.parseEvents(node, "cal")).isEmpty()
    }

    @Test
    fun `parses mixed payload and array format`() {
        val node: JsonNode = mapper.readTree("""[{"id":"1","title":"X","start":"2026-08-25T08:00:00Z","end":"2026-08-25T18:00:00Z"}]""")
        val events = fetcher.parseEvents(node, "cal-1")
        assertThat(events).hasSize(1)
        assertThat(events[0].calendarId).isEqualTo("cal-1")
    }

    @Test
    fun `sends Authorization header with the configured credentials`() {
        val recorded = mutableListOf<RecordedRequest>()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    recorded.add(request)
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"events":[]}""")
                }
            }
        fetcher.fetchEvents(
            config = config(),
            calendarId = "cal",
            rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
            rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
        )
        assertThat(recorded).isNotEmpty
        assertThat(recorded.first().getHeader("Authorization")).isNotNull
    }
}
