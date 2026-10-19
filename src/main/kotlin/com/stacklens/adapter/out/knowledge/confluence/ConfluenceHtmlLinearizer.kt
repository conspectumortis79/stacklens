package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.application.port.out.knowledge.CalendarEvent
import com.stacklens.application.port.out.knowledge.CalendarMeta
import com.stacklens.application.port.out.knowledge.LinearizedPage
import com.stacklens.application.port.out.knowledge.TableMeta
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class ConfluenceHtmlLinearizer {
    fun linearize(rawHtml: String): LinearizedPage = linearize(rawHtml, emptyList())

    fun linearize(
        rawHtml: String,
        calendarEvents: List<CalendarEvent>,
    ): LinearizedPage = linearize(rawHtml, calendarEvents, emptyMap())

    fun linearize(
        rawHtml: String,
        calendarEvents: List<CalendarEvent>,
        calendarTitles: Map<String, String>,
    ): LinearizedPage {
        if (rawHtml.isBlank() && calendarEvents.isEmpty()) {
            return LinearizedPage("", emptyList(), emptyList())
        }
        val doc = Jsoup.parseBodyFragment(rawHtml)
        doc.outputSettings().prettyPrint(false)
        val out = StringBuilder()
        val tables = mutableListOf<TableMeta>()
        walk(doc.body(), out, tables)
        val calendarMetas = appendCalendarTables(out, calendarEvents, calendarTitles)
        return LinearizedPage(
            text = collapseWhitespace(out.toString()),
            tables = tables,
            calendars = calendarMetas,
        )
    }

    private fun walk(
        node: Node,
        out: StringBuilder,
        tables: MutableList<TableMeta>,
    ) {
        when (node) {
            is TextNode -> out.append(node.text().replace('\u00A0', ' '))
            is Element -> handleElement(node, out, tables)
            else -> { }
        }
    }

    private fun handleElement(
        element: Element,
        out: StringBuilder,
        tables: MutableList<TableMeta>,
    ) {
        when (element.tagName()) {
            "br" -> out.append('\n')
            "p", "div" -> {
                val inner = StringBuilder()
                walkChildren(element, inner, tables)
                val trimmed = inner.toString().trim()
                if (trimmed.isNotEmpty()) out.append(trimmed).append("\n\n")
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName()[1].digitToInt()
                out.append("#".repeat(level)).append(' ')
                walkChildren(element, out, tables)
                out.append("\n\n")
            }
            "li" -> {
                out.append("- ")
                walkChildren(element, out, tables)
                out.append('\n')
            }
            "table" -> {
                out.append('\n')
                val meta = linearizeTable(element, out)
                if (meta != null) tables += meta
                val rsMeta = linearizeRotationSchedule(element, out)
                if (rsMeta != null) tables += rsMeta
                out.append("\n\n")
            }
            "script", "style" -> { }
            else -> walkChildren(element, out, tables)
        }
    }

    private fun walkChildren(
        parent: Element,
        out: StringBuilder,
        tables: MutableList<TableMeta>,
    ) {
        for (child in parent.childNodes()) walk(child, out, tables)
    }

    private fun linearizeTable(
        table: Element,
        out: StringBuilder,
    ): TableMeta? {
        val caption = nearestCaption(table)

        val headerRow: Element =
            table.selectFirst("thead > tr")
                ?: table.selectFirst("tr")
                ?: return null

        val headers =
            headerRow
                .children()
                .filter { it.tagName() in HEADER_TAGS }
                .map { it.text().trim() }
        if (headers.isEmpty()) return null

        val allRows = collectRows(table)
        val headerIndex = allRows.indexOf(headerRow)
        val dataRows = if (headerIndex >= 0) allRows.drop(headerIndex + 1) else allRows

        out.append("Tabelle")
        if (caption.isNotBlank()) out.append(": ").append(caption)
        out.append('\n')
        out.append("| ").append(headers.joinToString(" | ")).append(" |\n")
        out.append("|").append(headers.joinToString("|") { "---" }).append("|\n")
        for (tr in dataRows) {
            val cells = rowCells(tr)
            out.append("| ").append(cells.joinToString(" | ")).append(" |\n")
        }

        if (caption.isNotBlank()) {
            out.append("Inhalt der Tabelle \"").append(caption).append("\":\n")
        }
        for ((idx, tr) in dataRows.withIndex()) {
            val cells = rowCells(tr)
            out.append("Zeile ").append(idx + 1).append(": ")
            out.append(buildRowSentence(headers, cells))
            out.append('\n')
        }

        return TableMeta(caption = caption, headers = headers, rowCount = dataRows.size)
    }

    private fun collectRows(table: Element): List<Element> {
        val sectioned =
            table
                .children()
                .filter { it.tagName() in SECTION_TAGS }
                .flatMap { section -> section.children().filter { it.tagName() == "tr" } }
        if (sectioned.isNotEmpty()) return sectioned
        return table.children().filter { it.tagName() == "tr" }
    }

    private fun rowCells(tr: Element): List<String> =
        tr
            .children()
            .filter { it.tagName() in CELL_TAGS }
            .map { it.text().trim() }

    internal fun linearizeRotationSchedule(
        table: Element,
        out: StringBuilder,
    ): TableMeta? {
        val rows = collectRows(table)
        if (rows.size < 2) return null
        val allCells = rows.map { rowCells(it) }
        val dayColumns = detectDayColumns(allCells.first())
        if (dayColumns.size < 3) return null

        val dataRows =
            allCells.drop(1).mapNotNull { row ->
                if (row.isEmpty()) null else parseDate(row.first())?.let { it to row }
            }
        if (dataRows.isEmpty()) return null

        val caption = nearestCaption(table)

        val secondRowCells = allCells[1]
        val numDayColumns = dayColumns.size
        val isTwoSlotSchedule = secondRowCells.size >= (numDayColumns + 1) * 2

        out.append("Tabelle: Rotations-Schedule")
        if (caption.isNotBlank()) out.append(" — ").append(caption)
        out.append('\n')
        out.append("| Datum | Slot | Person |\n")
        out.append("|---|---|---|\n")

        var emitted = 0
        val perShift = mutableListOf<String>()
        for ((date, row) in dataRows) {
            val dateStr = date.format(DATE_FMT)
            for ((dayOrdinal, dayCol) in dayColumns.withIndex()) {
                val dayLabel = dayCol.second
                val slots = mutableListOf<Pair<String, String>>()
                val slotStride = if (isTwoSlotSchedule) 2 else 1
                val baseIdx = 1 + dayOrdinal * slotStride
                val shiftedDate = date.plusDays(dayOrdinal.toLong())
                val shiftedDateStr = shiftedDate.format(DATE_FMT)
                val cell1 = row.getOrNull(baseIdx)?.takeIf { it.isNotBlank() }
                if (cell1 != null) slots += "Vormittag" to cell1
                if (isTwoSlotSchedule) {
                    val cell2 = row.getOrNull(baseIdx + 1)?.takeIf { it.isNotBlank() }
                    if (cell2 != null) slots += "Nachmittag" to cell2
                }
                for ((slotLabel, person) in slots) {
                    out
                        .append("| ")
                        .append(shiftedDateStr)
                        .append(" (")
                        .append(dayLabel)
                        .append(") | ")
                        .append(slotLabel)
                        .append(" | ")
                        .append(person.escapePipe())
                        .append(" |\n")
                    perShift += "Eintrag: $person hat am $shiftedDateStr ($dayLabel $slotLabel) Support."
                    emitted++
                }
            }
        }
        if (emitted == 0) return null
        out.append('\n')
        out.append("Inhalt des Rotations-Schedules")
        if (caption.isNotBlank()) out.append(" — ").append(caption)
        out.append(":\n")
        perShift.forEach { out.append(it).append('\n') }

        return TableMeta(
            caption = if (caption.isNotBlank()) "Rotation: $caption" else "Rotations-Schedule",
            headers = listOf("Datum", "Slot", "Person"),
            rowCount = emitted,
        )
    }

    private fun detectDayColumns(headerCells: List<String>): List<Pair<Int, String>> {
        val dayAliases =
            mapOf(
                "mo" to "Montag",
                "mon" to "Montag",
                "monday" to "Montag",
                "di" to "Dienstag",
                "tue" to "Dienstag",
                "tues" to "Dienstag",
                "tuesday" to "Dienstag",
                "mi" to "Mittwoch",
                "wed" to "Mittwoch",
                "weds" to "Mittwoch",
                "wednesday" to "Mittwoch",
                "do" to "Donnerstag",
                "thu" to "Donnerstag",
                "thur" to "Donnerstag",
                "thurs" to "Donnerstag",
                "thursday" to "Donnerstag",
                "fr" to "Freitag",
                "fri" to "Freitag",
                "friday" to "Freitag",
            )
        val result = mutableListOf<Pair<Int, String>>()
        for ((idx, cell) in headerCells.withIndex()) {
            val key =
                cell
                    .trim()
                    .lowercase()
                    .split(Regex("[^a-zäöü]+"))
                    .filter { it.isNotBlank() }
                    .firstOrNull() ?: continue
            val label = dayAliases[key] ?: continue
            result += idx to label
        }
        return result
    }

    private fun parseDate(s: String): java.time.LocalDate? {
        val trimmed = s.trim()
        val patterns = listOf("dd.MM.yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy")
        for (p in patterns) {
            try {
                return java.time.LocalDate.parse(
                    trimmed,
                    java.time.format.DateTimeFormatter
                        .ofPattern(p),
                )
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun buildRowSentence(
        headers: List<String>,
        cells: List<String>,
    ): String {
        val pairs = headers.zip(cells).filter { (_, v) -> v.isNotBlank() }
        return when (pairs.size) {
            0 -> "(leere Zeile)"
            1 -> "${pairs[0].first} = ${pairs[0].second}."
            else -> {
                val (firstKey, firstVal) = pairs.first()
                val rest = pairs.drop(1).joinToString(", ") { (k, v) -> "$k $v" }
                "$firstKey $firstVal, $rest."
            }
        }
    }

    internal fun appendCalendarTables(
        out: StringBuilder,
        events: List<CalendarEvent>,
        titlesByCalendar: Map<String, String> = emptyMap(),
    ): List<CalendarMeta> {
        if (events.isEmpty()) return emptyList()
        val grouped =
            events
                .groupBy { it.calendarId }
                .mapValues { (_, list) ->
                    list.sortedBy { it.start }
                }
        val metas = mutableListOf<CalendarMeta>()
        for ((calendarId, calEvents) in grouped) {
            val title =
                titlesByCalendar[calendarId]?.takeIf { it.isNotBlank() }
                    ?: calendarId
            out.append("\nTabelle: Kalender \"").append(title).append("\"\n")
            out.append("| Datum | Uhrzeit | Titel | Beschreibung |\n")
            out.append("|---|---|---|---|\n")
            for (e in calEvents) {
                val date = DATE_FMT.format(e.start.atZone(ZoneId.systemDefault()))
                val start = TIME_FMT.format(e.start.atZone(ZoneId.systemDefault()))
                val end = TIME_FMT.format(e.end.atZone(ZoneId.systemDefault()))
                out
                    .append("| ")
                    .append(date)
                    .append(" | ")
                    .append(start)
                    .append("–")
                    .append(end)
                    .append(" | ")
                    .append(e.title.escapePipe())
                    .append(" | ")
                    .append((e.description ?: "").escapePipe())
                    .append(" |\n")
            }
            out.append("Inhalt des Kalenders \"").append(title).append("\":\n")
            for ((idx, e) in calEvents.withIndex()) {
                val date = DATE_FMT.format(e.start.atZone(ZoneId.systemDefault()))
                val start = TIME_FMT.format(e.start.atZone(ZoneId.systemDefault()))
                val end = TIME_FMT.format(e.end.atZone(ZoneId.systemDefault()))
                out
                    .append("Eintrag ")
                    .append(idx + 1)
                    .append(": ")
                    .append(e.title)
                    .append(" am ")
                    .append(date)
                    .append(" von ")
                    .append(start)
                    .append(" bis ")
                    .append(end)
                e.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    out.append(". ").append(desc)
                }
                out.append(".\n")
            }
            metas += CalendarMeta(calendarId = calendarId, title = title, eventCount = calEvents.size)
        }
        return metas
    }

    private fun String.escapePipe(): String = this.replace("|", "\\|")

    private fun nearestCaption(table: Element): String {
        var prev: Element? = table.previousElementSibling()
        while (prev != null) {
            if (prev.tagName() in HEADING_TAGS) return prev.text().trim()
            prev = prev.previousElementSibling()
        }
        table.selectFirst("caption")?.let { return it.text().trim() }
        return ""
    }

    private fun collapseWhitespace(s: String): String {
        val collapsed =
            s
                .lineSequence()
                .map { line ->
                    if (isTableRow(line)) line else line.replace(Regex("[ \\t]+"), " ")
                }.joinToString("\n")
        return collapsed.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length >= 3
    }

    private companion object {
        val HEADER_TAGS = setOf("th", "td")
        val CELL_TAGS = setOf("td", "th")
        val SECTION_TAGS = setOf("thead", "tbody", "tfoot")
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
