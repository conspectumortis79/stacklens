package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SqlAnalysisRecordTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val r = SqlAnalysisRecord(sql = "SELECT 1")
        assertEquals(0L, r.id)
        assertEquals("SELECT 1", r.sql)
        assertEquals(null, r.ddl)
        assertEquals(null, r.testData)
        assertEquals(null, r.analysisResult)
        assertEquals("RUNNING", r.status)
    }

    @Test
    fun `named constructor populates all fields`() {
        val now = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val r =
            SqlAnalysisRecord(
                id = 1L,
                sql = "SELECT 1",
                ddl = "CREATE TABLE x (id INT)",
                testData = "INSERT INTO x VALUES (1)",
                analysisResult = """{"x":1}""",
                status = "COMPLETED",
                createdAt = now,
            )
        assertEquals(1L, r.id)
        assertEquals("SELECT 1", r.sql)
        assertEquals("CREATE TABLE x (id INT)", r.ddl)
        assertEquals("INSERT INTO x VALUES (1)", r.testData)
        assertEquals("""{"x":1}""", r.analysisResult)
        assertEquals("COMPLETED", r.status)
        assertEquals(now, r.createdAt)
    }
}
