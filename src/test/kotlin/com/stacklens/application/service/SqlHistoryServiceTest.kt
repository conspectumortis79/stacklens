package com.stacklens.application.service

import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.domain.model.SqlAnalysisRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SqlHistoryServiceTest {
    private val sqlAnalysisRepo: SqlAnalysisPort = mock()

    private val service = SqlHistoryService(sqlAnalysisRepo)

    private fun record(
        id: Long = 1L,
        status: String = "RUNNING",
    ) = SqlAnalysisRecord(id = id, sql = "SELECT 1", status = status)

    @Test
    fun `createRunningRecord saves a RUNNING record`() {
        val saved = record(id = 1L, status = "RUNNING")
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenReturn(saved)
        val result = service.createRunningRecord("SELECT 1", null)
        assertEquals(1L, result.id)
    }

    @Test
    fun `finalizeRecord updates status and testData`() {
        val existing = record(id = 1L, status = "RUNNING")
        val updated = SqlAnalysisRecord(id = 1L, sql = "SELECT 1", status = "COMPLETED", testData = "INSERT INTO t VALUES (1)")
        whenever(sqlAnalysisRepo.findSqlAnalysisById(1L)).thenReturn(existing)
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenReturn(updated)
        val result = service.finalizeRecord(1L, "COMPLETED", "INSERT INTO t VALUES (1)", null)
        assertNotNull(result)
        assertEquals("COMPLETED", result!!.status)
    }

    @Test
    fun `finalizeRecord returns null when record not found`() {
        whenever(sqlAnalysisRepo.findSqlAnalysisById(99L)).thenReturn(null)
        val result = service.finalizeRecord(99L, "COMPLETED", null, null)
        assertNull(result)
    }

    @Test
    fun `finalizeRecord keeps existing testData when new value is null`() {
        val existing =
            SqlAnalysisRecord(
                id = 1L,
                sql = "SELECT 1",
                status = "RUNNING",
                testData = "existing-data",
            )
        val updated =
            SqlAnalysisRecord(
                id = 1L,
                sql = "SELECT 1",
                status = "COMPLETED",
                testData = "existing-data",
            )
        whenever(sqlAnalysisRepo.findSqlAnalysisById(1L)).thenReturn(existing)
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenReturn(updated)
        val result = service.finalizeRecord(1L, "COMPLETED", null, null)
        assertNotNull(result)
        assertEquals("existing-data", result!!.testData)
    }

    @Test
    fun `getAllAnalysisRecords delegates to repo`() {
        whenever(sqlAnalysisRepo.findRecentSqlAnalyses(200)).thenReturn(listOf(record()))
        val result = service.getAllAnalysisRecords()
        assertEquals(1, result.size)
    }

    @Test
    fun `getAnalysisRecord delegates to repo`() {
        val rec = record(id = 5L)
        whenever(sqlAnalysisRepo.findSqlAnalysisById(5L)).thenReturn(rec)
        assertEquals(rec, service.getAnalysisRecord(5L))
    }

    @Test
    fun `getAnalysisRecord returns null when not found`() {
        whenever(sqlAnalysisRepo.findSqlAnalysisById(99L)).thenReturn(null)
        assertNull(service.getAnalysisRecord(99L))
    }

    @Test
    fun `deleteAnalysisRecord delegates to repo`() {
        service.deleteAnalysisRecord(5L)
        verify(sqlAnalysisRepo).deleteSqlAnalysisById(5L)
    }

    @Test
    fun `deleteAllAnalysisRecords delegates to repo`() {
        service.deleteAllAnalysisRecords()
        verify(sqlAnalysisRepo).deleteAllSqlAnalyses()
    }
}
