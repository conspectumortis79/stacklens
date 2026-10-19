package com.stacklens.adapter.out.database.scanner

import com.stacklens.adapter.out.database.DatabaseObjectScanner
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class TriggerScanner : DatabaseObjectScanner {
    override val name: String = "triggers"

    override fun supports(dialect: DatabaseDialect): Boolean = dialect.supportsTriggers()

    override fun scan(
        conn: Connection,
        cfg: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val sql = """
            SELECT trigger_schema, trigger_name, event_manipulation, event_object_table, action_statement
            FROM information_schema.triggers
            WHERE (LOWER(trigger_name) LIKE LOWER(?) OR LOWER(action_statement) LIKE LOWER(?))
              AND trigger_schema NOT IN ('pg_catalog','information_schema')
        """
        return queryRows(conn, sql, "%$keyword%", "%$keyword%") { rs ->
            val schema = rs.getString("trigger_schema")
            val trigger = rs.getString("trigger_name")
            val event = rs.getString("event_manipulation")
            val table = rs.getString("event_object_table")
            val body = rs.getString("action_statement")?.take(500)
            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.DATABASE,
                resultType = ResultType.TRIGGER,
                configName = cfg.name,
                dbHost = cfg.host,
                databaseName = cfg.databaseName,
                schemaName = schema,
                tableName = table,
                columnName = trigger,
                ddlContext = "$event TRIGGER $trigger ON $schema.$table\n${body ?: ""}",
            )
        }
    }
}
