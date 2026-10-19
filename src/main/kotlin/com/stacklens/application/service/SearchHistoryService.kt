
package com.stacklens.application.service

import com.stacklens.application.collaboration.SearchSessionHistory
import com.stacklens.application.port.inbound.search.SearchHistoryUseCase
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession

@ApplicationService
class SearchHistoryService(
    private val sessionRepo: SearchSessionPort,
    private val resultRepo: SearchResultPort,
    private val tx: TransactionPort,
    private val cancellationRegistry: SearchCancellationRegistry,
    private val clock: ClockPort,
) : SearchHistoryUseCase,
    SearchSessionHistory {
    override fun sessionExists(sessionId: Long): Boolean = sessionRepo.sessionExistsById(sessionId)

    override fun findSession(sessionId: Long): SearchSession? = sessionRepo.findSessionById(sessionId)

    override fun listSessions(): List<SearchSession> = sessionRepo.findRecentSearchSessions(AnalysisConstants.MAX_RECENT_SESSIONS)

    override fun getSessionResultCount(sessionId: Long): Long = resultRepo.countBySessionId(sessionId)

    override fun cancelSession(sessionId: Long): SearchSession {
        cancellationRegistry.cancel(sessionId)
        val session =
            sessionRepo.findSessionById(sessionId)
                ?: throw NoSuchElementException("Session $sessionId not found")
        session.status = SessionStatus.CANCELLED
        session.finishedAt = clock.localDateTime()
        return sessionRepo.saveSession(session)
    }

    override fun deleteSession(sessionId: Long) {
        tx.inTransaction {
            resultRepo.deleteBySessionIdIn(listOf(sessionId))

            sessionRepo.deleteSessionsByIdIn(listOf(sessionId))
        }
    }

    override fun deleteAllSessions(): Int =
        tx.inTransaction {
            val sessions = sessionRepo.findAllByOrderByCreatedAtDesc()
            if (sessions.isEmpty()) return@inTransaction 0
            val sessionIds = sessions.map { it.id }
            resultRepo.deleteBySessionIdIn(sessionIds)
            sessionRepo.deleteSessionsByIdIn(sessionIds)
            sessions.size
        }

    override fun getResults(
        sessionId: Long,
        sourceType: String?,
    ): List<SearchResult> =
        if (sourceType != null) {
            val parsed =
                runCatching {
                    SourceType.valueOf(sourceType)
                }.getOrNull()
            if (parsed == null) emptyList() else resultRepo.findBySessionIdAndSourceType(sessionId, parsed)
        } else {
            resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(sessionId)
        }
}
