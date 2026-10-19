package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.domain.model.SearchSession
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SearchSessionRepository :
    JpaRepository<SearchSession, Long>,
    SearchSessionPort {
    override fun findAllByOrderByCreatedAtDesc(): List<SearchSession>

    override fun findRecentSearchSessions(limit: Int): List<SearchSession> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM SearchSession e WHERE e.id = :id")
    override fun findSessionById(
        @Param("id") id: Long,
    ): SearchSession?

    override fun saveSession(session: SearchSession): SearchSession = save(session)

    override fun sessionExistsById(id: Long): Boolean = existsById(id)

    override fun deleteSessionById(id: Long) = deleteById(id)

    override fun deleteAllSessions(sessions: Iterable<SearchSession>) = deleteAll(sessions)

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SearchSession s WHERE s.id IN :ids")
    override fun deleteSessionsByIdIn(
        @Param("ids") ids: List<Long>,
    ): Long
}
