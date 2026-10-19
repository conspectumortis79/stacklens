package com.stacklens.adapter.out.database.scanner

import com.stacklens.adapter.out.database.DatabaseObjectScanner
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class SequenceScanner : DatabaseObjectScanner {
    override val name: String = "sequences"

    override fun supports(dialect: DatabaseDialect): Boolean = dialect.supportsSequences()

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
            SELECT sequence_schema, sequence_name, data_type
            FROM information_schema.sequences
            WHERE LOWER(sequence_name) LIKE LOWER(?)
              AND sequence_schema NOT IN ('pg_catalog','information_schema')
            ORDER BY sequence_schema, sequence_name
        """,
                "sequence_schema",
            )

        return queryRows(conn, sql, "%$keyword%", *schemaParams(cfg)) { rs ->
            val schema = rs.getString("sequence_schema")
            val seq = rs.getString("sequence_name")
            val dtype = rs.getString("data_type")

            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.DATABASE,
                resultType = ResultType.SEQUENCE,
                configName = cfg.name,
                dbHost = cfg.host,
                databaseName = cfg.databaseName,
                schemaName = schema,
                tableName = seq,
                ddlContext = "SEQUENCE $schema.$seq ($dtype)",
            )
        }
    }
}
