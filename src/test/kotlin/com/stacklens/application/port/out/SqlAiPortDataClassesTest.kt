package com.stacklens.application.port.out

import com.stacklens.application.service.SqlAlternativeQuery
import com.stacklens.application.service.SqlCombinedResult
import com.stacklens.application.service.SqlGeneratedQueryResult
import com.stacklens.application.service.SqlOptimization
import com.stacklens.application.service.SqlQueryAnalysisResult
import com.stacklens.application.service.SqlSampleDataResult
import com.stacklens.application.service.SqlTableData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SqlAiPortDataClassesTest {
    @Test
    fun `SqlOptimization stores all four fields`() {
        val o = SqlOptimization(title = "t", description = "d", severity = "HIGH", suggestion = "s")
        assertEquals("t", o.title)
        assertEquals("d", o.description)
        assertEquals("HIGH", o.severity)
        assertEquals("s", o.suggestion)
    }

    @Test
    fun `SqlOptimization equality holds by field value`() {
        val a = SqlOptimization("t", "d", "HIGH", "s")
        val b = SqlOptimization("t", "d", "HIGH", "s")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `SqlQueryAnalysisResult stores all fields`() {
        val r =
            SqlQueryAnalysisResult(
                syntaxStatus = "OK",
                syntaxDetails = "ok",
                syntaxIssues = listOf("i1"),
                optimizations = listOf(SqlOptimization("t", "d", "M", "s")),
                indexSuggestions = listOf("CREATE INDEX"),
                insights = "good",
            )
        assertEquals("OK", r.syntaxStatus)
        assertEquals("ok", r.syntaxDetails)
        assertEquals(listOf("i1"), r.syntaxIssues)
        assertEquals(1, r.optimizations.size)
        assertEquals(listOf("CREATE INDEX"), r.indexSuggestions)
        assertEquals("good", r.insights)
    }

    @Test
    fun `SqlTableData stores all fields including null notes`() {
        val t =
            SqlTableData(
                tableName = "users",
                createStatement = "CREATE TABLE users (id INT)",
                insertStatements = listOf("INSERT INTO users VALUES (1)"),
                notes = null,
            )
        assertEquals("users", t.tableName)
        assertEquals("CREATE TABLE users (id INT)", t.createStatement)
        assertEquals(listOf("INSERT INTO users VALUES (1)"), t.insertStatements)
        assertNull(t.notes)
    }

    @Test
    fun `SqlTableData supports non-null notes`() {
        val t = SqlTableData("t", "create", listOf("i1"), "Some notes")
        assertEquals("Some notes", t.notes)
    }

    @Test
    fun `SqlSampleDataResult stores all fields with default validationNotes`() {
        val r =
            SqlSampleDataResult(
                tables = emptyList(),
                executionOrder = listOf("users", "orders"),
                notes = null,
            )
        assertEquals(emptyList<SqlTableData>(), r.tables)
        assertEquals(listOf("users", "orders"), r.executionOrder)
        assertNull(r.notes)
        assertNull(r.validationNotes)
    }

    @Test
    fun `SqlSampleDataResult supports all fields populated`() {
        val r =
            SqlSampleDataResult(
                tables = listOf(SqlTableData("t", "c", listOf(), "n")),
                executionOrder = listOf("t"),
                notes = "n",
                validationNotes = "vn",
            )
        assertEquals(1, r.tables.size)
        assertEquals("vn", r.validationNotes)
    }

    @Test
    fun `SqlCombinedResult stores all fields`() {
        val r =
            SqlCombinedResult(
                historyId = 7L,
                analysis = null,
                sampleData = null,
                analysisError = "ae",
                sampleDataError = "se",
            )
        assertEquals(7L, r.historyId)
        assertNull(r.analysis)
        assertNull(r.sampleData)
        assertEquals("ae", r.analysisError)
        assertEquals("se", r.sampleDataError)
    }

    @Test
    fun `SqlCombinedResult equality holds by field value`() {
        val a = SqlCombinedResult(null, null, null, null, null)
        val b = SqlCombinedResult(null, null, null, null, null)
        assertEquals(a, b)
    }

    @Test
    fun `SqlAlternativeQuery stores query and description`() {
        val q = SqlAlternativeQuery(query = "SELECT 1", description = "simple")
        assertEquals("SELECT 1", q.query)
        assertEquals("simple", q.description)
    }

    @Test
    fun `SqlGeneratedQueryResult stores all fields`() {
        val r =
            SqlGeneratedQueryResult(
                query = "SELECT * FROM users",
                explanation = "fetches all",
                usedTables = listOf("users"),
                alternativeQueries = listOf(SqlAlternativeQuery("SELECT id", "only id")),
                notes = "n",
            )
        assertEquals("SELECT * FROM users", r.query)
        assertEquals("fetches all", r.explanation)
        assertEquals(listOf("users"), r.usedTables)
        assertEquals(1, r.alternativeQueries.size)
        assertEquals("n", r.notes)
    }
}
