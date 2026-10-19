package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraChangelog
import com.stacklens.adapter.out.shared.JiraChangelogHistory
import com.stacklens.adapter.out.shared.JiraChangelogItem
import com.stacklens.adapter.out.shared.JiraIssueLink
import com.stacklens.adapter.out.shared.JiraIssueLinkType
import com.stacklens.adapter.out.shared.JiraLinkedIssueFields
import com.stacklens.adapter.out.shared.JiraLinkedIssueRef
import com.stacklens.adapter.out.shared.JiraNamedField
import com.stacklens.adapter.out.shared.JiraSprintIssueFields
import com.stacklens.adapter.out.shared.JiraSprintIssueItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraSprintDtoTest {
    @Test
    fun `JiraSprintResponse default and explicit constructors`() {
        val s = JiraSprintResponse()
        assertEquals(0L, s.id)
        assertEquals("", s.name)
        assertEquals("", s.state)
        assertNull(s.startDate)
        assertNull(s.endDate)
        assertNull(s.goal)
        val s2 =
            JiraSprintResponse(id = 1L, name = "S1", state = "active", startDate = "2024-01-01", endDate = "2024-01-31", goal = "G")
        assertEquals(1L, s2.id)
        assertEquals("G", s2.goal)
    }

    @Test
    fun `JiraSprintListResponse default and explicit constructors`() {
        val l = JiraSprintListResponse()
        assertTrue(l.values.isEmpty())
        val l2 = JiraSprintListResponse(values = listOf(JiraSprintResponse(id = 1L, name = "S", state = "active")))
        assertEquals(1, l2.values.size)
    }

    @Test
    fun `JiraSprintIssuesResponse default and explicit constructors`() {
        val r = JiraSprintIssuesResponse()
        assertEquals(0, r.total)
        assertTrue(r.issues.isEmpty())
        val r2 =
            JiraSprintIssuesResponse(total = 10, issues = listOf(JiraSprintIssueItem(key = "K1")))
        assertEquals(10, r2.total)
        assertEquals(1, r2.issues.size)
    }

    @Test
    fun `JiraSprintIssueItem default and explicit constructors`() {
        val i = JiraSprintIssueItem()
        assertEquals("", i.key)
        assertEquals(JiraSprintIssueFields(), i.fields)
        assertNull(i.changelog)
        val i2 =
            JiraSprintIssueItem(key = "K1", fields = JiraSprintIssueFields(summary = "S"), changelog = JiraChangelog())
        assertEquals("K1", i2.key)
        assertEquals(JiraChangelog(), i2.changelog)
    }

    @Test
    fun `JiraChangelog default and explicit constructors`() {
        val c = JiraChangelog()
        assertTrue(c.histories.isEmpty())
        val c2 = JiraChangelog(histories = listOf(JiraChangelogHistory()))
        assertEquals(1, c2.histories.size)
    }

    @Test
    fun `JiraChangelogHistory default and explicit constructors`() {
        val h = JiraChangelogHistory()
        assertNull(h.created)
        assertTrue(h.items.isEmpty())
        val h2 = JiraChangelogHistory(created = "2024-01-01", items = listOf(JiraChangelogItem(field = "status")))
        assertEquals("2024-01-01", h2.created)
        assertEquals(1, h2.items.size)
    }

    @Test
    fun `JiraChangelogItem default and explicit constructors`() {
        val i = JiraChangelogItem()
        assertNull(i.field)
        assertNull(i.fromString)
        assertNull(i.toString)
        val i2 = JiraChangelogItem(field = "status", fromString = "Open", toString = "Done")
        assertEquals("Open", i2.fromString)
    }

    @Test
    fun `JiraIssueLinkType default and explicit constructors`() {
        val t = JiraIssueLinkType()
        assertNull(t.name)
        assertNull(t.inward)
        assertNull(t.outward)
        val t2 = JiraIssueLinkType(name = "blocks", inward = "is blocked by", outward = "blocks")
        assertEquals("blocks", t2.name)
    }

    @Test
    fun `JiraLinkedIssueFields default and explicit constructors`() {
        val f = JiraLinkedIssueFields()
        assertNull(f.summary)
        assertNull(f.status)
        val f2 = JiraLinkedIssueFields(summary = "S", status = JiraNamedField(name = "Open"))
        assertEquals("Open", f2.status?.name)
    }

    @Test
    fun `JiraLinkedIssueRef default and explicit constructors`() {
        val r = JiraLinkedIssueRef()
        assertNull(r.key)
        assertNull(r.fields)
        val r2 = JiraLinkedIssueRef(key = "K1", fields = JiraLinkedIssueFields(summary = "S"))
        assertEquals("K1", r2.key)
    }

    @Test
    fun `JiraIssueLink default and explicit constructors`() {
        val l = JiraIssueLink()
        assertNull(l.type)
        assertNull(l.inwardIssue)
        assertNull(l.outwardIssue)
        val l2 =
            JiraIssueLink(
                type = JiraIssueLinkType(name = "blocks"),
                inwardIssue = JiraLinkedIssueRef(key = "K1"),
                outwardIssue = JiraLinkedIssueRef(key = "K2"),
            )
        assertEquals("blocks", l2.type?.name)
    }
}
