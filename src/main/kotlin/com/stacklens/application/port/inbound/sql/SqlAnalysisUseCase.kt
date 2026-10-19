package com.stacklens.application.port.inbound.sql

import com.stacklens.domain.model.SqlAnalysisRecord

interface SqlAnalysisUseCase {
    fun analyzeQuery(
        sql: String,
        ddl: String?,
    ): Any

    fun generateSampleData(
        sql: String,
        ddl: String?,
    ): Any

    fun analyzeCombined(
        sql: String,
        ddl: String?,
    ): Any

    fun generateQuery(
        ddl: String,
        seedData: String?,
        goal: String,
    ): Any

    fun runTest(
        sql: String,
        testData: String,
    ): Any
}

interface SqlAnalysisRecordLifecycleUseCase {
    fun createRunningRecord(
        sql: String,
        ddl: String?,
    ): SqlAnalysisRecord

    fun finalizeRecord(
        id: Long,
        status: String,
        testData: String?,
        analysisResult: String?,
    ): SqlAnalysisRecord?
}

interface SqlHistoryUseCase {
    fun getAllAnalysisRecords(): List<SqlAnalysisRecord>

    fun getAnalysisRecord(id: Long): SqlAnalysisRecord?

    fun deleteAnalysisRecord(id: Long)

    fun deleteAllAnalysisRecords()
}
