package com.stacklens.domain.model

import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchResultTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val r =
            SearchResult(
                sessionId = 1L,
                keyword = "foo",
                sourceType = SourceType.GITLAB,
            )
        assertEquals(0L, r.id)
        assertEquals(1L, r.sessionId)
        assertEquals("foo", r.keyword)
        assertEquals(SourceType.GITLAB, r.sourceType)
        assertEquals(null, r.resultType)
        assertEquals(null, r.repoName)
        assertEquals(null, r.repoUrl)
        assertEquals(null, r.projectId)
        assertEquals(null, r.filePath)
        assertEquals(null, r.branch)
        assertEquals(null, r.lineNumber)
        assertEquals(null, r.className)
        assertEquals(null, r.functionName)
        assertEquals(null, r.codeSnippet)
        assertEquals(null, r.configName)
        assertEquals(null, r.dbHost)
        assertEquals(null, r.databaseName)
        assertEquals(null, r.schemaName)
        assertEquals(null, r.tableName)
        assertEquals(null, r.columnName)
        assertEquals(null, r.dataType)
        assertEquals(null, r.ddlContext)
        assertEquals(null, r.crossRefParentId)
    }

    @Test
    fun `named constructor populates all fields`() {
        val r =
            SearchResult(
                id = 1L,
                sessionId = 2L,
                keyword = "k",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.COLUMN,
                repoName = "r",
                repoUrl = "u",
                projectId = 3L,
                filePath = "f",
                branch = "b",
                lineNumber = 4,
                className = "c",
                functionName = "fn",
                codeSnippet = "code",
                configName = "cn",
                dbHost = "h",
                databaseName = "db",
                schemaName = "s",
                tableName = "t",
                columnName = "col",
                dataType = "INT",
                ddlContext = "ddl",
                crossRefParentId = 5L,
            )
        assertEquals(1L, r.id)
        assertEquals(2L, r.sessionId)
        assertEquals(SourceType.DATABASE, r.sourceType)
        assertEquals(ResultType.COLUMN, r.resultType)
        assertEquals("r", r.repoName)
        assertEquals("u", r.repoUrl)
        assertEquals(3L, r.projectId)
        assertEquals("f", r.filePath)
        assertEquals("b", r.branch)
        assertEquals(4, r.lineNumber)
        assertEquals("c", r.className)
        assertEquals("fn", r.functionName)
        assertEquals("code", r.codeSnippet)
        assertEquals("cn", r.configName)
        assertEquals("h", r.dbHost)
        assertEquals("db", r.databaseName)
        assertEquals("s", r.schemaName)
        assertEquals("t", r.tableName)
        assertEquals("col", r.columnName)
        assertEquals("INT", r.dataType)
        assertEquals("ddl", r.ddlContext)
        assertEquals(5L, r.crossRefParentId)
    }
}
