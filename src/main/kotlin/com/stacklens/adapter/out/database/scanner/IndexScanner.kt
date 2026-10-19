package com.stacklens.adapter.out.database.scanner

import com.stacklens.adapter.out.database.DatabaseObjectScanner
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class IndexScanner : DatabaseObjectScanner {
    override val name: String = "indexes"

    override fun supports(dialect: DatabaseDialect): Boolean = true

    override fun scan(
        conn: Connection,
        cfg: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val dialect = DatabaseDialects.of(cfg.dbType)
        val sql = dialect.indexesSql().trimIndent()
        val params = dialect.indexParams(keyword)

        return queryRows(conn, sql, *params) { rs ->
            val schema = rs.getString("schemaname")
            val index = rs.getString("indexname")
            val table = rs.getString("tablename")

            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.DATABASE,
                resultType = ResultType.INDEX,
                configName = cfg.name,
                dbHost = cfg.host,
                databaseName = cfg.databaseName,
                schemaName = schema,
                tableName = table,
                columnName = index,
                ddlContext = "INDEX $schema.$index on $schema.$table",
            )
        }
    }
}
