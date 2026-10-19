package com.stacklens.application.port.out

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JiraPortModelsTest {
    @Test
    fun `JiraTicketData defaults acceptanceCriteria to empty list`() {
        val t =
            JiraTicketData(
                key = "K",
                summary = "S",
                description = "D",
                comments = "C",
                issueType = "Story",
                status = "Open",
                priority = "High",
                labels = listOf("l"),
                components = listOf("c"),
                url = "u",
            )
        assertEquals(emptyList<String>(), t.acceptanceCriteria)
    }

    @Test
    fun `JiraTicketData populates acceptanceCriteria when provided`() {
        val t =
            JiraTicketData(
                key = "K",
                summary = "S",
                description = "D",
                comments = "C",
                issueType = "Story",
                status = "Open",
                priority = "High",
                labels = listOf("l"),
                components = listOf("c"),
                url = "u",
                acceptanceCriteria = listOf("a", "b"),
            )
        assertEquals(listOf("a", "b"), t.acceptanceCriteria)
    }

    @Test
    fun `JiraProblemAnalysis populates fields`() {
        val a =
            JiraProblemAnalysis(
                problemDescription = "p",
                rootCause = "r",
                affectedComponents = listOf("c"),
                searchKeywords = listOf("k"),
                errorMessages = listOf("e"),
            )
        assertEquals("p", a.problemDescription)
        assertEquals("r", a.rootCause)
        assertEquals(listOf("c"), a.affectedComponents)
        assertEquals(listOf("k"), a.searchKeywords)
        assertEquals(listOf("e"), a.errorMessages)
    }

    @Test
    fun `JiraSimilarTicket populates fields`() {
        val t =
            JiraSimilarTicket(
                key = "K",
                summary = "S",
                description = "D",
                issueType = "Bug",
                status = "Closed",
                url = "u",
                foundViaKeyword = "kw",
            )
        assertEquals("K", t.key)
        assertEquals("kw", t.foundViaKeyword)
    }

    @Test
    fun `JiraSimilarTicket with null foundViaKeyword`() {
        val t =
            JiraSimilarTicket(
                key = "K",
                summary = "S",
                description = "D",
                issueType = "Bug",
                status = "Closed",
                url = "u",
                foundViaKeyword = null,
            )
        assertNull(t.foundViaKeyword)
    }

    @Test
    fun `SprintInfo populates fields`() {
        val s = SprintInfo(id = 1L, name = "Sprint 1", state = "active", startDate = "2024-01-01", endDate = "2024-01-15", goal = "ship")
        assertEquals(1L, s.id)
        assertEquals("active", s.state)
    }

    @Test
    fun `SprintInfo with null date fields`() {
        val s = SprintInfo(id = 1L, name = "Sprint 1", state = "future", startDate = null, endDate = null, goal = null)
        assertNull(s.startDate)
        assertNull(s.endDate)
        assertNull(s.goal)
    }

    @Test
    fun `LinkedIssue populates fields`() {
        val l = LinkedIssue(key = "K", summary = "S", relationship = "blocks", status = "Open")
        assertEquals("blocks", l.relationship)
        assertEquals("Open", l.status)
    }

    @Test
    fun `LinkedIssue with null status`() {
        val l = LinkedIssue(key = "K", summary = "S", relationship = "blocks", status = null)
        assertNull(l.status)
    }

    @Test
    fun `SprintIssue defaults linkedIssues to empty list and qaLoopCount to 0`() {
        val s =
            SprintIssue(
                key = "K",
                summary = "S",
                description = "D",
                issueType = "Story",
                status = "Open",
                priority = "High",
                assignee = "alice",
                labels = listOf("l"),
                comments = "C",
                url = "u",
            )
        assertEquals(0, s.qaLoopCount)
        assertEquals(emptyList<LinkedIssue>(), s.linkedIssues)
    }

    @Test
    fun `SprintIssue populates linkedIssues and qaLoopCount when provided`() {
        val s =
            SprintIssue(
                key = "K",
                summary = "S",
                description = "D",
                issueType = "Story",
                status = "Open",
                priority = "High",
                assignee = "alice",
                labels = listOf("l"),
                comments = "C",
                url = "u",
                createdAt = "2024",
                startedAt = "2024-01-15",
                resolvedAt = "2024-01-20",
                qaLoopCount = 3,
                linkedIssues = listOf(LinkedIssue(key = "K-2", summary = "x", relationship = "blocks", status = "Open")),
            )
        assertEquals(3, s.qaLoopCount)
        assertEquals(1, s.linkedIssues.size)
    }

    @Test
    fun `JiraTicketConstants expose the expected limits`() {
        assertEquals(10, JiraTicketConstants.MAX_COMMENTS)
        assertEquals(6, JiraTicketConstants.MAX_SEARCH_KEYWORDS)
        assertEquals(20, JiraTicketConstants.MAX_SIMILAR_TICKETS)
        assertEquals(10, JiraTicketConstants.MAX_SIMILAR_TICKETS_FOR_MR)
        assertEquals(3, JiraTicketConstants.MAX_MRS_PER_TICKET)
        assertEquals(15, JiraTicketConstants.MAX_TOTAL_MRS)
        assertEquals(5, JiraTicketConstants.MAX_FILES_PER_MR)
        assertEquals(3, JiraTicketConstants.MAX_REPO_CONTEXTS)
        assertEquals(40, JiraTicketConstants.MAX_REPO_FILES_PER_PROJECT)
        assertEquals(500_000, JiraTicketConstants.MAX_FILE_CONTENT_CHARS)
        assertEquals(120, JiraTicketConstants.MAX_FILES_FOR_CONTEXT)
    }
}
