package com.stacklens.application.service

data class SqlOptimization(
    val title: String,
    val description: String,
    val severity: String,
    val suggestion: String,
)

data class SqlQueryAnalysisResult(
    val syntaxStatus: String,
    val syntaxDetails: String,
    val syntaxIssues: List<String>,
    val optimizations: List<SqlOptimization>,
    val indexSuggestions: List<String>,
    val insights: String,
    val analysisId: Long? = null,
)

data class SqlTableData(
    val tableName: String,
    val createStatement: String,
    val insertStatements: List<String>,
    val notes: String?,
)

data class SqlSampleDataResult(
    val tables: List<SqlTableData>,
    val executionOrder: List<String>,
    val notes: String?,
    val validationNotes: String? = null,
    val analysisId: Long? = null,
)

data class SqlCombinedResult(
    val historyId: Long?,
    val analysis: SqlQueryAnalysisResult?,
    val sampleData: SqlSampleDataResult?,
    val analysisError: String?,
    val sampleDataError: String?,
)

data class SqlAlternativeQuery(
    val query: String,
    val description: String,
)

data class SqlGeneratedQueryResult(
    val query: String,
    val explanation: String,
    val usedTables: List<String>,
    val alternativeQueries: List<SqlAlternativeQuery>,
    val notes: String?,
    val analysisId: Long? = null,
)

interface SqlAi {
    fun buildAnalyzePrompt(
        sql: String,
        ddl: String?,
    ): String

    fun buildSampleDataPrompt(
        sql: String,
        ddl: String?,
    ): String

    fun buildGenerateQueryPrompt(
        ddl: String,
        seedData: String?,
        goal: String,
    ): String

    fun parseQueryAnalysis(response: String): SqlQueryAnalysisResult

    fun parseSampleData(response: String): SqlSampleDataResult

    fun parseGeneratedQuery(response: String): SqlGeneratedQueryResult
}
