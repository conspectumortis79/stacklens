package com.stacklens.application.port.inbound.search

interface SearchLoadMoreUseCase {
    fun loadMoreConfluence(sessionId: Long): SearchLoadMoreResult

    data class SearchLoadMoreResult(
        val addedCount: Long,
        val totalCount: Long,
        val hasMore: Boolean,
    )
}
