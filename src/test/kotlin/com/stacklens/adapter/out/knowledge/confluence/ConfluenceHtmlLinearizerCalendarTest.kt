package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.application.port.out.knowledge.CalendarEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.TimeZone

class ConfluenceHtmlLinearizerCalendarTest {
    private val sut = ConfluenceHtmlLinearizer()
    private val zone = java.time.ZoneId.of("UTC")
    private val originalTz = TimeZone.getDefault()

    @BeforeEach
    fun pinTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun `linearize without calendar events returns empty calendars list`() {
        val out = sut.linearize("<p>plain page</p>")
        assertThat(out.calendars).isEmpty()
    }

    @Test
    fun `linearize with empty events list returns empty calendars list`() {
        val out = sut.linearize("<p>plain page</p>", emptyList())
        assertThat(out.calendars).isEmpty()
        assertThat(out.text).doesNotContain("Tabelle: Kalender")
    }

    @Test
    fun `linearize with events emits markdown calendar table`() {
        val events =
            listOf(
                event(
                    calendarId = "cal-1",
                    id = "evt-1",
                    title = "On-call: Alice",
                    start = "2026-08-25T08:00:00Z",
                    end = "2026-08-25T18:00:00Z",
                    description = "Hotline 8-18",
                ),
            )
        val out = sut.linearize("<p>intro</p>", events, mapOf("cal-1" to "Support Calendar"))

        assertThat(out.text).contains("Tabelle: Kalender \"Support Calendar\"")
        assertThat(out.text).contains("| Datum | Uhrzeit | Titel | Beschreibung |")
        assertThat(out.text).contains("| 2026-08-25 | 08:00–18:00 | On-call: Alice | Hotline 8-18 |")
    }

    @Test
    fun `linearize with events emits per-event sentences for embedding`() {
        val events =
            listOf(
                event(
                    calendarId = "cal-1",
                    id = "evt-1",
                    title = "On-call: Alice",
                    start = "2026-08-25T08:00:00Z",
                    end = "2026-08-25T18:00:00Z",
                ),
            )
        val out = sut.linearize("<p>x</p>", events, mapOf("cal-1" to "Support Calendar"))
        val localStart = Instant.parse("2026-08-25T08:00:00Z").atZone(zone)
        val dateStr =
            "%04d-%02d-%02d".format(
                localStart.year,
                localStart.monthValue,
                localStart.dayOfMonth,
            )
        assertThat(out.text).contains("Eintrag 1: On-call: Alice am $dateStr")
    }

    @Test
    fun `calendar events with multiple entries are sorted by start`() {
        val events =
            listOf(
                event("cal", "2", "Later", "2026-08-26T08:00:00Z", "2026-08-26T18:00:00Z"),
                event("cal", "1", "Earlier", "2026-08-25T08:00:00Z", "2026-08-25T18:00:00Z"),
            )
        val out = sut.linearize("<p>x</p>", events)
        val earlierIdx = out.text.indexOf("Eintrag 1: Earlier")
        val laterIdx = out.text.indexOf("Eintrag 2: Later")
        assertThat(earlierIdx).isGreaterThanOrEqualTo(0)
        assertThat(laterIdx).isGreaterThan(earlierIdx)
    }

    @Test
    fun `multiple calendars produce separate tables and meta entries`() {
        val events =
            listOf(
                event("cal-A", "1", "On-call", "2026-08-25T08:00:00Z", "2026-08-25T18:00:00Z"),
                event("cal-B", "2", "Sprint Review", "2026-08-26T10:00:00Z", "2026-08-26T11:00:00Z"),
            )
        val titles = mapOf("cal-A" to "On-call", "cal-B" to "Sprint Review")
        val out = sut.linearize("<p>x</p>", events, titles)

        assertThat(out.calendars).hasSize(2)
        assertThat(out.calendars.map { it.calendarId }).containsExactlyInAnyOrder("cal-A", "cal-B")
        assertThat(out.text).contains("Tabelle: Kalender \"On-call\"")
        assertThat(out.text).contains("Tabelle: Kalender \"Sprint Review\"")
    }

    @Test
    fun `events grouped by calendarId share the calendar meta entry`() {
        val events =
            listOf(
                event("cal-A", "1", "A1", "2026-08-25T08:00:00Z", "2026-08-25T18:00:00Z"),
                event("cal-A", "2", "A2", "2026-08-26T08:00:00Z", "2026-08-26T18:00:00Z"),
                event("cal-B", "3", "B1", "2026-08-27T08:00:00Z", "2026-08-27T18:00:00Z"),
            )
        val titles = mapOf("cal-A" to "Team A", "cal-B" to "Team B")
        val out = sut.linearize("<p>x</p>", events, titles)
        val calA = out.calendars.first { it.calendarId == "cal-A" }
        val calB = out.calendars.first { it.calendarId == "cal-B" }
        assertThat(calA.eventCount).isEqualTo(2)
        assertThat(calB.eventCount).isEqualTo(1)
        assertThat(calA.title).isEqualTo("Team A")
        assertThat(calB.title).isEqualTo("Team B")
    }

    @Test
    fun `event description is included in both markdown row and sentence`() {
        val events =
            listOf(
                event(
                    "cal",
                    "1",
                    "X",
                    "2026-08-25T08:00:00Z",
                    "2026-08-25T18:00:00Z",
                    description = "Beschreibung mit Sonderzeichen | und \\ Pipe",
                ),
            )
        val out = sut.linearize("<p>x</p>", events)
        assertThat(out.text).contains("Beschreibung mit Sonderzeichen \\| und \\ Pipe")
    }

    @Test
    fun `event without description omits description column`() {
        val events =
            listOf(
                event("cal", "1", "X", "2026-08-25T08:00:00Z", "2026-08-25T18:00:00Z"),
            )
        val out = sut.linearize("<p>x</p>", events)
        assertThat(out.text).contains("| 2026-08-25 | 08:00–18:00 | X |  |")
    }

    @Test
    fun `linearize with only events and no html produces non-empty text and metas`() {
        val events =
            listOf(
                event("cal", "1", "Solo", "2026-08-25T08:00:00Z", "2026-08-25T18:00:00Z"),
            )
        val out = sut.linearize("", events)
        assertThat(out.text).isNotBlank
        assertThat(out.calendars).hasSize(1)
    }

    @Test
    fun `calendar block content does not bleed into subsequent page content`() {
        val events =
            listOf(
                event("cal", "1", "A", "2026-08-25T08:00:00Z", "2026-08-25T18:00:00Z"),
            )
        val out = sut.linearize("<p>before</p><p>after</p>", events)
        assertThat(out.text).contains("before")
        assertThat(out.text).contains("after")
        assertThat(out.text).contains("Tabelle: Kalender")
    }

    private fun event(
        calendarId: String,
        id: String,
        title: String,
        start: String,
        end: String,
        description: String? = null,
    ): CalendarEvent =
        CalendarEvent(
            calendarId = calendarId,
            id = id,
            title = title,
            start = Instant.parse(start),
            end = Instant.parse(end),
            description = description,
        )
}
