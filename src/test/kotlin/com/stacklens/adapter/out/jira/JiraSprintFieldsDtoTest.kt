package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraAssigneeField
import com.stacklens.adapter.out.shared.JiraCommentContainer
import com.stacklens.adapter.out.shared.JiraIssueLink
import com.stacklens.adapter.out.shared.JiraNamedField
import com.stacklens.adapter.out.shared.JiraSprintIssueFields
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JiraSprintFieldsDtoTest {
    @Test
    fun `JiraSprintIssueFields default constructor`() {
        val f = JiraSprintIssueFields()
        assertNull(f.summary)
        assertNull(f.description)
        assertNull(f.issuetype)
        assertNull(f.status)
        assertNull(f.priority)
        assertNull(f.assignee)
        assertNull(f.labels)
        assertNull(f.comment)
        assertNull(f.created)
        assertNull(f.resolutiondate)
        assertNull(f.statuscategorychangedate)
        assertNull(f.issuelinks)
    }

    @Test
    fun `JiraSprintIssueFields with values`() {
        val f =
            JiraSprintIssueFields(
                summary = "S",
                description = null,
                issuetype = JiraNamedField(name = "Bug"),
                status = JiraNamedField(name = "Open"),
                priority = JiraNamedField(name = "High"),
                assignee = JiraAssigneeField(displayName = "Alice"),
                labels = listOf("a"),
                comment = JiraCommentContainer(),
                created = "2024-01-01",
                resolutiondate = "2024-01-31",
                statuscategorychangedate = "2024-01-02",
                issuelinks = listOf(JiraIssueLink()),
            )
        assertEquals("S", f.summary)
        assertEquals("Alice", f.assignee?.displayName)
        assertEquals(1, f.issuelinks?.size)
    }

    @Test
    fun `JiraNamedField default constructor`() {
        assertNull(JiraNamedField().name)
    }

    @Test
    fun `JiraNamedField with values`() {
        assertEquals("Bug", JiraNamedField(name = "Bug").name)
    }

    @Test
    fun `JiraAssigneeField default constructor`() {
        assertNull(JiraAssigneeField().displayName)
    }

    @Test
    fun `JiraAssigneeField with values`() {
        assertEquals("Bob", JiraAssigneeField(displayName = "Bob").displayName)
    }
}
