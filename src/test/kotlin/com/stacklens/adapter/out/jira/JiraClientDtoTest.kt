package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraClientDtoTest {
    private val mapper = ObjectMapper()

    @Test
    fun `JiraClient_JiraSearchResponse default constructor`() {
        val r = JiraClient.JiraSearchResponse()
        assertTrue(r.issues.isEmpty())
        assertNull(r.total)
        assertNull(r.startAt)
    }

    @Test
    fun `JiraClient_JiraSearchResponse with values`() {
        val r =
            JiraClient.JiraSearchResponse(
                issues = listOf(JiraClient.JiraIssue(key = "K1")),
                total = 42,
                startAt = 0,
            )
        assertEquals(1, r.issues.size)
        assertEquals(42, r.total)
    }

    @Test
    fun `JiraClient_JiraIssue default constructor`() {
        val i = JiraClient.JiraIssue()
        assertEquals("", i.key)
        assertEquals(JiraClient.JiraFields(), i.fields)
    }

    @Test
    fun `JiraClient_JiraIssue with values`() {
        val i = JiraClient.JiraIssue(key = "ABC", fields = JiraClient.JiraFields(summary = "S"))
        assertEquals("ABC", i.key)
        assertEquals("S", i.fields.summary)
    }

    @Test
    fun `JiraClient_JiraFields default constructor`() {
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
    fun `JiraClient_JiraFields with values`() {
        val f =
            JiraClient.JiraFields(
                summary = "S",
                description = null,
                issuetype = JiraClient.JiraNamedValue(name = "Bug"),
                status = JiraClient.JiraNamedValue(name = "Open"),
                assignee = JiraClient.JiraAssignee(displayName = "Alice"),
                priority = JiraClient.JiraNamedValue(name = "High"),
                updated = "2024-01-01",
                attachment = listOf(JiraClient.JiraAttachment(id = "1")),
            )
        assertEquals("S", f.summary)
        assertEquals("Alice", f.assignee?.displayName)
        assertEquals(1, f.attachment?.size)
    }

    @Test
    fun `JiraClient_JiraAttachment default constructor`() {
        val a = JiraClient.JiraAttachment()
        assertEquals("", a.id)
        assertEquals("", a.filename)
        assertEquals("", a.content)
    }

    @Test
    fun `JiraClient_JiraAttachment with values`() {
        val a = JiraClient.JiraAttachment(id = "1", filename = "f.txt", content = "data")
        assertEquals("1", a.id)
        assertEquals("f.txt", a.filename)
        assertEquals("data", a.content)
    }

    @Test
    fun `JiraClient_JiraNamedValue default constructor`() {
        assertNull(JiraClient.JiraNamedValue().name)
    }

    @Test
    fun `JiraClient_JiraNamedValue with values`() {
        assertEquals("Bug", JiraClient.JiraNamedValue(name = "Bug").name)
    }

    @Test
    fun `JiraClient_JiraAssignee default constructor`() {
        val a = JiraClient.JiraAssignee()
        assertNull(a.displayName)
        assertNull(a.name)
    }

    @Test
    fun `JiraClient_JiraAssignee with values`() {
        val a = JiraClient.JiraAssignee(displayName = "Bob", name = "bob")
        assertEquals("Bob", a.displayName)
        assertEquals("bob", a.name)
    }
}
