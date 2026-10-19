package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraSprintIssueFieldResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JiraSprintIssueFieldResolverCompanionTest {
    @Test
    fun `DONE_STATUSES is a non-empty set of German and English names`() {
        val set = JiraSprintIssueFieldResolver.DONE_STATUSES
        assertNotNull(set)
        assertEquals(true, set.isNotEmpty())
    }

    @Test
    fun `QA_STATUSES is a non-empty set`() {
        val set = JiraSprintIssueFieldResolver.QA_STATUSES
        assertNotNull(set)
        assertEquals(true, set.isNotEmpty())
    }

    @Test
    fun `START_STATUSES is a non-empty set`() {
        val set = JiraSprintIssueFieldResolver.START_STATUSES
        assertNotNull(set)
        assertEquals(true, set.isNotEmpty())
    }
}
