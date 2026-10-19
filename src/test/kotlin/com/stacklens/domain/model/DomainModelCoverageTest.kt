package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DomainModelCoverageTest {
    @Test
    fun `JwtAnalysisRecord stores every provided field verbatim`() {
        val now = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
        val rec =
            JwtAnalysisRecord(
                id = 42L,
                token = "eyJ.tok.en",
                jwtId = "jti-1",
                audience = "aud-a,aud-b",
                issuer = "iss",
                subject = "sub",
                status = "VALID",
                createdAt = now,
            )
        assertEquals(42L, rec.id)
        assertEquals("eyJ.tok.en", rec.token)
        assertEquals("jti-1", rec.jwtId)
        assertEquals("aud-a,aud-b", rec.audience)
        assertEquals("iss", rec.issuer)
        assertEquals("sub", rec.subject)
        assertEquals("VALID", rec.status)
        assertEquals(now, rec.createdAt)
    }

    @Test
    fun `JwtAnalysisRecord defaults id to 0 and leaves optional claims null`() {
        val rec = JwtAnalysisRecord(token = "x", status = "EXPIRED")
        assertEquals(0L, rec.id)
        assertNull(rec.jwtId)
        assertNull(rec.audience)
        assertNull(rec.issuer)
        assertNull(rec.subject)
        assertNotNull(rec.createdAt)
    }

    @Test
    fun `SqlAnalysisRecord stores every provided field verbatim`() {
        val now = LocalDateTime.of(2024, 6, 7, 8, 9)
        val rec =
            SqlAnalysisRecord(
                id = 7L,
                sql = "SELECT 1",
                ddl = "CREATE TABLE t(id INT)",
                testData = "INSERT INTO t VALUES (1)",
                analysisResult = "{\"ok\":true}",
                status = "COMPLETED",
                createdAt = now,
            )
        assertEquals(7L, rec.id)
        assertEquals("SELECT 1", rec.sql)
        assertEquals("CREATE TABLE t(id INT)", rec.ddl)
        assertEquals("INSERT INTO t VALUES (1)", rec.testData)
        assertEquals("{\"ok\":true}", rec.analysisResult)
        assertEquals("COMPLETED", rec.status)
        assertEquals(now, rec.createdAt)
    }

    @Test
    fun `SqlAnalysisRecord defaults are applied`() {
        val rec = SqlAnalysisRecord(sql = "SELECT 2")
        assertEquals(0L, rec.id)
        assertNull(rec.ddl)
        assertNull(rec.testData)
        assertNull(rec.analysisResult)
        assertEquals("RUNNING", rec.status)
        assertNotNull(rec.createdAt)
    }

    @Test
    fun `ReviewAnalysisConfig defaults are applied`() {
        val cfg = ReviewAnalysisConfig()
        assertEquals(1L, cfg.id)
        assertEquals("", cfg.jiraSprintUrl)
        assertNull(cfg.jiraConfigId)
        assertEquals("", cfg.analysisInstructions)
        assertNotNull(cfg.updatedAt)
    }

    @Test
    fun `ReviewAnalysisConfig stores every provided field verbatim`() {
        val now = LocalDateTime.of(2025, 5, 6, 7, 8)
        val cfg =
            ReviewAnalysisConfig(
                jiraSprintUrl = "https://example/sprint",
                jiraConfigId = 99L,
                analysisInstructions = "deep analysis",
                updatedAt = now,
            )
        assertEquals("https://example/sprint", cfg.jiraSprintUrl)
        assertEquals(99L, cfg.jiraConfigId)
        assertEquals("deep analysis", cfg.analysisInstructions)
        assertEquals(now, cfg.updatedAt)
    }
}
