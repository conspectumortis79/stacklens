
package com.stacklens.application.service

import com.stacklens.application.service.search.SearchDispatcher
import com.stacklens.application.service.search.SearchRunContext
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.SearchResult
import java.util.concurrent.CompletableFuture

@ApplicationService
class SearchSourceDispatcher(
    private val sources: List<SearchSource>,
) : SearchDispatcher {
    override fun launchPhase1(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
        launch: (String, () -> Unit) -> CompletableFuture<*>,
    ): List<CompletableFuture<*>> =
        sources
            .filter { it.phase == SearchPhase.PHASE_1 && it.isEnabled(ctx) }
            .map { source -> launch(source.name) { source.search(ctx, isCancelled, publish) } }

    override fun launchPhase2(
        ctx: SearchRunContext,
        isCancelled: (Long) -> Boolean,
        publish: (List<SearchResult>) -> Unit,
        launch: (String, () -> Unit) -> CompletableFuture<*>,
    ): List<CompletableFuture<*>> =
        sources
            .filter { it.phase == SearchPhase.PHASE_2 && it.isEnabled(ctx) }
            .map { source -> launch(source.name) { source.search(ctx, isCancelled, publish) } }
}
