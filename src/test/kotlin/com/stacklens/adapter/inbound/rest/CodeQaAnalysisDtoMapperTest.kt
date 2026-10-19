package com.stacklens.adapter.inbound.rest

import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CodeQaAnalysisDtoMapperTest {
    private val mapper = CodeQaAnalysisDtoMapper()

    private fun analysis(
        id: Long = 1L,
        completedAt: LocalDateTime? = null,
        integrationTestEnabled: Boolean = false,
        integrationTestStatus: String? = null,
    ) = CodeQaAnalysis(
        id = id,
        mrUrl = "https://gitlab.example.com/group/repo/-/merge_requests/1",
        jiraTicketKey = "PROJ-1",
        status = "COMPLETED",
        currentStep = "done",
        logOutput = "log...",
        resultMarkdown = "# report",
        errorMessage = null,
        createdAt = LocalDateTime.of(2026, 1, 1, 10, 0),
        completedAt = completedAt,
        integrationTestEnabled = integrationTestEnabled,
        integrationTestStatus = integrationTestStatus,
    )

    @Test
    fun `toStatusMap exposes the fields the polling UI needs`() {
        val a =
            analysis(
                id = 7L,
                completedAt = LocalDateTime.of(2026, 2, 3, 4, 5),
                integrationTestEnabled = true,
                integrationTestStatus = "RUNNING",
            )
        val row = mapper.toStatusMap(a)
        assertEquals(7L, row["id"])
        assertEquals("COMPLETED", row["status"])
        assertEquals("done", row["currentStep"])
        assertEquals("log...", row["logOutput"])
        assertEquals(null, row["errorMessage"])
        assertEquals("2026-02-03T04:05", row["completedAt"])
        assertEquals(true, row["integrationTestEnabled"])
        assertEquals("RUNNING", row["integrationTestStatus"])
    }

    @Test
    fun `toStatusMap omits completedAt when null`() {
        val row = mapper.toStatusMap(analysis(completedAt = null))
        assertNull(row["completedAt"])
    }

    @Test
    fun `toFullMap exposes every persisted field`() {
        val ts = LocalDateTime.of(2026, 6, 7, 8, 9)
        val a =
            analysis(
                id = 3L,
                completedAt = ts,
                integrationTestEnabled = true,
                integrationTestStatus = "COMPLETED",
            )
        val row = mapper.toFullMap(a)
        assertEquals(3L, row["id"])
        assertEquals("https://gitlab.example.com/group/repo/-/merge_requests/1", row["mrUrl"])
        assertEquals("PROJ-1", row["jiraTicketKey"])
        assertEquals("COMPLETED", row["status"])
        assertEquals("done", row["currentStep"])
        assertEquals("log...", row["logOutput"])
        assertEquals("# report", row["resultMarkdown"])
        assertNull(row["errorMessage"])
        assertEquals("2026-01-01T10:00", row["createdAt"])
        assertEquals("2026-06-07T08:09", row["completedAt"])
        assertEquals(true, row["integrationTestEnabled"])
        assertEquals("COMPLETED", row["integrationTestStatus"])
        assertNull(row["integrationTestResult"])
    }

    @Test
    fun `toListItem exposes the compact row shape`() {
        val ts = LocalDateTime.of(2026, 6, 7, 8, 9)
        val a = analysis(id = 11L, completedAt = ts)
        val row = mapper.toListItem(a)
        assertEquals(11L, row["id"])
        assertEquals("https://gitlab.example.com/group/repo/-/merge_requests/1", row["mrUrl"])
        assertEquals("PROJ-1", row["jiraTicketKey"])
        assertEquals("COMPLETED", row["status"])
        assertEquals("2026-01-01T10:00", row["createdAt"])
        assertEquals("2026-06-07T08:09", row["completedAt"])
    }

    @Test
    fun `toListItem omits completedAt when null`() {
        val row = mapper.toListItem(analysis(completedAt = null))
        assertNull(row["completedAt"])
    }
}
