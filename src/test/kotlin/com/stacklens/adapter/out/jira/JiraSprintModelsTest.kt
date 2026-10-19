package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class JiraSprintModelsTest {
    @Test
    fun `Sprint default and explicit constructors`() {
        val s = Sprint(id = 1L, name = "S1", state = "active")
        assertEquals(1L, s.id)
        assertEquals("S1", s.name)
        assertEquals("active", s.state)
        assertNull(s.startDate)
        assertNull(s.endDate)
        assertNull(s.boardId)
        assertNull(s.originBoardId)
        assertEquals("", s.goal)
        val now = Instant.now()
        val s2 =
            Sprint(
                id = 2L,
                name = "S2",
                state = "closed",
                startDate = now,
                endDate = now,
                boardId = 3L,
                originBoardId = 4L,
                goal = "G",
            )
        assertEquals(now, s2.startDate)
        assertEquals(now, s2.endDate)
        assertEquals(3L, s2.boardId)
        assertEquals(4L, s2.originBoardId)
        assertEquals("G", s2.goal)
    }

    @Test
    fun `SprintIssue default and explicit constructors`() {
        val i = SprintIssue(id = 1L, key = "K1", summary = "S", status = "Open")
        assertEquals(1L, i.id)
        assertEquals("K1", i.key)
        assertEquals("S", i.summary)
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
        val i2 =
            SprintIssue(
                id = 2L,
                key = "K2",
                summary = "S2",
                status = "Done",
                assignee = "Alice",
                priority = "High",
                storyPoints = 5.0,
                issueType = "Bug",
                labels = listOf("a"),
                fixVersions = listOf("v1"),
                epic = "E1",
                parentKey = "P1",
                linkedIssues = listOf("K3"),
                subtaskIds = listOf(10L),
            )
        assertEquals(2L, i2.id)
        assertEquals("Alice", i2.assignee)
        assertEquals("High", i2.priority)
        assertEquals(5.0, i2.storyPoints)
        assertEquals("Bug", i2.issueType)
        assertEquals(listOf("a"), i2.labels)
        assertEquals(listOf("v1"), i2.fixVersions)
        assertEquals("E1", i2.epic)
        assertEquals("P1", i2.parentKey)
        assertEquals(listOf("K3"), i2.linkedIssues)
        assertEquals(listOf(10L), i2.subtaskIds)
    }

    @Test
    fun `Board default and explicit constructors`() {
        val b = Board(id = 1L, name = "B")
        assertEquals(1L, b.id)
        assertEquals("B", b.name)
        assertEquals("", b.type)
        val b2 = Board(id = 2L, name = "B2", type = "scrum")
        assertEquals(2L, b2.id)
        assertEquals("B2", b2.name)
        assertEquals("scrum", b2.type)
    }
}
