package com.stacklens.adapter.out.knowledge.confluence

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.CalendarEvent
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ConfluenceCalendarFetcherFailureTest {
    private lateinit var server: MockWebServer

    private val factory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 10_000)

    private val fetcher by lazy { ConfluenceCalendarFetcher(factory) }

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun config() =
        ConfluenceConfig(
            name = "cf",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
        )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun fetch(): List<CalendarEvent> =
        fetcher.fetchEvents(
            config = config(),
            calendarId = "cal-7",
            rangeStart = Instant.ofEpochMilli(1_750_000_000_000L),
            rangeEnd = Instant.ofEpochMilli(1_760_000_000_000L),
        )

    @Test
    fun `an error answer is retried against the second endpoint and ends empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/plain")
                .setBody("calendar plugin defekt"),
        )
        server.enqueue(json("""{"events":[]}"""))

        val events = fetch()

        assertTrue(events.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a wrapped events node that is no array yields nothing`() {
        val events =
            fetcher.parseEvents(
                ObjectMapper().readTree("""{"events":"kein array"}"""),
                "cal-7",
            )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `an event with container fields instead of text is dropped`() {
        val events =
            fetcher.parseEvents(
                ObjectMapper()
                    .readTree(
                        """{"events":[{"id":"e1","title":"Termin","start":"1750000000000",
                          "end":"wert","description":{"x":1},"location":["a"]}]}""",
                    ),
                "cal-7",
            )

        assertEquals(1, events.size)
        assertEquals("Termin", events.first().title)
        assertEquals(Instant.ofEpochMilli(1_750_000_000_000L), events.first().start)
        assertEquals(Instant.EPOCH, events.first().end)
    }

    @Test
    fun `a bare array answer is accepted and blank fields stay null`() {
        val events =
            fetcher.parseEvents(
                ObjectMapper().readTree("""[{"id":"e1","title":"  ","start":"","end":[]}]"""),
                "cal-7",
            )

        assertEquals(1, events.size)
        assertEquals("  ", events.first().title)
        assertEquals(null, events.first().description)
        assertEquals(null, events.first().location)
    }

    @Test
    fun `a node without an events key yields nothing`() {
        assertTrue(fetcher.parseEvents(ObjectMapper().readTree("""{"andere":"daten"}"""), "cal-7").isEmpty())
        assertTrue(fetcher.parseEvents(ObjectMapper().readTree("[]"), "cal-7").isEmpty())
    }
}
