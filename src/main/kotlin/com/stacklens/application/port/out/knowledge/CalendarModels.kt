package com.stacklens.application.port.out.knowledge

import java.time.Instant

data class CalendarEvent(
    val calendarId: String,
    val id: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val description: String? = null,
    val location: String? = null,
)

data class CalendarMeta(
    val calendarId: String,
    val title: String,
    val eventCount: Int,
)

data class CalendarReference(
    val calendarId: String,
    val title: String,
    val macroName: String,
)
