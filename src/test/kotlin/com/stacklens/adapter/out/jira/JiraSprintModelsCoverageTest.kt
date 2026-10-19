package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class JiraSprintModelsCoverageTest {
    @Test
    fun `Sprint defaults are sensible`() {
        val s =
            Sprint(
                id = 1L,
                name = "Sprint 1",
                state = "active",
            )
        assertEquals(1L, s.id)
        assertEquals("Sprint 1", s.name)
        assertEquals("active", s.state)
        assertNull(s.startDate)
        assertNull(s.endDate)
        assertNull(s.boardId)
        assertNull(s.originBoardId)
        assertEquals("", s.goal)
    }

    @Test
    fun `Sprint stores every provided field verbatim`() {
        val start = Instant.parse("2025-01-01T00:00:00Z")
        val end = Instant.parse("2025-01-14T00:00:00Z")
        val s =
            Sprint(
                id = 7L,
                name = "Sprint 7",
                state = "closed",
                startDate = start,
                endDate = end,
                boardId = 11L,
                originBoardId = 22L,
                goal = "ship it",
            )
        assertEquals(7L, s.id)
        assertEquals("Sprint 7", s.name)
        assertEquals("closed", s.state)
        assertEquals(start, s.startDate)
        assertEquals(end, s.endDate)
        assertEquals(11L, s.boardId)
        assertEquals(22L, s.originBoardId)
        assertEquals("ship it", s.goal)
    }

    @Test
    fun `SprintIssue defaults are sensible`() {
        val i =
            SprintIssue(
                id = 1L,
                key = "PROJ-1",
                summary = "do the thing",
                status = "Open",
            )
        assertEquals(1L, i.id)
        assertEquals("PROJ-1", i.key)
        assertEquals("do the thing", i.summary)
        assertEquals("Open", i.status)
        assertNull(i.assignee)
        assertNull(i.priority)
        assertNull(i.storyPoints)
        assertNull(i.issueType)
        assertTrue(i.labels.isEmpty())
        assertTrue(i.fixVersions.isEmpty())
        assertNull(i.epic)
        assertNull(i.parentKey)
        assertTrue(i.linkedIssues.isEmpty())
        assertTrue(i.subtaskIds.isEmpty())
    }

    @Test
    fun `SprintIssue stores every provided field verbatim`() {
        val i =
            SprintIssue(
                id = 42L,
                key = "PROJ-42",
                summary = "important",
                status = "Done",
                assignee = "alice",
                priority = "High",
                storyPoints = 5.0,
                issueType = "Story",
                labels = listOf("ui", "frontend"),
                fixVersions = listOf("v1.0"),
                epic = "PROJ-100",
                parentKey = "PROJ-10",
                linkedIssues = listOf("PROJ-43"),
                subtaskIds = listOf(101L, 102L),
            )
        assertEquals(42L, i.id)
        assertEquals("PROJ-42", i.key)
        assertEquals("important", i.summary)
        assertEquals("Done", i.status)
        assertEquals("alice", i.assignee)
        assertEquals("High", i.priority)
        assertEquals(5.0, i.storyPoints)
        assertEquals("Story", i.issueType)
        assertEquals(listOf("ui", "frontend"), i.labels)
        assertEquals(listOf("v1.0"), i.fixVersions)
        assertEquals("PROJ-100", i.epic)
        assertEquals("PROJ-10", i.parentKey)
        assertEquals(listOf("PROJ-43"), i.linkedIssues)
        assertEquals(listOf(101L, 102L), i.subtaskIds)
    }

    @Test
    fun `Board default type is empty string`() {
        val b = Board(id = 1L, name = "My Board")
        assertEquals(1L, b.id)
        assertEquals("My Board", b.name)
        assertEquals("", b.type)
    }

    @Test
    fun `Board stores provided type verbatim`() {
        val b = Board(id = 2L, name = "Scrum", type = "scrum")
        assertEquals("scrum", b.type)
    }
}
