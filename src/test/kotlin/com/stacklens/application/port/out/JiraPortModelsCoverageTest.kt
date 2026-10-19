package com.stacklens.application.port.out

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraPortModelsCoverageTest {
    @Test
    fun `JiraTicketData default acceptanceCriteria is empty list`() {
        val d =
            JiraTicketData(
                key = "K",
                summary = "S",
                description = "D",
                comments = "C",
                issueType = "T",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "u",
            )
        assertTrue(d.acceptanceCriteria.isEmpty())
    }

    @Test
    fun `JiraTicketData stores provided values verbatim`() {
        val d =
            JiraTicketData(
                key = "PROJ-1",
                summary = "Sum",
                description = "Desc",
                comments = "Com",
                issueType = "Bug",
                status = "Done",
                priority = "High",
                labels = listOf("a"),
                components = listOf("c"),
                url = "u",
                acceptanceCriteria = listOf("ac1"),
            )
        assertEquals("PROJ-1", d.key)
        assertEquals("Sum", d.summary)
        assertEquals("Desc", d.description)
        assertEquals("Com", d.comments)
        assertEquals("Bug", d.issueType)
        assertEquals("Done", d.status)
        assertEquals("High", d.priority)
        assertEquals(listOf("a"), d.labels)
        assertEquals(listOf("c"), d.components)
        assertEquals("u", d.url)
        assertEquals(listOf("ac1"), d.acceptanceCriteria)
    }

    @Test
    fun `JiraProblemAnalysis stores provided values verbatim`() {
        val a =
            JiraProblemAnalysis(
                problemDescription = "p",
                rootCause = "rc",
                affectedComponents = listOf("x", "y"),
                searchKeywords = listOf("k"),
                errorMessages = listOf("e"),
            )
        assertEquals("p", a.problemDescription)
        assertEquals("rc", a.rootCause)
        assertEquals(listOf("x", "y"), a.affectedComponents)
        assertEquals(listOf("k"), a.searchKeywords)
        assertEquals(listOf("e"), a.errorMessages)
    }

    @Test
    fun `JiraSimilarTicket default foundViaKeyword is null`() {
        val t =
            JiraSimilarTicket(
                key = "k",
                summary = "s",
                description = "d",
                issueType = "T",
                status = "S",
                url = "u",
                foundViaKeyword = null,
            )
        assertNull(t.foundViaKeyword)
    }

    @Test
    fun `JiraSimilarTicket stores provided values verbatim`() {
        val t =
            JiraSimilarTicket(
                key = "k",
                summary = "s",
                description = "d",
                issueType = "T",
                status = "S",
                url = "u",
                foundViaKeyword = "kw",
            )
        assertEquals("kw", t.foundViaKeyword)
    }

    @Test
    fun `SprintInfo default nullable date and goal fields are null`() {
        val s =
            SprintInfo(
                id = 1L,
                name = "n",
                state = "active",
                startDate = null,
                endDate = null,
                goal = null,
            )
        assertNull(s.startDate)
        assertNull(s.endDate)
        assertNull(s.goal)
    }

    @Test
    fun `SprintInfo stores provided values verbatim`() {
        val s =
            SprintInfo(
                id = 7L,
                name = "n",
                state = "closed",
                startDate = "2025-01-01",
                endDate = "2025-01-14",
                goal = "ship",
            )
        assertEquals(7L, s.id)
        assertEquals("n", s.name)
        assertEquals("closed", s.state)
        assertEquals("2025-01-01", s.startDate)
        assertEquals("2025-01-14", s.endDate)
        assertEquals("ship", s.goal)
    }

    @Test
    fun `LinkedIssue default status is null`() {
        val l = LinkedIssue(key = "k", summary = "s", relationship = "relates to", status = null)
        assertNull(l.status)
    }

    @Test
    fun `LinkedIssue stores provided values verbatim`() {
        val l =
            LinkedIssue(
                key = "k",
                summary = "s",
                relationship = "blocks",
                status = "Open",
            )
        assertEquals("k", l.key)
        assertEquals("s", l.summary)
        assertEquals("blocks", l.relationship)
        assertEquals("Open", l.status)
    }

    @Test
    fun `SprintIssue defaults are sensible`() {
        val s =
            SprintIssue(
                key = "k",
                summary = "s",
                description = "d",
                issueType = "T",
                status = "S",
                priority = null,
                assignee = null,
                labels = emptyList(),
                comments = "",
                url = "u",
            )
        assertNull(s.createdAt)
        assertNull(s.startedAt)
        assertNull(s.resolvedAt)
        assertEquals(0, s.qaLoopCount)
        assertTrue(s.linkedIssues.isEmpty())
    }

    @Test
    fun `SprintIssue stores provided values verbatim`() {
        val li = LinkedIssue(key = "L", summary = "Ls", relationship = "blocks", status = "S")
        val s =
            SprintIssue(
                key = "k",
                summary = "s",
                description = "d",
                issueType = "T",
                status = "S",
                priority = "High",
                assignee = "alice",
                labels = listOf("l1"),
                comments = "c",
                url = "u",
                createdAt = "2025-01-01",
                startedAt = "2025-01-02",
                resolvedAt = "2025-01-03",
                qaLoopCount = 2,
                linkedIssues = listOf(li),
            )
        assertEquals("2025-01-01", s.createdAt)
        assertEquals("2025-01-02", s.startedAt)
        assertEquals("2025-01-03", s.resolvedAt)
        assertEquals(2, s.qaLoopCount)
        assertEquals(listOf(li), s.linkedIssues)
    }

    @Test
    fun `JiraTicketConstants hold the documented shared constants`() {
        val maxes =
            listOf(
                JiraTicketConstants.MAX_COMMENTS,
                JiraTicketConstants.MAX_SEARCH_KEYWORDS,
                JiraTicketConstants.MAX_SIMILAR_TICKETS,
                JiraTicketConstants.MAX_SIMILAR_TICKETS_FOR_MR,
                JiraTicketConstants.MAX_MRS_PER_TICKET,
                JiraTicketConstants.MAX_TOTAL_MRS,
                JiraTicketConstants.MAX_FILES_PER_MR,
                JiraTicketConstants.MAX_REPO_CONTEXTS,
                JiraTicketConstants.MAX_REPO_FILES_PER_PROJECT,
                JiraTicketConstants.MAX_FILE_CONTENT_CHARS,
                JiraTicketConstants.MAX_FILES_FOR_CONTEXT,
            )
        assertTrue(maxes.isNotEmpty())
        maxes.forEach { assertTrue(it > 0) }
    }
}
