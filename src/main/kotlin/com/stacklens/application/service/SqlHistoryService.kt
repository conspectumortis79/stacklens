package com.stacklens.application.service

import com.stacklens.application.collaboration.SqlAnalysisRecordLifecycle
import com.stacklens.application.port.inbound.sql.SqlAnalysisRecordLifecycleUseCase
import com.stacklens.application.port.inbound.sql.SqlHistoryUseCase
import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.SqlAnalysisRecord

@ApplicationService
class SqlHistoryService(
    private val sqlAnalysisRepo: SqlAnalysisPort,
) : SqlHistoryUseCase,
    SqlAnalysisRecordLifecycleUseCase,
    SqlAnalysisRecordLifecycle {
    override fun createRunningRecord(
        sql: String,
        ddl: String?,
    ): SqlAnalysisRecord = sqlAnalysisRepo.saveSqlAnalysis(SqlAnalysisRecord(sql = sql, ddl = ddl, status = AnalysisStatus.RUNNING))

    override fun finalizeRecord(
        id: Long,
        status: String,
        testData: String?,
        analysisResult: String?,
    ): SqlAnalysisRecord? {
        val existing = sqlAnalysisRepo.findSqlAnalysisById(id) ?: return null
        return sqlAnalysisRepo.saveSqlAnalysis(
            SqlAnalysisRecord(
                id = existing.id,
                sql = existing.sql,
                ddl = existing.ddl,
                testData = testData ?: existing.testData,
                analysisResult = analysisResult ?: existing.analysisResult,
                status = status,
                createdAt = existing.createdAt,
            ),
        )
    }

    override fun getAllAnalysisRecords(): List<SqlAnalysisRecord> = sqlAnalysisRepo.findRecentSqlAnalyses(AnalysisConstants.MAX_RECENT_RECORDS)

    override fun getAnalysisRecord(id: Long): SqlAnalysisRecord? = sqlAnalysisRepo.findSqlAnalysisById(id)

    override fun deleteAnalysisRecord(id: Long) = sqlAnalysisRepo.deleteSqlAnalysisById(id)

    override fun deleteAllAnalysisRecords() = sqlAnalysisRepo.deleteAllSqlAnalyses()
}
