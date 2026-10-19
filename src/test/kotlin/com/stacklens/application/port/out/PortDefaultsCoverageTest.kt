package com.stacklens.application.port.out

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PortDefaultsCoverageTest {
    private object StubOcr : LocalOcrPort {
        override fun isAvailable(): Boolean = true

        override fun extractText(
            imageBytes: ByteArray,
            mimeType: String,
            language: String,
        ): String = "$language|$mimeType|${imageBytes.size}"
    }

    @Test
    fun `local ocr ports fall back to the shared default reason and language`() {
        assertNull(StubOcr.failureReason)
        assertEquals("deu+eng|image/png|3", StubOcr.extractText(byteArrayOf(1, 2, 3), "image/png"))
        assertEquals("eng|image/jpeg|1", StubOcr.extractText(byteArrayOf(9), "image/jpeg", "eng"))
        assertEquals(true, StubOcr.isAvailable())
    }

    @Test
    fun `sprint review carriers use empty defaults for optional jira metadata`() {
        val ticket =
            SprintReviewTicket(
                key = "STK-1",
                summary = "Summary",
                description = "Description",
                url = "https://jira/STK-1",
            )

        assertEquals("", ticket.issueType)
        assertEquals("", ticket.status)
        assertNull(ticket.assignee)
        assertNull(ticket.priority)
        assertEquals(emptyList<String>(), ticket.labels)
        assertNull(ticket.storyPoints)
        assertEquals(emptyList<SprintReviewTicket>(), ticket.subtasks)
        assertEquals(emptyList<SprintReviewTicket>(), ticket.referencedTickets)
    }

    @Test
    fun `resolved sprints default their board information`() {
        val sprint = ResolvedSprint(id = 5L, name = "Sprint 12", state = "active", url = "https://jira/board/5")

        assertNull(sprint.boardId)
        assertNull(sprint.boardName)
        assertEquals(5L, sprint.copy(boardId = 9L).id)
    }
}
