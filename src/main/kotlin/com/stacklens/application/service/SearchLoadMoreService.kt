package com.stacklens.application.service

import com.stacklens.application.collaboration.SearchSessionHistory
import com.stacklens.application.port.inbound.search.SearchLoadMoreUseCase
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult

@ApplicationService
class SearchLoadMoreService(
    private val searchHistory: SearchSessionHistory,
    private val searchConfigProvider: SearchConfigResolver,
    private val resultRepo: SearchResultPort,
    private val sessionRepo: SearchSessionPort,
    private val confluenceSearch: ConfluenceSearchPort,
) : SearchLoadMoreUseCase {
    private val pageSize = 25

    override fun loadMoreConfluence(sessionId: Long): SearchLoadMoreUseCase.SearchLoadMoreResult {
        val session =
            searchHistory.findSession(sessionId)
                ?: throw IllegalArgumentException("session $sessionId not found")
        if (session.status != SessionStatus.COMPLETED) {
            throw IllegalStateException("session $sessionId is not completed (status=${session.status})")
        }

        val configs = searchConfigProvider.resolveConfluenceConfigs(session)
        if (configs.isEmpty()) {
            return SearchLoadMoreUseCase.SearchLoadMoreResult(
                addedCount = 0L,
                totalCount = searchHistory.getSessionResultCount(sessionId),
                hasMore = false,
            )
        }

        val keywords = session.keywordList()
        val websiteFilters = session.confluenceWebsiteList()

        val existing = resultRepo.findBySessionIdAndSourceType(sessionId, SourceType.CONFLUENCE)
        val existingOffsetMap: Map<Pair<String, String>, Int> =
            HashMap<Pair<String, String>, Int>().apply {
                for (r in existing) {
                    val key = Pair(r.keyword, r.configName.orEmpty())
                    this[key] = (this[key] ?: 0) + 1
                }
            }

        val newResults = mutableListOf<SearchResult>()
        val newResultsByKey: HashMap<Pair<String, String>, Int> = HashMap()
        for (keyword in keywords) {
            for (config in configs) {
                val off = existingOffsetMap[Pair(keyword, config.name)] ?: 0
                val fetched = confluenceSearch.searchKeyword(config, keyword, websiteFilters, sessionId, off)
                newResults += fetched
                val key = Pair(keyword, config.name)
                newResultsByKey[key] = (newResultsByKey[key] ?: 0) + fetched.size
            }
        }

        if (newResults.isNotEmpty()) {
            resultRepo.saveAllResults(newResults)
            val updatedSession = sessionRepo.findSessionById(sessionId)
            if (updatedSession != null) {
                updatedSession.resultCount = searchHistory.getSessionResultCount(sessionId).toInt()
                sessionRepo.saveSession(updatedSession)
            }
        }

        val totalCount = searchHistory.getSessionResultCount(sessionId)
        val hasMore = newResultsByKey.values.any { it >= pageSize }
        return SearchLoadMoreUseCase.SearchLoadMoreResult(
            addedCount = newResults.size.toLong(),
            totalCount = totalCount,
            hasMore = hasMore,
        )
    }
}
