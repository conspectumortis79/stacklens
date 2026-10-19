package com.stacklens.application.service

import com.stacklens.application.collaboration.SqlAnalysisRecordLifecycle
import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.sql.SqlAnalysisUseCase
import com.stacklens.application.port.out.JsonWriterPort
import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.ParallelTaskPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.SqlAi
import com.stacklens.application.service.SqlCombinedResult
import com.stacklens.application.service.SqlGeneratedQueryResult
import com.stacklens.application.service.SqlQueryAnalysisResult
import com.stacklens.application.service.SqlSampleDataResult
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.SqlAnalysisRecord
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@ApplicationService
class SqlAnalysisService(
    private val copilotCliService: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val jsonCodec: JsonWriterPort,
    private val sqlAnalysisRepo: SqlAnalysisPort,
    private val aiService: SqlAi,
    private val startupHealer: StartupRecovery,
    private val sqlTestRunner: SqlTestRunner,
    private val recordLifecycle: SqlAnalysisRecordLifecycle,
    private val parallelTasks: ParallelTaskPort,
    private val clock: ClockPort,
) : SqlAnalysisUseCase,
    StaleWorkHealer {
    data class TestRunResult(
        val columns: List<String>,
        val rows: List<List<String?>>,
        val rowCount: Int,
        val truncated: Boolean = false,
        val error: String? = null,
    )

    override fun heal() {
        resetStaleAnalyses()
    }

    fun resetStaleAnalyses() =
        startupHealer.heal(
            logTag = "SQL-Analyse",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                sqlAnalysisRepo.findAllByOrderByCreatedAtDesc().filter {
                    it.status == AnalysisStatus.RUNNING ||
                        it.status == AnalysisStatus.PENDING
                }
            },
            markFailed = { },
            save = { old ->
                sqlAnalysisRepo.saveSqlAnalysis(
                    SqlAnalysisRecord(
                        id = old.id,
                        sql = old.sql,
                        ddl = old.ddl,
                        testData = old.testData,
                        analysisResult = old.analysisResult,
                        status = AnalysisStatus.CANCELLED,
                        createdAt = old.createdAt,
                    ),
                )
            },
        )

    override fun analyzeQuery(
        sql: String,
        ddl: String?,
    ): SqlQueryAnalysisResult {
        val model = copilotCliService.resolveModel()
        val virtualId = clock.epochMillis()
        val response =
            aiUsageContext.withContext("sql", virtualId) {
                copilotCliService.execute(model, aiService.buildAnalyzePrompt(sql, ddl))
            }
        return aiService.parseQueryAnalysis(response).copy(analysisId = virtualId)
    }

    override fun generateSampleData(
        sql: String,
        ddl: String?,
    ): SqlSampleDataResult {
        val model = copilotCliService.resolveModel()
        val virtualId = clock.epochMillis()
        val response =
            aiUsageContext.withContext("sql", virtualId) {
                copilotCliService.execute(model, aiService.buildSampleDataPrompt(sql, ddl))
            }
        return aiService.parseSampleData(response).copy(analysisId = virtualId)
    }

    override fun analyzeCombined(
        sql: String,
        ddl: String?,
    ): SqlCombinedResult {
        val model = copilotCliService.resolveModel()
        val analyzePrompt = aiService.buildAnalyzePrompt(sql, ddl)
        val dataPrompt = aiService.buildSampleDataPrompt(sql, ddl)

        val record = recordLifecycle.createRunningRecord(sql, ddl)

        aiUsageContext.setContext("sql", record.id)
        aiUsageContext.setModelOverride(model)

        val (analyzeFuture, dataFuture) = launchParallelAiTasks(model, record.id, analyzePrompt, dataPrompt)

        val (analysisResult, analysisError) = awaitQueryAnalysis(analyzeFuture)
        val (sampleDataResult, sampleDataError) = awaitSampleData(dataFuture)

        val finalStatus = if (analysisError != null) "FAILED" else "COMPLETED"
        aiUsageContext.clearContext()
        val testDataStr = buildTestDataString(sampleDataResult)
        val analysisJson = encodeAnalysisJson(analysisResult)
        recordLifecycle.finalizeRecord(record.id, finalStatus, testDataStr, analysisJson)

        return SqlCombinedResult(
            historyId = record.id,
            analysis = analysisResult,
            sampleData = sampleDataResult,
            analysisError = analysisError,
            sampleDataError = sampleDataError,
        )
    }

    private fun launchParallelAiTasks(
        model: String,
        recordId: Long,
        analyzePrompt: String,
        dataPrompt: String,
    ): Pair<CompletableFuture<String>, CompletableFuture<String>> {
        val trackKind = "sql"
        val trackId = recordId
        val analyzeFuture =
            parallelTasks.submit {
                aiUsageContext.setContext(trackKind, trackId)
                try {
                    aiUsageContext.setModelOverride(model)
                    copilotCliService.execute(model, analyzePrompt)
                } finally {
                    aiUsageContext.clearContext()
                }
            }
        val dataFuture =
            parallelTasks.submit {
                aiUsageContext.setContext(trackKind, trackId)
                try {
                    aiUsageContext.setModelOverride(model)
                    copilotCliService.execute(model, dataPrompt)
                } finally {
                    aiUsageContext.clearContext()
                }
            }
        return analyzeFuture to dataFuture
    }

    private fun awaitQueryAnalysis(future: CompletableFuture<String>): Pair<SqlQueryAnalysisResult?, String?> =
        try {
            val parsed = aiService.parseQueryAnalysis(future.get())
            parsed to null
        } catch (e: ExecutionException) {
            null to executionExceptionMessage(e, "Analysis failed")
        } catch (e: Exception) {
            null to (e.message ?: "Analysis failed")
        }

    private fun awaitSampleData(future: CompletableFuture<String>): Pair<SqlSampleDataResult?, String?> =
        try {
            val parsed = aiService.parseSampleData(future.get())
            parsed to null
        } catch (e: ExecutionException) {
            null to executionExceptionMessage(e, "Test data generation failed")
        } catch (e: Exception) {
            null to (e.message ?: "Test data generation failed")
        }

    private fun buildTestDataString(sampleDataResult: SqlSampleDataResult?): String =
        sampleDataResult
            ?.tables
            ?.flatMap { t -> listOfNotNull(t.createStatement.takeIf { it.isNotBlank() }) + t.insertStatements }
            ?.joinToString("\n")
            .orEmpty()

    private fun encodeAnalysisJson(analysisResult: SqlQueryAnalysisResult?): String? =
        analysisResult?.let {
            try {
                jsonCodec.encode(it)
            } catch (_: Exception) {
                null
            }
        }

    override fun generateQuery(
        ddl: String,
        seedData: String?,
        goal: String,
    ): SqlGeneratedQueryResult {
        val model = copilotCliService.resolveModel()
        val virtualId = clock.epochMillis()
        val response =
            aiUsageContext.withContext("sql", virtualId) {
                copilotCliService.execute(model, aiService.buildGenerateQueryPrompt(ddl, seedData, goal))
            }
        return aiService.parseGeneratedQuery(response).copy(analysisId = virtualId)
    }

    override fun runTest(
        sql: String,
        testData: String,
    ): TestRunResult = sqlTestRunner.runTest(sql, testData).let { TestRunResult(it.columns, it.rows, it.rowCount, it.truncated, it.error) }

    internal fun splitSetupSql(script: String): List<String> = sqlTestRunner.splitSetupSql(script)

    internal fun executionExceptionMessage(
        e: java.util.concurrent.ExecutionException,
        fallback: String,
    ): String = e.cause?.message ?: e.message ?: fallback
}
