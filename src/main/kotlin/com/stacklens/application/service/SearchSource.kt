package com.stacklens.application.service

import com.stacklens.application.service.search.SearchRunContext
import com.stacklens.domain.model.SearchResult

enum class SearchPhase { PHASE_1, PHASE_2 }

interface SearchSource {
    val name: String

    val phase: SearchPhase

    fun isEnabled(ctx: SearchRunContext): Boolean

    fun search(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
    )
}
