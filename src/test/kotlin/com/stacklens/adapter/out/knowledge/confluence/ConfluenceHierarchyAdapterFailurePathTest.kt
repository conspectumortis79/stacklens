package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.application.port.out.knowledge.CalendarEvent
import com.stacklens.application.port.out.knowledge.CalendarReference
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class ConfluenceHierarchyAdapterFailurePathTest {
    private lateinit var server: MockWebServer

    private val factory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 10_000)

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
        com.stacklens.domain.model.ConfluenceConfig(
            name = "demo",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
        )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun client() = factory.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()

    @Test
    fun `the page id is taken from the url path when no query parameter helps`() {
        val adapter = ConfluenceHierarchyAdapter(factory)
        val base = server.url("/").toString().trimEnd('/')

        assertEquals(
            "1234",
            adapter.extractPageId(config(), "$base/spaces/DEV/pages/1234/Irgendein-Titel", "$base/rest/api", client()),
        )
        assertEquals(
            "77",
            adapter.extractPageId(config(), "$base/pages/viewpage.action?pageId=77", "$base/rest/api", client()),
        )
        assertNull(
            adapter.extractPageId(config(), "$base/display/DEV/OhneId", "$base/rest/api", client()),
        )
    }

    @Test
    fun `a title lookup resolves the identifier through the api`() {
        server.enqueue(json("""{"results":[{"id":"55"}]}"""))
        val adapter = ConfluenceHierarchyAdapter(factory)
        val base = server.url("/").toString().trimEnd('/')

        assertEquals(
            "55",
            adapter.extractPageId(config(), "$base/display/DEV/Rotationsplan?x=1&=z&title=Rotationsplan", "$base/rest/api", client()),
        )
    }

    @Test
    fun `a title lookup that answers with broken json is dropped`() {
        server.enqueue(json("kein json{"))
        val adapter = ConfluenceHierarchyAdapter(factory)
        val base = server.url("/").toString().trimEnd('/')

        assertNull(
            adapter.extractPageId(config(), "$base/display/DEV/Titel?title=Kaputt", "$base/rest/api", client()),
        )
    }

    @Test
    fun `a root page that fails aborts the walk with the http cause`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/plain")
                .setBody("interner fehler"),
        )
        val adapter = ConfluenceHierarchyAdapter(factory)
        val base = server.url("/").toString().trimEnd('/')

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.walkHierarchy(config(), "$base/pages/400/Fehlstart", true, 2, 5).toList()
            }

        assertTrue(failure.message!!.contains("root page fetch failed"), failure.message)
        assertTrue(failure.message!!.contains("Cause:"), failure.message)
    }

    @Test
    fun `a failing child lookup keeps the root page`() {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(
            json(
                """{"id":"1","title":"Wurzel","body":{"storage":{"value":"<p>text</p>"}}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(503).setBody("patha"))
        val adapter = ConfluenceHierarchyAdapter(factory)

        val pages = adapter.walkHierarchy(config(), "$base/spaces/DEV/pages/1/Wurzel", true, 2, 5).toList()

        assertEquals(1, pages.size)
        assertEquals("Wurzel", pages.first().title)
    }

    private fun calendarEvent(
        id: String,
        title: String,
    ): CalendarEvent = CalendarEvent("cal-9", id, title, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))

    private fun fetch(
        fetcher: ConfluenceCalendarFetcher,
        refs: List<CalendarReference>,
    ): List<CalendarEvent> {
        val adapter = ConfluenceHierarchyAdapter(factory, calendarFetcher = fetcher)
        val method =
            ConfluenceHierarchyAdapter::class.java.declaredMethods.first { it.name == "fetchCalendarEvents" }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(adapter, config(), refs) as List<CalendarEvent>
    }

    private val reference = CalendarReference(calendarId = "cal-9", title = " ", macroName = "calendar")

    @Test
    fun `a calendar lookup that explodes yields no events and falls back to the id title`() {
        val fetcher = mock<ConfluenceCalendarFetcher>()
        whenever(fetcher.fetchEvents(any(), any(), any(), any())).thenThrow(RuntimeException("calendar plugin offline"))

        val events = fetch(fetcher, listOf(reference))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `events without a title inherit the calendar name`() {
        val fetcher = mock<ConfluenceCalendarFetcher>()
        whenever(fetcher.fetchEvents(any(), any(), any(), any()))
            .thenReturn(listOf(calendarEvent("e1", " "), calendarEvent("e2", "Benannt")))

        val events = fetch(fetcher, listOf(reference))

        assertEquals(listOf("cal-9", "Benannt"), events.map { it.title })
    }
}
