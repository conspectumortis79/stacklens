package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.application.port.out.knowledge.CalendarReference
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.stereotype.Component

@Component
class ConfluenceCalendarMacroDetector {
    fun detect(rawHtml: String): List<CalendarReference> {
        if (rawHtml.isBlank()) return emptyList()
        val doc = Jsoup.parseBodyFragment(rawHtml)
        val macros =
            doc.body().getAllElements().filter { el ->
                el.hasAttr("ac:name") && el.attr("ac:name") in CALENDAR_MACRO_NAMES
            }
        return macros
            .mapNotNull { toCalendarReference(it) }
            .distinctBy { it.calendarId }
    }

    private fun toCalendarReference(macro: Element): CalendarReference? {
        val calendarId =
            parameterValue(macro, "id")
                ?: return null
        val title = parameterValue(macro, "title") ?: ""
        return CalendarReference(
            calendarId = calendarId,
            title = title,
            macroName = macro.attr("ac:name"),
        )
    }

    private fun parameterValue(
        macro: Element,
        name: String,
    ): String? {
        val parameter =
            macro.getAllElements().firstOrNull { el ->
                (el.tagName() == "ac:parameter" || el.tagName() == "parameter") &&
                    el.attr("ac:name") == name
            } ?: return null
        val value = parameter.text().trim()
        return value.takeIf { it.isNotBlank() }
    }

    private companion object {
        val CALENDAR_MACRO_NAMES =
            setOf(
                "calendar",
                "team-calendar",
                "team-calendars",
                "team-calendar-pro",
            )
    }
}
