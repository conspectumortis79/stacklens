package com.stacklens.adapter.out.database.scanner

import com.stacklens.adapter.out.database.DatabaseObjectScanner
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class ViewScanner : DatabaseObjectScanner {
    override val name: String = "views"

    override fun supports(dialect: DatabaseDialect): Boolean = true

    override fun scan(
        conn: Connection,
        cfg: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val sql = DatabaseDialects.of(cfg.dbType).viewsSql()
        return queryRows(conn, sql, "%$keyword%", "%$keyword%") { rs ->
            val schema = rs.getString("table_schema")
            val view = rs.getString("table_name")
            val def = rs.getString("view_definition")?.take(500)

            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.DATABASE,
                resultType = ResultType.VIEW,
                configName = cfg.name,
                dbHost = cfg.host,
                databaseName = cfg.databaseName,
                schemaName = schema,
                tableName = view,
                ddlContext = def ?: "View $schema.$view",
            )
        }
    }
}
