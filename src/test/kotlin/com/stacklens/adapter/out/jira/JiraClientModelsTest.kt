package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraClientModelsTest {
    @Test
    fun `JiraSearchResponse default constructor`() {
        val r = JiraClient.JiraSearchResponse()
        assertTrue(r.issues.isEmpty())
        assertNull(r.total)
        assertNull(r.startAt)
    }

    @Test
    fun `JiraIssue default constructor`() {
        val i = JiraClient.JiraIssue()
        assertEquals("", i.key)
        assertEquals(JiraClient.JiraFields(), i.fields)
    }

    @Test
    fun `JiraFields default constructor`() {
        val f = JiraClient.JiraFields()
        assertNull(f.summary)
        assertNull(f.description)
        assertNull(f.issuetype)
        assertNull(f.status)
        assertNull(f.assignee)
        assertNull(f.priority)
        assertNull(f.updated)
        assertNull(f.attachment)
    }

    @Test
    fun `JiraAttachment default constructor`() {
        val a = JiraClient.JiraAttachment()
        assertEquals("", a.id)
        assertEquals("", a.filename)
        assertEquals("", a.content)
    }

    @Test
    fun `JiraNamedValue default constructor`() {
        val v = JiraClient.JiraNamedValue()
        assertNull(v.name)
    }

    @Test
    fun `JiraAssignee default constructor`() {
        val a = JiraClient.JiraAssignee()
        assertNull(a.displayName)
        assertNull(a.name)
    }

    @Test
    fun `JiraFields with full values`() {
        val f =
            JiraClient.JiraFields(
                summary = "s",
                issuetype = JiraClient.JiraNamedValue(name = "Bug"),
                status = JiraClient.JiraNamedValue(name = "Open"),
                assignee = JiraClient.JiraAssignee(displayName = "A"),
                priority = JiraClient.JiraNamedValue(name = "High"),
                updated = "now",
                attachment = listOf(JiraClient.JiraAttachment(id = "1")),
            )
        assertEquals("s", f.summary)
        assertEquals("Bug", f.issuetype?.name)
        assertEquals("Open", f.status?.name)
        assertEquals("A", f.assignee?.displayName)
        assertEquals("High", f.priority?.name)
        assertEquals("now", f.updated)
        assertEquals(1, f.attachment?.size)
    }
}
