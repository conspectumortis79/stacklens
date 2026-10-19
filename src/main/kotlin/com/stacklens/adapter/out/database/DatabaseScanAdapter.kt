package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.database.scanner.DatabaseDialects
import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.database.DatabaseScanResult
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.util.closingAuto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
open class DatabaseScanAdapter(
    private val connectionPort: DatabaseScanConnection,
    private val scanners: List<DatabaseObjectScanner> = emptyList(),
) : DatabaseScanPort {
    private val log = LoggerFactory.getLogger(DatabaseScanAdapter::class.java)

    override fun scanDatabase(
        config: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): DatabaseScanResult {
        val results = mutableListOf<SearchResult>()

        try {
            connectionPort.open(config).closingAuto { conn ->
                log.info("[DB:{}] Scanning for keyword '{}'", config.name, keyword)
                val dialect = DatabaseDialects.of(config.dbType)
                for (scanner in scanners) {
                    if (!scanner.supports(dialect)) continue
                    runCatching { results += scanner.scan(conn, config, keyword, sessionId) }
                        .onFailure { log.warn("[DB:{}] {} failed: {}", config.name, scanner.name, it.message) }
                }
            }
        } catch (e: Exception) {
            log.error(
                "[DB:{}] Connection or scan error: {} — continuing search with remaining databases",
                config.name,
                e.message,
                e,
            )
            return DatabaseScanResult(
                results = emptyList(),
                connectionError =
                    DatabaseConnectionError(
                        configId = config.id,
                        configName = config.name,
                        host = config.host,
                        databaseName = config.databaseName,
                        dbType = config.dbType.name,
                        errorMessage = (e.message ?: e.javaClass.simpleName).take(500),
                    ),
            )
        }

        return DatabaseScanResult(results = results)
    }

    override fun extractCrossRefTerms(result: SearchResult): List<String> {
        val keyword = result.keyword.trim()
        val terms = mutableListOf<String>()
        result.tableName?.let { terms += it }
        result.columnName?.takeIf { result.resultType == ResultType.COLUMN || result.resultType == ResultType.INDEX }?.let { terms += it }
        return terms
            .distinct()
            .filter { it.length >= 3 }
            .filter { keyword.isEmpty() || it.contains(keyword, ignoreCase = true) }
    }

    override fun fetchSchemaSummary(config: DatabaseConfig): String {
        return try {
            connectionPort.open(config).use { conn ->
                val sql =
                    """
                    SELECT table_name, column_name, data_type, character_maximum_length, is_nullable
                    FROM information_schema.columns
                    WHERE table_schema NOT IN ('information_schema','pg_catalog','sys','mysql','performance_schema')
                    ORDER BY table_name, ordinal_position
                    """.trimIndent()
                val rows = mutableListOf<Triple<String, String, String>>()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val table = rs.getString("table_name") ?: continue
                            val column = rs.getString("column_name") ?: continue
                            val dtype = rs.getString("data_type") ?: "unknown"
                            val maxLen = rs.getString("character_maximum_length")
                            val nullable = rs.getString("is_nullable") == "YES"
                            val colDef =
                                buildString {
                                    append(dtype)
                                    if (maxLen != null) append("($maxLen)")
                                    if (!nullable) append(" NOT NULL")
                                }
                            rows += Triple(table, column, colDef)
                        }
                    }
                }
                if (rows.isEmpty()) return ""
                buildString {
                    appendLine("## Database: ${config.name}")
                    rows.groupBy { it.first }.forEach { (table, cols) ->
                        appendLine("TABLE $table")
                        cols.forEach { (_, col, def) -> appendLine("  $col $def") }
                    }
                }.trimEnd()
            }
        } catch (e: Exception) {
            log.warn("[DB:{}] fetchSchemaSummary failed: {}", config.name, e.message)
            ""
        }
    }
}
