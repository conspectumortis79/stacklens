package com.stacklens.adapter.out.database.scanner

import com.stacklens.adapter.out.database.DatabaseObjectScanner
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class ColumnScanner : DatabaseObjectScanner {
    override val name: String = "columns"

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
            SELECT table_schema, table_name, column_name, data_type, character_maximum_length, is_nullable, column_default
            FROM information_schema.columns
            WHERE LOWER(column_name) LIKE LOWER(?)
              AND table_schema NOT IN ('information_schema','pg_catalog','sys','mysql','performance_schema')
            ORDER BY table_schema, table_name, ordinal_position
        """,
                "table_schema",
            )

        return queryRows(conn, sql, "%$keyword%", *schemaParams(cfg)) { rs ->
            val schema = rs.getString("table_schema")
            val table = rs.getString("table_name")
            val column = rs.getString("column_name")
            val dtype = rs.getString("data_type")
            val maxLen = rs.getString("character_maximum_length")
            val nullable = rs.getString("is_nullable")
            val default = rs.getString("column_default")

            val ddl =
                buildString {
                    append("$column $dtype")
                    if (maxLen != null) append("($maxLen)")
                    if (nullable == "NO") append(" NOT NULL")
                    if (default != null) append(" DEFAULT $default")
                }

            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.DATABASE,
                resultType = ResultType.COLUMN,
                configName = cfg.name,
                dbHost = cfg.host,
                databaseName = cfg.databaseName,
                schemaName = schema,
                tableName = table,
                columnName = column,
                dataType = dtype,
                ddlContext = "Column in $schema.$table: $ddl",
            )
        }
    }
}
