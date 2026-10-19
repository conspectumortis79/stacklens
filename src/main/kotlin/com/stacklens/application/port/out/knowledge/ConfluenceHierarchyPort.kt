package com.stacklens.application.port.out.knowledge

import com.stacklens.domain.model.ConfluenceConfig

interface ConfluenceHierarchyPort {
    fun ensureHostMatches(
        config: ConfluenceConfig,
        rootPageUrl: String,
    )

    fun walkHierarchy(
        config: ConfluenceConfig,
        rootPageUrl: String,
        includeChildren: Boolean,
        maxDepth: Int,
        maxPages: Int = 2000,
    ): Sequence<ConfluencePageSnapshot>
}

data class ConfluencePageSnapshot(
    val pageId: String,
    val title: String,
    val sourceUri: String,
    val plainText: String,
    val depth: Int,
    val tables: List<TableMeta> = emptyList(),
    val calendars: List<CalendarMeta> = emptyList(),
)

data class TableMeta(
    val caption: String,
    val headers: List<String>,
    val rowCount: Int,
)
