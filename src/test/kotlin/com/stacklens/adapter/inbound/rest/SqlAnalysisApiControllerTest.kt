package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.sql.SqlAnalysisRecordLifecycleUseCase
import com.stacklens.application.port.inbound.sql.SqlAnalysisUseCase
import com.stacklens.application.port.inbound.sql.SqlHistoryUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.SqlAnalysisRecord
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class SqlAnalysisApiControllerTest {
    @Mock private lateinit var sqlAnalysisService: SqlAnalysisUseCase

    @Mock private lateinit var sqlHistoryService: SqlHistoryUseCase

    @Mock private lateinit var sqlRecordLifecycle: SqlAnalysisRecordLifecycleUseCase

    private val i18n: I18nUseCase =
        object : I18nUseCase, I18nPort {
            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ) = key

            override fun t(
                key: String,
                vararg args: Any,
            ) = key

            override fun resolveLanguage() = "en"

            override fun translateError(e: Exception) = e.message ?: ""
        }

    private val controller by lazy {
        SqlAnalysisApiController(sqlAnalysisService, sqlHistoryService, sqlRecordLifecycle, i18n, StaleAnalysisStatusFixtures.withRealClock())
    }

    private fun mockRecord(
        id: Long = 1L,
        sql: String = "SELECT * FROM users",
        status: String = "COMPLETED",
        testData: String? = null,
        analysisResult: String? = null,
    ) = SqlAnalysisRecord(
        id = id,
        sql = sql,
        ddl = null,
        testData = testData,
        analysisResult = analysisResult,
        status = status,
        createdAt = LocalDateTime.now(),
    )

    @Test
    fun `analyze returns 400 when sql is blank`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "")
        val result = controller.analyze(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `analyze returns result on success`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.analyzeQuery(eq("SELECT 1"), anyOrNull()))
            .thenReturn(mapOf("syntax" to "ok"))

        val result = controller.analyze(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `analyze returns 500 on exception`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.analyzeQuery(any(), anyOrNull())).thenThrow(RuntimeException("AI error"))
        val result = controller.analyze(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `generateData returns 400 when sql is blank`() {
        val req = SqlAnalysisApiController.GenerateDataRequest(sql = "  ")
        val result = controller.generateData(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `generateData returns result on success`() {
        val req = SqlAnalysisApiController.GenerateDataRequest(sql = "SELECT 1", ddl = "CREATE TABLE t (id INT)")
        whenever(sqlAnalysisService.generateSampleData(eq("SELECT 1"), any()))
            .thenReturn(mapOf("insertStatements" to "INSERT INTO ..."))

        val result = controller.generateData(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `generateData returns 500 on exception`() {
        val req = SqlAnalysisApiController.GenerateDataRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.generateSampleData(any(), anyOrNull())).thenThrow(RuntimeException("error"))
        val result = controller.generateData(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `analyzeCombined returns 400 when sql is blank`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "")
        val result = controller.analyzeCombined(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `analyzeCombined returns result on success`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.analyzeCombined(eq("SELECT 1"), anyOrNull()))
            .thenReturn(mapOf("analysis" to "ok", "data" to "INSERT ..."))

        val result = controller.analyzeCombined(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `analyzeCombined returns 500 on exception`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.analyzeCombined(any(), anyOrNull())).thenThrow(RuntimeException("error"))
        val result = controller.analyzeCombined(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `generateQuery returns 400 when ddl is blank`() {
        val req = SqlAnalysisApiController.GenerateQueryRequest(ddl = "", goal = "find users")
        val result = controller.generateQuery(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `generateQuery returns 400 when goal is blank`() {
        val req = SqlAnalysisApiController.GenerateQueryRequest(ddl = "CREATE TABLE t (id INT)", goal = "")
        val result = controller.generateQuery(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `generateQuery returns result on success`() {
        val req = SqlAnalysisApiController.GenerateQueryRequest(ddl = "CREATE TABLE t (id INT)", goal = "find records")
        whenever(sqlAnalysisService.generateQuery(any(), anyOrNull(), any())).thenReturn(mapOf("query" to "SELECT * FROM t"))
        val result = controller.generateQuery(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `generateQuery returns 500 on exception`() {
        val req = SqlAnalysisApiController.GenerateQueryRequest(ddl = "CREATE TABLE t (id INT)", goal = "find records")
        whenever(sqlAnalysisService.generateQuery(any(), anyOrNull(), any())).thenThrow(RuntimeException("error"))
        val result = controller.generateQuery(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `runTest returns 400 when sql is blank`() {
        val req = SqlAnalysisApiController.RunTestRequest(sql = "", testData = "INSERT INTO t VALUES(1)")
        val result = controller.runTest(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `runTest returns result on success`() {
        val req = SqlAnalysisApiController.RunTestRequest(sql = "SELECT * FROM t", testData = "INSERT INTO t VALUES(1)")
        whenever(sqlAnalysisService.runTest(any(), any())).thenReturn(mapOf("rows" to listOf<Any>()))
        val result = controller.runTest(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `runTest returns 500 on exception`() {
        val req = SqlAnalysisApiController.RunTestRequest(sql = "SELECT * FROM t", testData = "INSERT INTO t VALUES(1)")
        whenever(sqlAnalysisService.runTest(any(), any())).thenThrow(RuntimeException("DB error"))
        val result = controller.runTest(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `saveHistory returns 400 when sql is blank`() {
        val req = SqlAnalysisApiController.SaveHistoryRequest(sql = "")
        val result = controller.saveHistory(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `saveHistory creates running record and returns id`() {
        val req = SqlAnalysisApiController.SaveHistoryRequest(sql = "SELECT 1")
        whenever(sqlRecordLifecycle.createRunningRecord(eq("SELECT 1"), anyOrNull())).thenReturn(mockRecord())
        val result = controller.saveHistory(req)
        assertEquals(200, result.statusCode.value())
        val body = result.body as Map<*, *>
        assertNotNull(body["id"])
    }

    @Test
    fun `listHistory returns list of records`() {
        val records =
            listOf(
                mockRecord(id = 1L, status = "COMPLETED"),
                mockRecord(id = 2L, status = "FAILED"),
            )
        whenever(sqlHistoryService.getAllAnalysisRecords()).thenReturn(records)
        val result = controller.listHistory()
        assertEquals(2, result.size)
        assertTrue(result.all { it.containsKey("id") && it.containsKey("status") })
    }

    @Test
    fun `listHistory marks stale RUNNING records as FAILED`() {
        val staleRecord =
            SqlAnalysisRecord(
                id = 1L,
                sql = "SELECT 1",
                status = "RUNNING",
                createdAt = LocalDateTime.now().minusMinutes(10),
            )
        whenever(sqlHistoryService.getAllAnalysisRecords()).thenReturn(listOf(staleRecord))
        val result = controller.listHistory()
        assertEquals(1, result.size)
        assertEquals("FAILED", result[0]["status"])
    }

    @Test
    fun `listHistory keeps non-stale RUNNING records as RUNNING`() {
        val freshRecord =
            SqlAnalysisRecord(
                id = 1L,
                sql = "SELECT 1",
                status = "RUNNING",
                createdAt = LocalDateTime.now().minusMinutes(2),
            )
        whenever(sqlHistoryService.getAllAnalysisRecords()).thenReturn(listOf(freshRecord))
        val result = controller.listHistory()
        assertEquals(1, result.size)
        assertEquals("RUNNING", result[0]["status"])
    }

    @Test
    fun `getHistory returns 404 when not found`() {
        whenever(sqlHistoryService.getAnalysisRecord(eq(1L))).thenReturn(null)
        val result = controller.getHistory(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `getHistory returns 200 when found`() {
        whenever(sqlHistoryService.getAnalysisRecord(eq(1L))).thenReturn(mockRecord())
        val result = controller.getHistory(1L)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `deleteHistory returns 404 when not found`() {
        whenever(sqlHistoryService.getAnalysisRecord(eq(1L))).thenReturn(null)
        val result = controller.deleteHistory(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `deleteHistory returns 204 when found`() {
        whenever(sqlHistoryService.getAnalysisRecord(eq(1L))).thenReturn(mockRecord())
        val result = controller.deleteHistory(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `deleteAllHistory clears all records`() {
        val result = controller.deleteAllHistory()
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertEquals(true, body["deleted"])
    }

    @Test
    fun `analyze returns 500 with fallback message when exception has null message`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.analyzeQuery(any(), anyOrNull())).thenThrow(RuntimeException())
        val result = controller.analyze(req)
        assertEquals(500, result.statusCode.value())
        val body = result.body as Map<*, *>
        assertEquals("common.be_unknown_error", body["error"])
    }

    @Test
    fun `generateData returns 500 with fallback message when exception has null message`() {
        val req = SqlAnalysisApiController.GenerateDataRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.generateSampleData(any(), anyOrNull())).thenThrow(RuntimeException())
        val result = controller.generateData(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `analyzeCombined returns 500 with fallback message when exception has null message`() {
        val req = SqlAnalysisApiController.AnalyzeRequest(sql = "SELECT 1")
        whenever(sqlAnalysisService.analyzeCombined(any(), anyOrNull())).thenThrow(RuntimeException())
        val result = controller.analyzeCombined(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `generateQuery returns 500 with fallback message when exception has null message`() {
        val req = SqlAnalysisApiController.GenerateQueryRequest(ddl = "CREATE TABLE t (id INT)", goal = "fetch all")
        whenever(sqlAnalysisService.generateQuery(any(), anyOrNull(), any())).thenThrow(RuntimeException())
        val result = controller.generateQuery(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `runTest returns 500 with fallback message when exception has null message`() {
        val req = SqlAnalysisApiController.RunTestRequest(sql = "SELECT 1", testData = "INSERT INTO t VALUES (1)")
        whenever(sqlAnalysisService.runTest(any(), any())).thenThrow(RuntimeException())
        val result = controller.runTest(req)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `listHistory marks hasTestData true when testData is not null`() {
        val recordWithData = mockRecord(id = 1L, status = "COMPLETED", testData = "INSERT INTO t VALUES (1)")
        whenever(sqlHistoryService.getAllAnalysisRecords()).thenReturn(listOf(recordWithData))
        val result = controller.listHistory()
        assertEquals(true, result[0]["hasTestData"])
    }
}
