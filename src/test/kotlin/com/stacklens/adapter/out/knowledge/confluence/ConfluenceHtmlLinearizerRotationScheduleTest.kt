package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.application.port.out.knowledge.CalendarEvent
import com.stacklens.testsupport.CompanionAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ConfluenceHtmlLinearizerRotationScheduleTest {
    private val sut = ConfluenceHtmlLinearizer()

    private fun text(html: String): String = sut.linearize(html).text

    private fun rotation(
        header: String,
        rows: List<String>,
        prefix: String = "",
    ): String =
        """
        $prefix
        <table>$header${rows.joinToString("")}</table>
        """.trimIndent()

    @Test fun `single slot schedule lists one person per weekday`() {
        val html =
            rotation(
                "<tr><th>Woche</th><th>Mo</th><th>Di</th><th>Mi</th></tr>",
                listOf("<tr><td>2026-08-24</td><td>Ana</td><td>Ben</td><td>Cai</td></tr>"),
            )

        val out = text(html)

        assertTrue(out.contains("Tabelle: Rotations-Schedule"))
        assertTrue(out.contains("| 2026-08-24 (Montag) | Vormittag | Ana |"))
        assertTrue(out.contains("| 2026-08-26 (Mittwoch) | Vormittag | Cai |"))
        assertTrue(out.contains("Eintrag: Ben hat am 2026-08-25 (Dienstag Vormittag) Support."))
        assertFalse(out.contains("Nachmittag"))
    }

    @Test fun `two slot schedule splits forenoon and afternoon per day`() {
        val html =
            rotation(
                "<tr><th></th><th>Mo</th><th>Di</th><th>Mi</th></tr>" +
                    "<tr><th>Info</th><th>Vormittag</th><th>Nachmittag</th><th>Vormittag</th><th>Nachmittag</th><th>Vormittag</th><th>Nachmittag</th><th>Kommentar</th></tr>",
                listOf("<tr><td>24.08.2026</td><td>Ana</td><td>Ben</td><td></td><td>Cai</td><td>Dan</td><td>Eve</td><td>x</td></tr>"),
            )

        val out = text(html)

        assertTrue(out.contains("| 2026-08-24 (Montag) | Vormittag | Ana |"))
        assertTrue(out.contains("| 2026-08-24 (Montag) | Nachmittag | Ben |"))
        assertTrue(out.contains("| 2026-08-25 (Dienstag) | Nachmittag | Cai |"))
        assertTrue(out.contains("| 2026-08-26 (Mittwoch) | Vormittag | Dan |"))
        assertFalse(out.contains("(Dienstag) | Vormittag |"))
    }

    @Test fun `days without a person and rows without enough cells are skipped`() {
        val html =
            rotation(
                "<tr><th>Kopf</th><th>Mo</th><th>Di</th><th>Mi</th><th>Do</th><th>Fr</th></tr>",
                listOf(
                    "<tr><td>2026-08-24</td><td>   </td><td>Ben</td></tr>",
                    "<tr><td>2026-08-31</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>",
                ),
            )

        val out = text(html)

        assertTrue(out.contains("| 2026-08-25 (Dienstag) | Vormittag | Ben |"))
        assertFalse(out.contains("2026-08-24 (Montag)"))
        assertEquals(1, Regex("Eintrag: ").findAll(out).count())
    }

    @Test fun `a schedule whose rows carry no names emits no entries`() {
        val html =
            rotation(
                "<tr><th>Kopf</th><th>Mo</th><th>Di</th><th>Mi</th></tr>",
                listOf("<tr><td>2026-08-24</td><td></td><td> </td><td></td></tr>"),
            )

        val out = text(html)

        assertTrue(out.contains("Tabelle: Rotations-Schedule"))
        assertFalse(out.contains("Eintrag:"))
        assertFalse(out.contains("Inhalt des Rotations-Schedules"))
    }

    @Test fun `weekday headers need dated rows to be treated as a schedule`() {
        val html =
            rotation(
                "<tr><th>Kopf</th><th>Mo</th><th>Di</th><th>Mi</th></tr>",
                listOf("<tr><td>kein datum</td><td>a</td><td>b</td><td>c</td></tr>", "<tr></tr>"),
            )

        val out = text(html)

        assertFalse(out.contains("Rotations-Schedule"))
    }

    @Test fun `header cells that are not weekdays do not create day columns`() {
        val html =
            rotation(
                "<tr><th>#</th><th>Woche</th><th>Monday</th><th>Di</th><th>Fr</th></tr>",
                listOf("<tr><td>2026-08-24</td><td>w01</td><td>Ana</td><td>Ben</td><td>Cai</td></tr>"),
            )

        val out = text(html)

        assertTrue(out.contains("| 2026-08-24 (Montag) | Vormittag | w01 |"))
        assertTrue(out.contains("| 2026-08-25 (Dienstag) | Vormittag | Ana |"))
        assertTrue(out.contains("| 2026-08-26 (Freitag) | Vormittag | Ben |"))
        assertEquals(3, Regex("Eintrag: ").findAll(out).count())
    }

    @Test fun `all supported date formats start the week on monday`() {
        val html =
            rotation(
                "<tr><th>Kopf</th><th>Mo</th><th>Di</th><th>Mi</th></tr>",
                listOf(
                    "<tr><td>24.08.2026</td><td>a1</td><td>a2</td><td>a3</td></tr>",
                    "<tr><td>2026-08-24</td><td>b1</td><td>b2</td><td>b3</td></tr>",
                    "<tr><td>24/08/2026</td><td>c1</td><td>c2</td><td>c3</td></tr>",
                    "<tr><td>08/24/2026</td><td>d1</td><td>d2</td><td>d3</td></tr>",
                ),
            )

        val out = text(html)

        assertEquals(12, Regex("Eintrag: ").findAll(out).count())
    }

    @Test fun `the caption of a schedule comes from the previous heading`() {
        val html =
            rotation(
                "<tr><th>Kopf</th><th>Mo</th><th>Di</th><th>Mi</th></tr>",
                listOf("<tr><td>2026-08-24</td><td>Ana</td><td>Ben</td><td>Cai</td></tr>"),
                prefix = "<h3>Team-Kalender</h3><p>Notiz</p>",
            )

        val out = text(html)

        assertTrue(out.contains("Tabelle: Rotations-Schedule — Team-Kalender"))
        assertTrue(out.contains("Inhalt des Rotations-Schedules — Team-Kalender:"))
    }

    @Test fun `a table caption element is used when no heading precedes the table`() {
        val html =
            """
            <p>Text</p>
            <table>
              <caption>Rotationsliste</caption>
              <tr><th>Kopf</th><th>Mo</th><th>Di</th><th>Mi</th></tr>
              <tr><td>2026-08-24</td><td>Ana</td><td>Ben</td><td>Cai</td></tr>
            </table>
            """.trimIndent()

        val out = text(html)

        assertTrue(out.contains("Tabelle: Rotations-Schedule — Rotationsliste"))
    }

    @Test fun `pipes inside person names are escaped in markdown rows`() {
        val html =
            rotation(
                "<tr><th>Kopf</th><th>Mo</th><th>Di</th><th>Mi</th></tr>",
                listOf("<tr><td>2026-08-24</td><td>Ana | Der Boss</td><td>Ben</td><td>Cai</td></tr>"),
            )

        assertTrue(text(html).contains("| Ana \\| Der Boss |"))
    }

    @Test fun `calendar tables fall back to the calendar id as title`() {
        val out = StringBuilder()
        val first = Instant.parse("2026-08-24T08:00:00Z")
        val later = Instant.parse("2026-08-25T09:00:00Z")

        val metas =
            sut.appendCalendarTables(
                out,
                listOf(
                    CalendarEvent("cal-b", "2", "Planungsmeeting", later, later.plusSeconds(3600), null, null),
                    CalendarEvent("cal-a", "1", "Retro | mit Pipe", first, first.plusSeconds(1800), "Beschreibung", "Raum 3"),
                    CalendarEvent("cal-a", "3", "Fruehschicht", first.plusSeconds(7200), first.plusSeconds(9000), "   ", null),
                ),
                titlesByCalendar = mapOf("cal-a" to "Team-Kalender", "cal-b" to "   "),
            )

        assertEquals(listOf("cal-b", "Team-Kalender"), metas.map { it.title })
        assertEquals(listOf(1, 2), metas.map { it.eventCount })
        assertTrue(out.contains("Tabelle: Kalender \"Team-Kalender\""))
        assertTrue(out.contains("Tabelle: Kalender \"cal-b\""))
        assertTrue(out.contains("Retro \\| mit Pipe"))
        assertTrue(out.contains("Eintrag 2: Fruehschicht am"))
        assertTrue(out.contains(". Beschreibung."))
        assertFalse(out.contains(". ."))
    }

    @Test fun `an empty event list produces no calendar output`() {
        val out = StringBuilder()

        assertTrue(sut.appendCalendarTables(out, emptyList(), emptyMap()).isEmpty())
        assertTrue(sut.appendCalendarTables(out, emptyList()).isEmpty())
        assertEquals("", out.toString())
    }

    @Test fun `markdown row detection keeps table lines untouched`() {
        val out = text("<p>| plain pipe line</p><p>|</p><p>text with   spaces</p>")

        assertTrue(out.contains("| plain pipe line"))
        assertTrue(out.contains("text with spaces"))
    }

    @Test fun `a header row of a nested table is not dropped from the outer table`() {
        val html =
            """
            <table>
              <tr><td>outside</td><td>row two</td></tr>
              <tr><td colspan="2"><table><thead><tr><th>innen</th></tr></thead><tbody><tr><td>x</td></tr></tbody></table></td></tr>
            </table>
            """.trimIndent()

        val out = text(html)

        assertTrue(out.contains("| outside | row two |"))
    }

    @Test fun `a two slot row that ends early keeps the morning entry only`() {
        val html =
            rotation(
                "<tr><th></th><th>Mo</th><th>Di</th><th>Mi</th></tr>" +
                    "<tr><th>Info</th><th>Vormittag</th><th>Nachmittag</th><th>Vormittag</th><th>Nachmittag</th><th>Vormittag</th><th>Nachmittag</th><th>Kommentar</th></tr>",
                listOf("<tr><td>24.08.2026</td><td>Ana</td></tr>"),
            )

        val out = text(html)

        assertTrue(out.contains("| 2026-08-24 (Montag) | Vormittag | Ana |"))
        assertFalse(out.contains("Nachmittag | Ana"))
        assertEquals(1, Regex("Eintrag: ").findAll(out).count())
    }

    @Test fun `the tag and format constants of the linearizer are the documented ones`() {
        assertEquals(setOf("th", "td"), CompanionAccessor.read(ConfluenceHtmlLinearizer::class.java, "getHEADER_TAGS"))
        assertEquals(setOf("td", "th"), CompanionAccessor.read(ConfluenceHtmlLinearizer::class.java, "getCELL_TAGS"))
        assertEquals(
            setOf("thead", "tbody", "tfoot"),
            CompanionAccessor.read(ConfluenceHtmlLinearizer::class.java, "getSECTION_TAGS"),
        )
        assertEquals(
            setOf("h1", "h2", "h3", "h4", "h5", "h6"),
            CompanionAccessor.read(ConfluenceHtmlLinearizer::class.java, "getHEADING_TAGS"),
        )
        assertNotNull(CompanionAccessor.read(ConfluenceHtmlLinearizer::class.java, "getDATE_FMT"))
        assertNotNull(CompanionAccessor.read(ConfluenceHtmlLinearizer::class.java, "getTIME_FMT"))
    }
}
