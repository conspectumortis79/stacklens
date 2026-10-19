package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.database.DatabaseScanResult
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareDatabaseScanPort")
open class McpAwareDatabaseScanAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("databaseScanPort")
    private val delegate: DatabaseScanPort,
) : DatabaseScanPort {
    private val log = LoggerFactory.getLogger(McpAwareDatabaseScanAdapter::class.java)

    override fun scanDatabase(
        config: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): DatabaseScanResult {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { it.routeDatabase }
                ?: return delegate.scanDatabase(config, keyword, sessionId)
        return try {
            val res =
                gateway.callTool(
                    mcp,
                    "db_scan_keyword",
                    mapOf("name" to config.name, "keyword" to keyword),
                )
            if (res.isError) {
                log.warn("[MCP] db_scan_keyword error, falling back: {}", res.text)
                return delegate.scanDatabase(config, keyword, sessionId)
            }
            val node =
                McpRouting.parseJsonOrNull(objectMapper, res.text)
                    ?: return delegate.scanDatabase(config, keyword, sessionId)
            val dbName = node.path("databaseName").asText(config.databaseName)
            DatabaseScanResult(
                results =
                    node.path("hits").mapNotNull { hit ->
                        mapHitToSearchResult(hit, config, keyword, sessionId, dbName)
                    },
            )
        } catch (e: Exception) {
            log.warn("[MCP] db_scan_keyword exception, falling back: {}", e.message)
            delegate.scanDatabase(config, keyword, sessionId)
        }
    }

    override fun extractCrossRefTerms(result: SearchResult): List<String> = delegate.extractCrossRefTerms(result)

    override fun fetchSchemaSummary(config: DatabaseConfig): String {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { it.routeDatabase }
                ?: return delegate.fetchSchemaSummary(config)
        return try {
            val res = gateway.callTool(mcp, "db_schema_summary", mapOf("name" to config.name))
            if (res.isError) {
                log.warn("[MCP] db_schema_summary error, falling back: {}", res.text)
                return delegate.fetchSchemaSummary(config)
            }
            val node =
                McpRouting.parseJsonOrNull(objectMapper, res.text)
                    ?: return delegate.fetchSchemaSummary(config)
            buildSchemaSummary(node)
        } catch (e: Exception) {
            log.warn("[MCP] db_schema_summary exception, falling back: {}", e.message)
            delegate.fetchSchemaSummary(config)
        }
    }

    private fun mapHitToSearchResult(
        hit: JsonNode,
        config: DatabaseConfig,
        keyword: String,
        sessionId: Long,
        dbName: String,
    ): SearchResult? {
        val kind: String = hit.path("kind").asText("")
        val resultType =
            when (kind) {
                "table" -> ResultType.TABLE
                "column" -> ResultType.COLUMN
                "view" -> ResultType.VIEW
                else -> return null
            }
        return SearchResult(
            sessionId = sessionId,
            keyword = keyword,
            sourceType = SourceType.DATABASE,
            resultType = resultType,
            configName = config.name,
            dbHost = config.host,
            databaseName = dbName,
            schemaName = hit.path("schemaName").asText("").takeIf(String::isNotBlank),
            tableName = hit.path("tableName").asText("").takeIf(String::isNotBlank),
            columnName = hit.path("columnName").asText("").takeIf(String::isNotBlank),
            dataType = hit.path("dataType").asText("").takeIf(String::isNotBlank),
            ddlContext = hit.path("ddlContext").asText("").takeIf(String::isNotBlank),
        )
    }

    private fun buildSchemaSummary(node: JsonNode): String {
        val cols = node.path("columns")
        if (!cols.isArray || cols.isEmpty) return ""
        val byTable = LinkedHashMap<String, MutableList<JsonNode>>()
        cols.forEach { c ->
            val t = c.path("tableName").asText("")
            if (t.isNotBlank()) byTable.getOrPut(t) { mutableListOf() }.add(c)
        }
        return buildString {
            byTable.forEach { (table, columns) ->
                append("TABLE ").append(table).append('\n')
                columns.forEach { col ->
                    append("  ")
                        .append(col.path("columnName").asText(""))
                        .append(' ')
                        .append(col.path("dataType").asText(""))
                        .append(if (col.path("nullable").asText("YES") == "NO") " NOT NULL" else "")
                        .append('\n')
                }
                append('\n')
            }
        }.trimEnd()
    }
}
