package com.stacklens.application.collaboration

import com.stacklens.domain.model.SqlAnalysisRecord

interface SqlAnalysisRecordLifecycle {
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
