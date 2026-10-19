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

class LoggingFixAnalysisPartialUpdaterStageColumnCoverageTest {
    private val entityManager: EntityManager = mock()
    private val query: Query = mock()

    private fun updater(): LoggingFixAnalysisPartialUpdater {
        val updater = LoggingFixAnalysisPartialUpdater()
        val field = LoggingFixAnalysisPartialUpdater::class.java.getDeclaredField("entityManager")
        field.isAccessible = true
        field.set(updater, entityManager)
        whenever(entityManager.createQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.executeUpdate()).thenReturn(1)
        return updater
    }

    @Test
    fun `each stage column is translated to its entity property`() {
        val updater = updater()

        updater.appendStageOutput(9L, LoggingFixStageColumn.DIAGNOSE, "chunk")
        verify(query).setParameter("chunk", "chunk")

        updater.appendStageOutput(9L, LoggingFixStageColumn.PLAN, "chunk")
        updater.appendStageOutput(9L, LoggingFixStageColumn.CODE, "chunk")
        assertEquals(
            1,
            updater.appendStageOutput(9L, LoggingFixStageColumn.TESTS, "chunk"),
        )
    }
}
