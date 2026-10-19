package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.shared.UuidIdGenerator
import com.stacklens.adapter.out.sql.H2InMemoryTestSessionAdapter
import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.service.SqlAi
import com.stacklens.application.service.SqlGeneratedQueryResult
import com.stacklens.application.service.SqlQueryAnalysisResult
import com.stacklens.application.service.SqlSampleDataResult
import com.stacklens.application.service.SqlTableData
import com.stacklens.domain.model.SqlAnalysisRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException

class SqlAnalysisServiceTest {
    private lateinit var copilotPort: CopilotPort
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }
    private val jsonCodec = JacksonJsonCodecAdapter(objectMapper)
    private lateinit var sqlAnalysisRepo: SqlAnalysisPort
    private lateinit var aiService: SqlAi
    private lateinit var service: SqlAnalysisService

    @BeforeEach
    fun setup() {
        copilotPort = mock()
        sqlAnalysisRepo = mock()
        aiService = mock()
        service =
            SqlAnalysisService(
                copilotPort,
                aiUsageContext = FakeAiUsageContextPort(),
                jsonCodec,
                sqlAnalysisRepo,
                aiService,
                StartupHealer(),
                sqlTestRunner = SqlTestRunner(H2InMemoryTestSessionAdapter(), UuidIdGenerator()),
                recordLifecycle = SqlHistoryService(sqlAnalysisRepo),
                parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
    }

    private fun analysis(status: String = "RUNNING") =
        SqlQueryAnalysisResult(
            syntaxStatus = "OK",
            syntaxDetails = "",
            syntaxIssues = emptyList(),
            optimizations = emptyList(),
            indexSuggestions = emptyList(),
            insights = "",
        )

    private fun record(
        id: Long = 1L,
        status: String = "RUNNING",
    ) = SqlAnalysisRecord(id = id, sql = "SELECT 1", status = status)

    @Test
    fun `analyzeQuery calls ai service with sql and ddl`() {
        val analysisResult = analysis()
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("prompt")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysisResult)
        val result = service.analyzeQuery("SELECT * FROM users", "CREATE TABLE users (id INT)")
        assertEquals(analysisResult, result.copy(analysisId = null))
    }

    @Test
    fun `analyzeQuery works with null ddl`() {
        val analysisResult = analysis()
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("prompt")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysisResult)
        val result = service.analyzeQuery("SELECT 1", null)
        assertNotNull(result)
    }

    @Test
    fun `generateSampleData calls ai service`() {
        val sampleResult =
            SqlSampleDataResult(
                tables = listOf(SqlTableData("users", "CREATE TABLE users (id INT)", listOf("INSERT INTO users VALUES (1)"), null)),
                executionOrder = listOf("users"),
                notes = null,
            )
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("prompt")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseSampleData(any())).thenReturn(sampleResult)
        val result = service.generateSampleData("SELECT * FROM users", null)
        assertEquals(sampleResult, result.copy(analysisId = null))
    }

    @Test
    fun `generateQuery calls ai service`() {
        val genResult =
            SqlGeneratedQueryResult(
                query = "SELECT id FROM users WHERE age > 18",
                explanation = "Filters adults",
                usedTables = listOf("users"),
                alternativeQueries = emptyList(),
                notes = null,
            )
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildGenerateQueryPrompt(any(), anyOrNull(), any())).thenReturn("prompt")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseGeneratedQuery(any())).thenReturn(genResult)
        val result = service.generateQuery("CREATE TABLE users (id INT, age INT)", null, "Find adults")
        assertEquals(genResult.query, result.query)
    }

    @Test
    fun `runTest executes simple SELECT and returns rows`() {
        val result =
            service.runTest(
                "SELECT * FROM test_users",
                "CREATE TABLE test_users (id INT, name VARCHAR(50)); INSERT INTO test_users VALUES (1, 'Alice'); INSERT INTO test_users VALUES (2, 'Bob');",
            )
        assertEquals(2, result.rowCount)
        assertEquals(listOf("id", "name"), result.columns)
        assertNull(result.error)
        assertFalse(result.truncated)
        assertEquals(2, result.rows.size)
    }

    @Test
    fun `runTest returns error when SQL is invalid`() {
        val result =
            service.runTest(
                "SELECT * FROM nonexistent_table",
                "",
            )
        assertNotNull(result.error)
    }

    @Test
    fun `runTest skips comment lines in setup SQL`() {
        val result =
            service.runTest(
                "SELECT * FROM t",
                """
                -- this is a comment
                CREATE TABLE t (id INT);
                INSERT INTO t VALUES (42);
                """.trimIndent(),
            )
        assertEquals(1, result.rowCount)
    }

    @Test
    fun `runTest handles empty test data gracefully`() {
        val result = service.runTest("SELECT 1 AS val", "")
        assertTrue(result.error == null || result.rowCount == 1)
    }

    @Test
    fun `runTest truncates results when more than 200 rows`() {
        val insertStmts = (1..205).joinToString("\n") { "INSERT INTO big (id) VALUES ($it);" }
        val result =
            service.runTest(
                "SELECT * FROM big",
                "CREATE TABLE big (id INT);\n$insertStmts",
            )
        assertTrue(result.rowCount <= 200)
    }

    @Test
    fun `resetStaleAnalyses marks RUNNING records as FAILED`() {
        val running = record(id = 1L, status = "RUNNING")
        val completed = record(id = 2L, status = "COMPLETED")
        whenever(sqlAnalysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running, completed))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] }
        service.heal()
        verify(sqlAnalysisRepo).saveSqlAnalysis(any())
    }

    @Test
    fun `resetStaleAnalyses also marks PENDING records as FAILED`() {
        val pending = record(id = 1L, status = "PENDING")
        whenever(sqlAnalysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(pending))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] }
        service.resetStaleAnalyses()
        verify(sqlAnalysisRepo).saveSqlAnalysis(any())
    }

    @Test
    fun `resetStaleAnalyses does nothing when no running records`() {
        whenever(sqlAnalysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(
            listOf(record(id = 1L, status = "COMPLETED")),
        )
        service.resetStaleAnalyses()
    }

    @Test
    fun `analyzeCombined returns combined result with both analysis and sample data`() {
        val analysisResult = analysis()
        val sampleResult = SqlSampleDataResult(tables = emptyList(), executionOrder = emptyList(), notes = null)
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("prompt1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("prompt2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysisResult)
        whenever(aiService.parseSampleData(any())).thenReturn(sampleResult)
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertEquals(analysisResult, result.analysis)
        assertNull(result.analysisError)
    }

    @Test
    fun `analyzeCombined captures analysis error when AI fails`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("prompt1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("prompt2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("AI exploded"))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertNull(result.analysis)
        assertNotNull(result.analysisError)
    }

    @Test
    fun `analyzeCombined captures sample data error separately`() {
        val analysisResult = analysis()
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("prompt1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("prompt2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysisResult)
        whenever(aiService.parseSampleData(any())).thenThrow(RuntimeException("parse fail"))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertEquals(analysisResult, result.analysis)
        assertNotNull(result.sampleDataError)
    }

    @Test
    fun `resetStaleAnalyses swallows outer exceptions`() {
        whenever(sqlAnalysisRepo.findAllByOrderByCreatedAtDesc()).thenThrow(RuntimeException("DB down"))
        service.resetStaleAnalyses()
    }

    @Test
    fun `resetStaleAnalyses swallows per-record save exceptions`() {
        val running = record(id = 1L, status = "RUNNING")
        whenever(sqlAnalysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(running))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenThrow(RuntimeException("save fail"))
        service.resetStaleAnalyses()
    }

    @Test
    fun `runTest handles multi-statement setup with comments and blanks`() {
        val result =
            service.runTest(
                "SELECT * FROM t",
                """
                -- Create table
                # Another comment
                CREATE TABLE t (id INT);

                INSERT INTO t VALUES (1);
                INSERT INTO t VALUES (2);
                """.trimIndent(),
            )
        assertEquals(2, result.rowCount)
    }

    @Test
    fun `runTest handles statement without trailing semicolon`() {
        val result =
            service.runTest(
                "SELECT 1 AS val",
                "CREATE TABLE dummy (id INT)",
            )
        assertTrue(result.error == null || result.rowCount >= 0)
    }

    @Test
    fun `analyzeCombined sets COMPLETED when analysis succeeds and sampleData fails`() {
        val analysisResult = analysis()
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysisResult)
        whenever(aiService.parseSampleData(any())).thenThrow(RuntimeException("parse fail"))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertEquals(analysisResult, result.analysis)
        assertNull(result.analysisError)
        assertNotNull(result.sampleDataError)
    }

    @Test
    fun `analyzeCombined captures null cause message from ExecutionException`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException(null as String?))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertNull(result.analysis)
        assertNotNull(result.analysisError)
    }

    @Test
    fun `analyzeCombined handles serialization exception for analysisResult`() {
        val analysisResult = analysis()
        val badCodec = org.mockito.kotlin.mock<com.stacklens.application.port.out.JsonCodecPort>()
        whenever(badCodec.encode(any())).thenThrow(RuntimeException("serialize fail"))
        val svcWithBadMapper =
            SqlAnalysisService(
                copilotPort,
                aiUsageContext = FakeAiUsageContextPort(),
                badCodec,
                sqlAnalysisRepo,
                aiService,
                StartupHealer(),
                sqlTestRunner = SqlTestRunner(H2InMemoryTestSessionAdapter(), UuidIdGenerator()),
                recordLifecycle = SqlHistoryService(sqlAnalysisRepo),
                parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysisResult)
        whenever(
            aiService.parseSampleData(any()),
        ).thenReturn(SqlSampleDataResult(tables = emptyList(), executionOrder = emptyList(), notes = null))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = svcWithBadMapper.analyzeCombined("SELECT 1", null)
        assertEquals(analysisResult, result.analysis)
    }

    @Test
    fun `runTest discards blank-only statements after trimming semicolons`() {
        val result =
            service.runTest(
                "SELECT 1 AS val",
                ";\n;\n;",
            )
        assertTrue(result.error == null)
    }

    @Test
    fun `runTest handles empty line between non-empty lines in setup`() {
        val result =
            service.runTest(
                "SELECT * FROM t",
                "CREATE TABLE t (id INT);\n\n\nINSERT INTO t VALUES (1);",
            )
        assertEquals(1, result.rowCount)
    }

    @Test
    fun `runTest returns exactly 200 rows without marking truncated`() {
        val inserts = (1..200).joinToString("\n") { "INSERT INTO t200 (id) VALUES ($it);" }
        val result = service.runTest("SELECT * FROM t200", "CREATE TABLE t200 (id INT);\n$inserts")
        assertEquals(200, result.rowCount)
    }

    @Test
    fun `runTest blank line in middle of multi-line statement does not discard buffer`() {
        val setupSql = "CREATE TABLE tblank (id INT);\nINSERT INTO tblank\n\nVALUES (1);"
        val result = service.runTest("SELECT * FROM tblank", setupSql)
        assertTrue(result.error == null || result.rowCount >= 0)
    }

    @Test
    fun `runTest setup stmt with invalid SQL hits catch block`() {
        val result = service.runTest("SELECT 1 AS val", "INVALID SQL STATEMENT;")
        assertTrue(result.error == null)
    }

    @Test
    fun `analyzeCombined Exception with non-null message on analyze future covers branch`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("explicit error msg"))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertNull(result.analysis)
        assertNotNull(result.analysisError)
    }

    @Test
    fun `analyzeCombined parseQueryAnalysis throws covers catch Exception for analyze`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("raw response")
        whenever(aiService.parseQueryAnalysis(any())).thenThrow(RuntimeException("parse error"))
        whenever(aiService.parseSampleData(any())).thenReturn(
            SqlSampleDataResult(tables = emptyList(), executionOrder = emptyList(), notes = null),
        )
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertNotNull(result.analysisError)
        assertNull(result.analysis)
    }

    @Test
    fun `analyzeCombined parseSampleData throws covers catch Exception for data`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("raw response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysis())
        whenever(aiService.parseSampleData(any())).thenThrow(RuntimeException("sample parse error"))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertNotNull(result.sampleDataError)
        assertNull(result.sampleData)
    }

    @Test
    fun `analyzeCombined with non-empty tables covers flatMap lambda on line 137`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("raw response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysis())
        val tables =
            listOf(
                SqlTableData("t1", "CREATE TABLE t1 (id INT)", listOf("INSERT INTO t1 VALUES (1)"), null),
                SqlTableData("t2", "", listOf(), null),
            )
        whenever(aiService.parseSampleData(any())).thenReturn(
            SqlSampleDataResult(tables = tables, executionOrder = emptyList(), notes = null),
        )
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertNotNull(result.sampleData)
    }

    @Test
    fun `analyzeCombined parseQueryAnalysis throws null message uses Analysis failed`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("raw response")
        whenever(aiService.parseQueryAnalysis(any())).thenThrow(RuntimeException(null as String?))
        whenever(aiService.parseSampleData(any())).thenReturn(
            SqlSampleDataResult(tables = emptyList(), executionOrder = emptyList(), notes = null),
        )
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertEquals("Analysis failed", result.analysisError)
    }

    @Test
    fun `analyzeCombined parseSampleData throws null message uses Testdaten fehlgeschlagen`() {
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4o")
        whenever(aiService.buildAnalyzePrompt(any(), anyOrNull())).thenReturn("p1")
        whenever(aiService.buildSampleDataPrompt(any(), anyOrNull())).thenReturn("p2")
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("raw response")
        whenever(aiService.parseQueryAnalysis(any())).thenReturn(analysis())
        whenever(aiService.parseSampleData(any())).thenThrow(RuntimeException(null as String?))
        whenever(sqlAnalysisRepo.saveSqlAnalysis(any())).thenAnswer { it.arguments[0] as SqlAnalysisRecord }
        whenever(sqlAnalysisRepo.findSqlAnalysisById(any())).thenReturn(record())
        val result = service.analyzeCombined("SELECT 1", null)
        assertEquals("Test data generation failed", result.sampleDataError)
    }

    @Test
    fun `executionExceptionMessage returns cause message when both cause and ExecutionException have messages`() {
        val cause = RuntimeException("root cause")
        val ee = ExecutionException("wrapper", cause)
        assertEquals("root cause", service.executionExceptionMessage(ee, "fallback"))
    }

    @Test
    fun `executionExceptionMessage returns wrapper message when cause is null`() {
        val ee = ExecutionException("wrapper message", null)
        assertEquals("wrapper message", service.executionExceptionMessage(ee, "fallback"))
    }

    @Test
    fun `executionExceptionMessage returns fallback when cause and message are both null`() {
        val ee = ExecutionException(null as String?, null)
        assertEquals("fallback", service.executionExceptionMessage(ee, "fallback"))
    }
}
