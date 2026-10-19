package com.stacklens.application.service.analysis

import com.stacklens.application.port.inbound.analysis.StaleAnalysisKind
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.temporal.ChronoUnit

class StaleAnalysisStatusTest {
    private val clock = FixedClockPort(java.time.Instant.parse("2026-01-07T12:00:00Z"))
    private val now = clock.localDateTime()

    private fun status(defaultTimeout: Duration = Duration.ofMinutes(5)) =
        StaleAnalysisStatus(
            clock = clock,
            defaultRunningTimeout = defaultTimeout,
            runningTimeouts = mapOf(StaleAnalysisKind.TICKET_CREATION to Duration.ofMinutes(10)),
        )

    @Test
    fun `a status built without overrides uses the default timeout for every kind`() {
        val plain = StaleAnalysisStatus(clock = clock, defaultRunningTimeout = Duration.ofMinutes(7))
        val sixMinutes = now.minus(6, ChronoUnit.MINUTES)
        val eightMinutes = now.minus(8, ChronoUnit.MINUTES)

        for (kind in StaleAnalysisKind.entries) {
            assertEquals("RUNNING", plain.effectiveStatus(kind, "RUNNING", sixMinutes), kind.name)
            assertEquals("FAILED", plain.effectiveStatus(kind, "RUNNING", eightMinutes), kind.name)
        }
    }

    @Test
    fun `a running record younger than the kind timeout keeps its status`() {
        val underFiveMinutes = now.minus(4, ChronoUnit.MINUTES)
        val underTenMinutes = now.minus(9, ChronoUnit.MINUTES)

        assertEquals("RUNNING", status().effectiveStatus(StaleAnalysisKind.IMAGE_EXTRACTION, "RUNNING", underFiveMinutes))
        assertEquals("RUNNING", status().effectiveStatus(StaleAnalysisKind.TICKET_CREATION, "RUNNING", underTenMinutes))
    }

    @Test
    fun `a running record older than the kind timeout is reported as failed`() {
        val sixMinutes = now.minus(6, ChronoUnit.MINUTES)
        val elevenMinutes = now.minus(11, ChronoUnit.MINUTES)

        assertEquals("FAILED", status().effectiveStatus(StaleAnalysisKind.SQL_ANALYSIS, "RUNNING", sixMinutes))
        assertEquals("FAILED", status().effectiveStatus(StaleAnalysisKind.TICKET_CREATION, "RUNNING", elevenMinutes))
    }

    @Test
    fun `the ticket creation kind uses its own longer timeout instead of the default`() {
        val sevenMinutes = now.minus(7, ChronoUnit.MINUTES)

        assertEquals("RUNNING", status().effectiveStatus(StaleAnalysisKind.TICKET_CREATION, "RUNNING", sevenMinutes))
        assertEquals("FAILED", status().effectiveStatus(StaleAnalysisKind.IMAGE_EXTRACTION, "RUNNING", sevenMinutes))
    }

    @Test
    fun `statuses other than running are never rewritten`() {
        val longAgo = now.minus(3, ChronoUnit.HOURS)

        for (status in listOf("PENDING", "COMPLETED", "FAILED", "CANCELLED")) {
            assertEquals(status, this.status().effectiveStatus(StaleAnalysisKind.SQL_ANALYSIS, status, longAgo))
        }
    }
}
