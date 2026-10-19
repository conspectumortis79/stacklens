package com.stacklens.adapter.out.knowledge.jira

import com.fasterxml.jackson.databind.node.NullNode
import com.stacklens.adapter.out.jira.DefaultJiraApiVersionResolver
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class JiraHierarchyAdapterKeyAndAdfCoverageTest {
    private val adapter = JiraHierarchyAdapter(mock<ReactiveHttpClientFactory>(), DefaultJiraApiVersionResolver())

    @Test
    fun `a key is extracted case insensitively and uppercased`() {
        assertEquals("ABC-123", adapter.extractTicketKey("  siehe abc-123 bitte "))
    }

    @Test
    fun `text without any ticket key yields no key`() {
        assertNull(adapter.extractTicketKey("kein ticket weit und breit"))
    }

    @Test
    fun `a null adf node flattens to an empty string`() {
        assertEquals("", flattenAdf(null))
    }

    @Test
    fun `a json null node flattens to an empty string`() {
        assertEquals("", flattenAdf(NullNode.instance))
    }

    private fun flattenAdf(node: Any?): Any? = PrivateInvocation.invoke(adapter, "flattenAdf", node)
}
