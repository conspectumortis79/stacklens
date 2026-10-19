package com.stacklens.adapter.out.database.scanner

import com.stacklens.adapter.out.database.DatabaseObjectScanner
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class TableScanner : DatabaseObjectScanner {
    override val name: String = "tables"

    override fun supports(dialect: DatabaseDialect): Boolean = true

    override fun scan(
        conn: Connection,
        cfg: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val sql =
            buildSchemaFilter(
                cfg,
                """
            SELECT table_schema, table_name, table_type
            FROM information_schema.tables
            WHERE LOWER(table_name) LIKE LOWER(?)
              AND table_schema NOT IN ('information_schema','pg_catalog','sys','mysql','performance_schema')
            ORDER BY table_schema, table_name
        """,
                "table_schema",
            )

        return queryRows(conn, sql, "%$keyword%", *schemaParams(cfg)) { rs ->
            val schema = rs.getString("table_schema")
            val table = rs.getString("table_name")
            val type = rs.getString("table_type")

            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.DATABASE,
                resultType = if (type.contains("VIEW", ignoreCase = true)) ResultType.VIEW else ResultType.TABLE,
                configName = cfg.name,
                dbHost = cfg.host,
                databaseName = cfg.databaseName,
                schemaName = schema,
                tableName = table,
                ddlContext = "Table '$schema.$table' (type: $type) contains the keyword in its name",
            )
        }
    }
}
