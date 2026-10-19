package com.stacklens.application.service
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.JsonReaderPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.SqlAi
import com.stacklens.application.service.SqlAlternativeQuery
import com.stacklens.application.service.SqlGeneratedQueryResult
import com.stacklens.application.service.SqlOptimization
import com.stacklens.application.service.SqlQueryAnalysisResult
import com.stacklens.application.service.SqlSampleDataResult
import com.stacklens.application.service.SqlTableData
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class SqlAiService(
    private val copilotCliService: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val jsonCodec: JsonReaderPort,
    private val aiJsonExtractor: AiResponseJsonExtractorPort,
) : SqlAi {
    private val log = LoggerFactory.getLogger(SqlAiService::class.java)

    override fun buildAnalyzePrompt(
        sql: String,
        ddl: String?,
    ): String {
        val lang = copilotCliService.resolveLanguage()
        val ddlSection = if (!ddl.isNullOrBlank()) promptLoader.get(lang, "sql.ddlSection", mapOf("ddl" to ddl)) else ""
        return promptLoader.get(lang, "sql.analyzeQuery", mapOf("sql" to sql, "ddlSection" to ddlSection))
    }

    override fun buildSampleDataPrompt(
        sql: String,
        ddl: String?,
    ): String {
        val lang = copilotCliService.resolveLanguage()
        val ddlSection = if (!ddl.isNullOrBlank()) promptLoader.get(lang, "sql.ddlSection", mapOf("ddl" to ddl)) else ""
        return promptLoader.get(lang, "sql.sampleData", mapOf("sql" to sql, "ddlSection" to ddlSection))
    }

    override fun buildGenerateQueryPrompt(
        ddl: String,
        seedData: String?,
        goal: String,
    ): String {
        val lang = copilotCliService.resolveLanguage()
        val seedSection = if (!seedData.isNullOrBlank()) promptLoader.get(lang, "sql.seedSection", mapOf("seedData" to seedData)) else ""
        return promptLoader.get(
            lang,
            "sql.generateQuery",
            mapOf("ddl" to ddl, "seedSection" to seedSection, "goal" to goal),
        )
    }

    override fun parseQueryAnalysis(response: String): SqlQueryAnalysisResult =
        try {
            val node = jsonCodec.decodeMap(extractJsonBlock(response))
            val optimizations =
                asList(node["optimizations"]).map { opt ->
                    val map = asMap(opt)
                    SqlOptimization(
                        title = asText(map["title"]),
                        description = asText(map["description"]),
                        severity = asText(map["severity"]).ifBlank { "MITTEL" },
                        suggestion = asText(map["suggestion"]),
                    )
                }
            SqlQueryAnalysisResult(
                syntaxStatus = asText(node["syntaxStatus"]).ifBlank { "UNBEKANNT" },
                syntaxDetails = asText(node["syntaxDetails"]),
                syntaxIssues = asList(node["syntaxIssues"]).map { nodeText(it) }.filter { it.isNotBlank() },
                optimizations = optimizations,
                indexSuggestions = asList(node["indexSuggestions"]).map { nodeText(it) }.filter { it.isNotBlank() },
                insights = asText(node["insights"]),
            )
        } catch (e: Exception) {
            log.warn("Could not parse query analysis response: {}", e.message)
            SqlQueryAnalysisResult(
                syntaxStatus = "UNBEKANNT",
                syntaxDetails = "Analysis could not be parsed.",
                syntaxIssues = emptyList(),
                optimizations = emptyList(),
                indexSuggestions = emptyList(),
                insights = response.take(1000),
            )
        }

    override fun parseSampleData(response: String): SqlSampleDataResult =
        try {
            val node = jsonCodec.decodeMap(extractJsonBlock(response))
            val tables =
                asList(node["tables"]).map { t ->
                    val map = asMap(t)
                    SqlTableData(
                        tableName = asText(map["tableName"]),
                        createStatement = asText(map["createStatement"]),
                        insertStatements = asList(map["insertStatements"]).map { nodeText(it) }.filter { it.isNotBlank() },
                        notes = asText(map["notes"]).ifBlank { null },
                    )
                }
            SqlSampleDataResult(
                tables = tables,
                executionOrder = asList(node["executionOrder"]).map { nodeText(it) }.filter { it.isNotBlank() },
                notes = asText(node["notes"]).ifBlank { null },
                validationNotes = asText(node["validationNotes"]).ifBlank { null },
            )
        } catch (e: Exception) {
            log.warn("Could not parse sample data response: {}", e.message)
            SqlSampleDataResult(tables = emptyList(), executionOrder = emptyList(), notes = response.take(1000))
        }

    override fun parseGeneratedQuery(response: String): SqlGeneratedQueryResult =
        try {
            val node = jsonCodec.decodeMap(extractJsonBlock(response))
            val alternatives =
                asList(node["alternativeQueries"]).map { a ->
                    val map = asMap(a)
                    SqlAlternativeQuery(
                        query = asText(map["query"]),
                        description = asText(map["description"]),
                    )
                }
            SqlGeneratedQueryResult(
                query = asText(node["query"]),
                explanation = asText(node["explanation"]),
                usedTables = asList(node["usedTables"]).map { nodeText(it) }.filter { it.isNotBlank() },
                alternativeQueries = alternatives,
                notes = asText(node["notes"]).ifBlank { null },
            )
        } catch (e: Exception) {
            log.warn("Could not parse generate-query response: {}", e.message)
            SqlGeneratedQueryResult(
                query = "",
                explanation = "Response could not be parsed.",
                usedTables = emptyList(),
                alternativeQueries = emptyList(),
                notes = response.take(1000),
            )
        }

    internal fun asText(value: Any?): String =
        when (value) {
            null -> ""
            is String -> value
            else -> value.toString()
        }

    internal fun asList(value: Any?): List<Any?> =
        when (value) {
            is List<*> -> value
            else -> emptyList()
        }

    @Suppress("UNCHECKED_CAST")
    internal fun asMap(value: Any?): Map<String, Any?> =
        when (value) {
            is Map<*, *> -> value as Map<String, Any?>
            else -> emptyMap()
        }

    fun nodeText(node: Any?): String {
        if (node is String) return node
        if (node is Map<*, *>) {
            for (key in listOf("text", "message", "issue", "description", "statement", "suggestion", "query", "value")) {
                val v = node[key]
                if (v is String && v.isNotBlank()) return v
            }
            return node.toString()
        }
        return node?.toString() ?: ""
    }

    fun extractJsonBlock(text: String): String = aiJsonExtractor.extractJsonBlock(text)
}
