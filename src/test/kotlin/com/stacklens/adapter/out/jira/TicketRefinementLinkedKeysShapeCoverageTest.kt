package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TicketRefinementLinkedKeysShapeCoverageTest {
    private val mapper = ObjectMapper()

    private fun adapter() =
        TicketRefinementNativeJiraAdapter(
            jiraTicketHttp = mock<JiraTicketHttpClient>(),
            objectMapper = mapper,
            adfToHtml = mock(),
            mdConverter = mock(),
            apiVersionResolver = mock<JiraApiVersionResolver>(),
        )

    @Test
    fun `blank and missing keys are dropped from links and subtasks`() {
        val parent =
            mapper.readTree(
                """
                {"fields":{
                  "issuelinks":[
                    {"inwardIssue":{"key":"  "},"outwardIssue":{"key":"REL-1"}},
                    {"inwardIssue":{"key":"REL-2"},"outwardIssue":{}},
                    {}
                  ],
                  "subtasks":[{"key":""},{"key":"SUB-1"},{"summary":"no key"}]
                }}
                """.trimIndent(),
            )

        val keys = adapter().linkedKeysFrom(parent, "", "", "PARENT-1")

        assertEquals(listOf("REL-1", "REL-2", "SUB-1"), keys)
    }

    @Test
    fun `mentioned keys in summary and description are collected`() {
        val keys =
            adapter()
                .linkedKeysFrom(
                    mapper.readTree("""{"fields":{}}"""),
                    "see OTHER-9 and AGAIN-3 for details",
                    "starts with FIRST-2",
                    "PARENT-1",
                )

        assertEquals(listOf("FIRST-2", "OTHER-9", "AGAIN-3"), keys)
    }
}
