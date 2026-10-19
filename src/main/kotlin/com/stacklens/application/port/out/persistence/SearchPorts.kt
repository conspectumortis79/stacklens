package com.stacklens.application.port.out

import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession

interface SearchSessionPort {
    fun findAllByOrderByCreatedAtDesc(): List<SearchSession>

    fun findRecentSearchSessions(limit: Int): List<SearchSession>

    fun findSessionById(id: Long): SearchSession?

    fun saveSession(session: SearchSession): SearchSession

    fun sessionExistsById(id: Long): Boolean

    fun deleteSessionById(id: Long)

    fun deleteAllSessions(sessions: Iterable<SearchSession>)

    fun deleteSessionsByIdIn(ids: List<Long>): Long
}

interface SearchResultPort {
    fun findBySessionIdOrderBySourceTypeAscCreatedAtAsc(sessionId: Long): List<SearchResult>

    fun findBySessionIdAndSourceType(
        sessionId: Long,
        sourceType: SourceType,
    ): List<SearchResult>

    fun countBySessionId(sessionId: Long): Long

    fun saveAllResults(results: Iterable<SearchResult>): List<SearchResult>

    fun deleteAllResults(results: Iterable<SearchResult>)

    fun deleteBySessionIdIn(sessionIds: List<Long>): Long

    fun findCappedBySessionId(
        sessionId: Long,
        perSourceLimit: Int,
    ): Map<SourceType, List<SearchResult>>
}
