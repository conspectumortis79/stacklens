package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.LoggingFixStageColumn
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class LoggingFixAnalysisPartialUpdaterTest {
    private val entityManager: EntityManager = mock()
    private val query: Query = mock()

    private fun updater(): LoggingFixAnalysisPartialUpdater {
        val u = LoggingFixAnalysisPartialUpdater()
        val f = LoggingFixAnalysisPartialUpdater::class.java.getDeclaredField("entityManager")
        f.isAccessible = true
        f.set(u, entityManager)
        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.executeUpdate()).thenReturn(1)
        return u
    }

    @Test
    fun `updateStageDiagnose executes jpql`() {
        val u = updater()
        assertEquals(1, u.updateStageDiagnose(7L, "value"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stageDiagnose = :value WHERE e.id = :id")
        verify(query).setParameter("value", "value")
        verify(query).setParameter("id", 7L)
    }

    @Test
    fun `updateStagePlan executes jpql`() {
        val u = updater()
        assertEquals(1, u.updateStagePlan(7L, "value"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stagePlan = :value WHERE e.id = :id")
    }

    @Test
    fun `updateStageCode executes jpql`() {
        val u = updater()
        assertEquals(1, u.updateStageCode(7L, "value"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stageCode = :value WHERE e.id = :id")
    }

    @Test
    fun `updateStageTests executes jpql`() {
        val u = updater()
        assertEquals(1, u.updateStageTests(7L, "value"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stageTests = :value WHERE e.id = :id")
    }

    @Test
    fun `appendStageOutput accepts every allowed column`() {
        val u = updater()
        assertEquals(1, u.appendStageOutput(7L, LoggingFixStageColumn.DIAGNOSE, "chunk"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stageDiagnose = COALESCE(e.stageDiagnose, '') || :chunk WHERE e.id = :id")
        assertEquals(1, u.appendStageOutput(7L, LoggingFixStageColumn.PLAN, "chunk"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stagePlan = COALESCE(e.stagePlan, '') || :chunk WHERE e.id = :id")
        assertEquals(1, u.appendStageOutput(7L, LoggingFixStageColumn.CODE, "chunk"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stageCode = COALESCE(e.stageCode, '') || :chunk WHERE e.id = :id")
        assertEquals(1, u.appendStageOutput(7L, LoggingFixStageColumn.TESTS, "chunk"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.stageTests = COALESCE(e.stageTests, '') || :chunk WHERE e.id = :id")
    }

    @Test
    fun `updateStatus executes jpql`() {
        val u = updater()
        assertEquals(1, u.updateStatus(7L, "RUNNING"))
        verify(entityManager).createQuery("UPDATE LoggingFixAnalysis e SET e.status = :status WHERE e.id = :id")
        verify(query).setParameter("status", "RUNNING")
    }

    @Test
    fun `updateStatusWithErrorMessage executes jpql`() {
        val u = updater()
        val ts = LocalDateTime.of(2025, 1, 1, 10, 0)
        assertEquals(1, u.updateStatusWithErrorMessage(7L, "ERROR", "boom", ts))
        verify(entityManager).createQuery(
            "UPDATE LoggingFixAnalysis e " +
                "SET e.status = :status, e.errorMessage = :errorMessage, e.completedAt = :completedAt " +
                "WHERE e.id = :id",
        )
        verify(query).setParameter("completedAt", ts)
    }

    @Test
    fun `updateStatusAndCompletedAt executes jpql`() {
        val u = updater()
        val ts = LocalDateTime.of(2025, 1, 1, 10, 0)
        assertEquals(1, u.updateStatusAndCompletedAt(7L, "DONE", ts))
        verify(entityManager).createQuery(
            "UPDATE LoggingFixAnalysis e " +
                "SET e.status = :status, e.completedAt = :completedAt " +
                "WHERE e.id = :id",
        )
    }
}
