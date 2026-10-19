package com.stacklens.application.port.out.knowledge

data class LinearizedPage(
    val text: String,
    val tables: List<TableMeta>,
    val calendars: List<CalendarMeta> = emptyList(),
)
