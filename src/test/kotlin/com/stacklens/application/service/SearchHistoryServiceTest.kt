package com.stacklens.application.service

import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class SearchHistoryServiceTest {
    private val sessionRepo = mock<SearchSessionPort>()
    private val resultRepo = mock<SearchResultPort>()
    private val tx =
        object : TransactionPort {
            override fun <T> inTransaction(block: () -> T): T = block()

            override fun afterCommit(block: () -> Unit) = block()
        }
    private val cancellationRegistry = SearchCancellationRegistry()

    private val service =
        SearchHistoryService(
            sessionRepo,
            resultRepo,
            tx,
            cancellationRegistry,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun makeSession(
        id: Long = 1L,
        status: SessionStatus = SessionStatus.PENDING,
    ) = SearchSession(
        id = id,
        keywords = "kw",
        status = status,
        createdAt = LocalDateTime.now(),
    )

    private fun makeResult(sessionId: Long = 1L) =
        SearchResult(
            sessionId = sessionId,
            keyword = "kw",
            sourceType = SourceType.GITLAB,
        )

    @Test
    fun `sessionExists delegates to port`() {
        whenever(sessionRepo.sessionExistsById(1L)).thenReturn(true)
        assertTrue(service.sessionExists(1L))
        whenever(sessionRepo.sessionExistsById(2L)).thenReturn(false)
        assertTrue(!service.sessionExists(2L))
    }

    @Test
    fun `findSession delegates to port`() {
        val session = makeSession(id = 1L)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        assertEquals(session, service.findSession(1L))
        whenever(sessionRepo.findSessionById(2L)).thenReturn(null)
        assertNull(service.findSession(2L))
    }

    @Test
    fun `listSessions delegates to port`() {
        val sessions = listOf(makeSession(id = 1L), makeSession(id = 2L))
        whenever(sessionRepo.findRecentSearchSessions(200)).thenReturn(sessions)
        assertEquals(sessions, service.listSessions())
    }

    @Test
    fun `getSessionResultCount delegates to port`() {
        whenever(resultRepo.countBySessionId(7L)).thenReturn(42L)
        assertEquals(42L, service.getSessionResultCount(7L))
    }

    @Test
    fun `cancelSession throws when session not found`() {
        whenever(sessionRepo.findSessionById(99L)).thenReturn(null)
        assertThrows<NoSuchElementException> { service.cancelSession(99L) }
    }

    @Test
    fun `cancelSession marks session as CANCELLED`() {
        val session = makeSession(id = 1L, status = SessionStatus.RUNNING)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(session)
        whenever(sessionRepo.saveSession(any())).thenReturn(session)

        val result = service.cancelSession(1L)

        assertEquals(SessionStatus.CANCELLED, result.status)
        assertNotNull(result.finishedAt)
        verify(sessionRepo).saveSession(session)
        assertTrue(cancellationRegistry.isCancelled(1L))
    }

    @Test
    fun `deleteSession removes results then session`() {
        service.deleteSession(3L)

        verify(resultRepo).deleteBySessionIdIn(listOf(3L))
        verify(sessionRepo).deleteSessionsByIdIn(listOf(3L))
    }

    @Test
    fun `deleteAllSessions removes all sessions and their results`() {
        val s1 = makeSession(id = 1L)
        val s2 = makeSession(id = 2L)
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(s1, s2))

        val count = service.deleteAllSessions()

        assertEquals(2, count)
        verify(resultRepo).deleteBySessionIdIn(listOf(1L, 2L))
        verify(sessionRepo).deleteSessionsByIdIn(listOf(1L, 2L))
    }

    @Test
    fun `deleteAllSessions returns 0 when no sessions`() {
        whenever(sessionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())
        val count = service.deleteAllSessions()
        assertEquals(0, count)
        verify(resultRepo, never()).deleteBySessionIdIn(any())
        verify(sessionRepo, never()).deleteSessionsByIdIn(any())
    }

    @Test
    fun `getResults with null sourceType returns all results ordered`() {
        val results = listOf(makeResult())
        whenever(resultRepo.findBySessionIdOrderBySourceTypeAscCreatedAtAsc(1L)).thenReturn(results)
        assertEquals(results, service.getResults(1L, null))
    }

    @Test
    fun `getResults with sourceType filters by type`() {
        val results = listOf(makeResult())
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.GITLAB)).thenReturn(results)
        assertEquals(results, service.getResults(1L, "GITLAB"))
    }

    @Test
    fun `getResults with invalid sourceType returns empty list`() {
        val result = service.getResults(1L, "BOGUS_INVALID_SOURCE_TYPE")
        assertTrue(result.isEmpty())
    }
}
