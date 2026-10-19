package com.stacklens.adapter.out.knowledge.confluence

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfluenceHtmlLinearizerRotationBranchesTest {
    private val sut = ConfluenceHtmlLinearizer()

    private fun tableOf(html: String): Element = Jsoup.parse(html).selectFirst("table")!!

    private fun Element.withoutSections(): Element {
        children()
            .filter { it.tagName() in setOf("thead", "tbody", "tfoot") }
            .toList()
            .forEach { section ->
                section
                    .children()
                    .toList()
                    .forEach { row -> appendChild(row) }
                section.remove()
            }
        return this
    }

    @Test
    fun `rows outside of a section are collected directly from the table`() {
        val table =
            tableOf(
                """
                <table>
                  <tr><th>Woche</th><th>Mo</th><th>Di</th><th>Mi</th></tr>
                  <tr><td>2026-08-24</td><td>Ana</td><td>Ben</td><td>Cai</td></tr>
                </table>
                """.trimIndent(),
            ).withoutSections()
        val out = StringBuilder()

        val meta = sut.linearizeRotationSchedule(table, out)

        assertNotNull(meta)
        assertEquals(3, meta!!.rowCount)
        assertTrue(out.contains("| 2026-08-25 (Dienstag) | Vormittag | Ben |"))
    }

    @Test
    fun `a data row shorter than the header leaves the missing slots empty`() {
        val table =
            tableOf(
                """
                <table>
                  <tr><th>Woche</th><th>Mo</th><th>Di</th><th>Mi</th></tr>
                  <tr><th></th><th>Vormittag</th><th>Nachmittag</th><th>Vormittag</th><th>Nachmittag</th><th>Vormittag</th><th>Nachmittag</th><th>Kommentar</th></tr>
                  <tr><td>2026-08-24</td><td>Ana</td><td></td><td>Ben</td><td></td></tr>
                </table>
                """.trimIndent(),
            )
        val out = StringBuilder()

        val meta = sut.linearizeRotationSchedule(table, out)

        assertNotNull(meta)
        assertEquals(2, meta!!.rowCount)
        assertTrue(out.contains("| 2026-08-24 (Montag) | Vormittag | Ana |"))
        assertTrue(out.contains("| 2026-08-25 (Dienstag) | Vormittag | Ben |"))
        assertTrue(!out.contains("Nachmittag |"))
    }

    @Test
    fun `a schedule without any date prefixed row is not a rotation schedule`() {
        val table =
            tableOf(
                """
                <table>
                  <tr><th>Woche</th><th>Mo</th><th>Di</th><th>Mi</th></tr>
                  <tr><td>Kein Datum</td><td>Ana</td><td>Ben</td><td>Cai</td></tr>
                </table>
                """.trimIndent(),
            )

        assertNull(sut.linearizeRotationSchedule(table, StringBuilder()))
    }
}
