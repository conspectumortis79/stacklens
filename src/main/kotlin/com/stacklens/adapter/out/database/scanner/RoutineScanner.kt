package com.stacklens.adapter.out.database.scanner

import com.stacklens.adapter.out.database.DatabaseObjectScanner
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class RoutineScanner : DatabaseObjectScanner {
    override val name: String = "routines"

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
            SELECT routine_schema, routine_name, routine_type, routine_definition
            FROM information_schema.routines
            WHERE (LOWER(routine_name) LIKE LOWER(?) OR LOWER(routine_definition) LIKE LOWER(?))
              AND routine_schema NOT IN ('information_schema','pg_catalog','sys','mysql','performance_schema')
            ORDER BY routine_schema, routine_name
        """,
                "routine_schema",
            )

        return queryRows(conn, sql, "%$keyword%", "%$keyword%", *schemaParams(cfg)) { rs ->
            val schema = rs.getString("routine_schema")
            val name = rs.getString("routine_name")
            val type = rs.getString("routine_type")
            val def = rs.getString("routine_definition")?.take(500)

            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.DATABASE,
                resultType = ResultType.PROCEDURE,
                configName = cfg.name,
                dbHost = cfg.host,
                databaseName = cfg.databaseName,
                schemaName = schema,
                tableName = name,
                ddlContext = "$type $schema.$name\n${def ?: ""}",
            )
        }
    }
}
