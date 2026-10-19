package com.stacklens.domain.model

import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SearchResultCoverageTest {
    @Test
    fun `SearchResult defaults leave every optional field null`() {
        val r =
            SearchResult(
                sessionId = 1L,
                keyword = "kw",
                sourceType = SourceType.GITLAB,
            )
        assertEquals(0L, r.id)
        assertEquals(1L, r.sessionId)
        assertEquals("kw", r.keyword)
        assertEquals(SourceType.GITLAB, r.sourceType)
        assertNull(r.resultType)
        assertNull(r.repoName)
        assertNull(r.repoUrl)
        assertNull(r.projectId)
        assertNull(r.filePath)
        assertNull(r.branch)
        assertNull(r.lineNumber)
        assertNull(r.className)
        assertNull(r.functionName)
        assertNull(r.codeSnippet)
        assertNull(r.configName)
        assertNull(r.dbHost)
        assertNull(r.databaseName)
        assertNull(r.schemaName)
        assertNull(r.tableName)
        assertNull(r.columnName)
        assertNull(r.dataType)
        assertNull(r.ddlContext)
        assertNull(r.crossRefParentId)
        assertNotNull(r.createdAt)
    }

    @Test
    fun `SearchResult stores every provided field verbatim`() {
        val now = LocalDateTime.of(2025, 1, 1, 0, 0)
        val r =
            SearchResult(
                id = 42L,
                sessionId = 7L,
                keyword = "kw",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.TABLE,
                repoName = "repo",
                repoUrl = "https://repo",
                projectId = 99L,
                filePath = "src/x.kt",
                branch = "main",
                lineNumber = 12,
                className = "C",
                functionName = "f",
                codeSnippet = "println()",
                configName = "cfg",
                dbHost = "h",
                databaseName = "db",
                schemaName = "s",
                tableName = "t",
                columnName = "c",
                dataType = "INT",
                ddlContext = "CREATE TABLE t(id INT)",
                crossRefParentId = 13L,
                createdAt = now,
            )
        assertEquals(42L, r.id)
        assertEquals(7L, r.sessionId)
        assertEquals("kw", r.keyword)
        assertEquals(SourceType.DATABASE, r.sourceType)
        assertEquals(ResultType.TABLE, r.resultType)
        assertEquals("repo", r.repoName)
        assertEquals("https://repo", r.repoUrl)
        assertEquals(99L, r.projectId)
        assertEquals("src/x.kt", r.filePath)
        assertEquals("main", r.branch)
        assertEquals(12, r.lineNumber)
        assertEquals("C", r.className)
        assertEquals("f", r.functionName)
        assertEquals("println()", r.codeSnippet)
        assertEquals("cfg", r.configName)
        assertEquals("h", r.dbHost)
        assertEquals("db", r.databaseName)
        assertEquals("s", r.schemaName)
        assertEquals("t", r.tableName)
        assertEquals("c", r.columnName)
        assertEquals("INT", r.dataType)
        assertEquals("CREATE TABLE t(id INT)", r.ddlContext)
        assertEquals(13L, r.crossRefParentId)
        assertEquals(now, r.createdAt)
    }
}
