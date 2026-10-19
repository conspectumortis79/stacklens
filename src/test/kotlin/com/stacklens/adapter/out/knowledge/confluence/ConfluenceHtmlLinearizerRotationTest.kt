package com.stacklens.adapter.out.knowledge.confluence

import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class ConfluenceHtmlLinearizerRotationTest {
    private val sut = ConfluenceHtmlLinearizer()

    @Test
    fun `linearizes a German rotation-schedule with Mo-Fr header and per-day slots`() {
        val html =
            """
            <h2>Support-Kalender</h2>
            <table>
              <tr><th></th><th>Mo</th><th>Di</th><th>Mi</th><th>Do</th><th>Fr</th><th>Infos</th></tr>
              <tr><th></th><th>Support</th><th>bis 18:00 Uhr</th><th>Support</th><th>bis 18:00 Uhr</th><th>Support</th><th>bis 18:00 Uhr</th><th>Support</th><th>bis 18:00 Uhr</th><th>Support</th><th>bis 18:00 Uhr</th><th></th></tr>
              <tr><td>24.08.2026</td><td>Gregor Peter</td><td>Philipp Holzapfel</td><td>Max Pfister</td><td>Gregor Peter</td><td>Maximilian Reitz</td><td>Christian Schaberg</td><td>Max Pfister</td><td>Gregor Peter</td><td>Philipp Holzapfel</td><td>Maximilian Reitz</td><td>Urlaub Martin</td></tr>
            </table>
            """.trimIndent()
        val out = sut.linearize(html)

        assertThat(out.text).contains("Tabelle: Rotations-Schedule")
        assertThat(out.text).contains("| 2026-08-24 (Montag) | Vormittag | Gregor Peter |")
        assertThat(out.text).contains("| 2026-08-24 (Montag) | Nachmittag | Philipp Holzapfel |")
        assertThat(out.text).contains("| 2026-08-25 (Dienstag) | Vormittag | Max Pfister |")
        assertThat(out.text).contains(
            "Eintrag: Gregor Peter hat am 2026-08-24 (Montag Vormittag) Support.",
        )
        assertThat(out.text).contains(
            "Eintrag: Max Pfister hat am 2026-08-25 (Dienstag Vormittag) Support.",
        )
    }

    @Test
    fun `does not match a regular table without weekday header`() {
        val html =
            """
            <table>
              <tr><th>Spalte A</th><th>Spalte B</th></tr>
              <tr><td>1</td><td>2</td></tr>
            </table>
            """.trimIndent()
        val out = sut.linearize(html)
        assertThat(out.text).doesNotContain("Rotations-Schedule")
    }

    @Test
    fun `does not match a table with weekday header but no date rows`() {
        val html =
            """
            <table>
              <tr><th>Mo</th><th>Di</th></tr>
              <tr><td>free</td><td>free</td></tr>
            </table>
            """.trimIndent()
        val out = sut.linearize(html)
        assertThat(out.text).doesNotContain("Rotations-Schedule")
    }

    @Test
    fun `recognizes English weekday headers`() {
        val html =
            """
            <table>
              <tr><th>Mon</th><th>Tue</th><th>Wed</th><th>Thu</th><th>Fri</th></tr>
              <tr><td>2026-08-24</td><td>Alice</td><td>Bob</td><td>Carol</td><td>Dave</td><td>Eve</td></tr>
            </table>
            """.trimIndent()
        val out = sut.linearize(html)
        assertThat(out.text).contains("Tabelle: Rotations-Schedule")
        assertThat(out.text).contains("Eintrag: Alice hat am 2026-08-24 (Montag Vormittag) Support.")
        assertThat(out.text).contains("Eintrag: Eve hat am 2026-08-28 (Freitag Vormittag) Support.")
    }

    @Test
    fun `falls back to single-slot per day when no afternoon column is present`() {
        val html =
            """
            <table>
              <tr><th>Mo</th><th>Di</th><th>Mi</th></tr>
              <tr><td>24.08.2026</td><td>Alice</td><td>Bob</td><td>Carol</td></tr>
            </table>
            """.trimIndent()
        val out = sut.linearize(html)
        assertThat(out.text).contains("Tabelle: Rotations-Schedule")
        assertThat(out.text).doesNotContain("Nachmittag")
        assertThat(out.text).contains("Eintrag: Alice hat am 2026-08-24 (Montag Vormittag) Support.")
    }

    @Test
    fun `rotation entries show up in TableMeta with rotation caption`() {
        val html =
            """
            <table>
              <tr><th>Mo</th><th>Di</th><th>Mi</th><th>Do</th><th>Fr</th></tr>
              <tr><th>Support</th><th>bis 18:00</th><th>Support</th><th>bis 18:00</th><th>Support</th></tr>
              <tr><td>24.08.2026</td><td>Alice</td><td>Bob</td><td>Carol</td><td>Dave</td><td>Eve</td></tr>
              <tr><td>31.08.2026</td><td>X</td><td>Y</td><td>Z</td><td>U</td><td>V</td></tr>
            </table>
            """.trimIndent()
        val out = sut.linearize(html)
        val rotation = out.tables.firstOrNull { it.caption.startsWith("Rotation") }
        assertThat(rotation).isNotNull
        assertThat(rotation!!.rowCount).isEqualTo(10)
    }

    @Test
    fun `direct unit call on table element returns TableMeta for rotation schedule`() {
        val table =
            Jsoup
                .parseBodyFragment(
                    """
                    <table>
                      <tr><th>Mo</th><th>Di</th><th>Mi</th></tr>
                      <tr><td>2026-08-24</td><td>Alice</td><td>Bob</td><td>Carol</td></tr>
                    </table>
                    """.trimIndent(),
                ).body()
                .selectFirst("table")!!
        val out = StringBuilder()
        val meta = sut.linearizeRotationSchedule(table, out)
        assertThat(meta).isNotNull
        assertThat(out.toString()).contains("Alice")
        assertThat(out.toString()).contains("2026-08-24")
    }

    @Test
    fun `non-date-prefixed rows are ignored`() {
        val html =
            """
            <table>
              <tr><th>Mo</th><th>Di</th></tr>
              <tr><td>2026-08-24</td><td>Alice</td><td>Bob</td></tr>
              <tr><td>not-a-date</td><td>X</td><td>Y</td></tr>
            </table>
            """.trimIndent()
        val out = sut.linearize(html)
        assertThat(out.text).contains("Alice")
        assertThat(out.text).doesNotContain("Eintrag: X hat am")
    }
}
