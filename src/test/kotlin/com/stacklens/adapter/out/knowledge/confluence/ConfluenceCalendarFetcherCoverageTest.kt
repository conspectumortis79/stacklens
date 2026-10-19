package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.time.Instant

class ConfluenceCalendarFetcherCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var fetcher: ConfluenceCalendarFetcher

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = ConfluenceCalendarFetcher(ReactiveHttpClientFactory(3000, 5000))
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

    private fun json(body: String) = MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(body)

    private fun fetch(): List<com.stacklens.application.port.out.knowledge.CalendarEvent> =
        fetcher.fetchEvents(
            config = config(),
            calendarId = "cal-7",
            rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
            rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
        )

    @Test
    fun `events wrapped in an object are parsed with their timestamps`() {
        server.enqueue(
            json(
                """
                {"events":[
                  {"id":"e1","title":"Standup","description":"  ","location":"Room 1",
                   "start":"2026-02-03T09:00:00Z","end":"2026-02-03T09:30:00Z"},
                  {"id":"e2","title":"Review","start":"   ","end":"1770000000000"},
                  {"id":"e3","title":"Numeric","start":1770000000000,"end":{"unexpected":true}}
                ]}
                """.trimIndent(),
            ),
        )

        val events = fetch()

        assertEquals(3, events.size)
        assertEquals("e1", events[0].id)
        assertNull(events[0].description)
        assertEquals("Room 1", events[0].location)
        assertEquals(Instant.parse("2026-02-03T09:00:00Z"), events[0].start)
        assertEquals(Instant.EPOCH, events[1].start)
        assertEquals(Instant.ofEpochMilli(1770000000000L), events[1].end)
        assertEquals(Instant.ofEpochMilli(1770000000000L), events[2].start)
        assertEquals(Instant.EPOCH, events[2].end)
    }

    @Test
    fun `a bare array payload is accepted`() {
        server.enqueue(json("""[{"id":"solo","title":"Only","start":"2026-05-05T05:05:05Z","end":"2026-05-05T06:00:00Z"}]"""))

        val events = fetch()

        assertEquals(1, events.size)
        assertEquals("solo", events.single().id)
    }

    @Test
    fun `an object without an events array yields no events`() {
        server.enqueue(json("""{"other":[{"id":"x"}]}"""))

        assertTrue(fetch().isEmpty())
    }

    @Test
    fun `numeric timestamps and missing nodes fall back to the epoch`() {
        server.enqueue(json("""{"events":[{"id":"weird","title":"W","start":"not-a-date","end":42}]}"""))

        val events = fetch()

        assertEquals(1, events.size)
        assertEquals(Instant.EPOCH, events.single().start)
        org.junit.jupiter.api.Assertions
            .assertNotEquals(Instant.EPOCH, events.single().end)
    }

    @Test
    fun `a server error body is logged and reported as no events`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("calendar unavailable"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("calendar unavailable"))

        assertTrue(fetch().isEmpty())
    }

    @Test
    fun `an unreachable host is reported as no events`() {
        val dead =
            ConfluenceConfig(
                name = "dead",
                baseUrl = "http://127.0.0.1:1",
                username = "u",
                apiToken = "t",
                cloudMode = true,
            )

        val events =
            fetcher.fetchEvents(
                config = dead,
                calendarId = "cal-1",
                rangeStart = Instant.parse("2026-01-01T00:00:00Z"),
                rangeEnd = Instant.parse("2026-12-31T23:59:59Z"),
            )

        assertTrue(events.isEmpty())
    }
}
