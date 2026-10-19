package com.stacklens.adapter.out.database.scanner

import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.util.closingAuto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet

internal val scannerLog: Logger = LoggerFactory.getLogger("com.stacklens.adapter.out.database.scanner")

internal fun <T> queryRows(
    conn: Connection,
    sql: String,
    vararg params: Any,
    mapper: (ResultSet) -> T,
): List<T> {
    val results = mutableListOf<T>()
    try {
        conn.prepareStatement(sql.trimIndent()).use { stmt ->
            params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.executeQuery().closingAuto { rs ->
                while (rs.next()) {
                    try {
                        results += mapper(rs)
                    } catch (e: Exception) {
                        scannerLog.debug("Row mapping error: {}", e.message)
                    }
                }
            }
        }
    } catch (e: Exception) {
        scannerLog.debug("Query error [{}...]: {}", sql.take(60).trim(), e.message)
        throw e
    }
    return results
}

internal fun buildSchemaFilter(
    cfg: DatabaseConfig,
    baseSql: String,
    schemaCol: String,
): String {
    val schemas = cfg.schemaList()
    if (schemas.isEmpty()) return baseSql
    val placeholders = schemas.joinToString(",") { "?" }
    return "$baseSql AND $schemaCol IN ($placeholders)"
}

internal fun schemaParams(cfg: DatabaseConfig): Array<Any> = cfg.schemaList().toTypedArray()
