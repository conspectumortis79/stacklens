package com.stacklens.application.service.search

import com.stacklens.application.service.search.SearchRunContext
import com.stacklens.domain.model.SearchResult
import java.util.concurrent.CompletableFuture

interface SearchDispatcher {
    fun launchPhase1(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
        launch: (String, () -> Unit) -> CompletableFuture<*>,
    ): List<CompletableFuture<*>>

    fun launchPhase2(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
        launch: (String, () -> Unit) -> CompletableFuture<*>,
    ): List<CompletableFuture<*>>
}
