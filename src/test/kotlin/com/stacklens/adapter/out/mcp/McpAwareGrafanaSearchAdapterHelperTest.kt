package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.search.GrafanaSearchPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class McpAwareGrafanaSearchAdapterHelperTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<GrafanaSearchPort>()
    private val mapper = ObjectMapper()
    private val adapter = McpAwareGrafanaSearchAdapter(mcpRepo, gateway, mapper, delegate)

    private fun matchesFilter(
        dashJson: String,
        filters: List<String>,
    ): Boolean {
        val func = McpAwareGrafanaSearchAdapter::class.declaredFunctions.first { it.name == "matchesFilter" }
        func.javaMethod!!.isAccessible = true
        val node = mapper.readTree(dashJson)
        return try {
            func.call(adapter, node, filters) as Boolean
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause!!
        }
    }

    private fun extractQuerySnippet(
        targetJson: String,
        keyword: String,
    ): String {
        val func = McpAwareGrafanaSearchAdapter::class.declaredFunctions.first { it.name == "extractQuerySnippet" }
        func.javaMethod!!.isAccessible = true
        val node = mapper.readTree(targetJson)
        return try {
            func.call(adapter, node, keyword) as String
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause!!
        }
    }

    @Test
    fun `matchesFilter returns true when filters is empty`() {
        val dash = """{"url":"/d/abc/test","title":"Test","uid":"test"}"""
        assertTrue(matchesFilter(dash, emptyList()))
    }

    @Test
    fun `matchesFilter returns true when filter is found in url`() {
        val dash = """{"url":"/d/abc/test","title":"X","uid":"test"}"""
        assertTrue(matchesFilter(dash, listOf("test")))
    }

    @Test
    fun `matchesFilter returns true when filter is found in title`() {
        val dash = """{"url":"/d/abc/X","title":"Production","uid":"prod"}"""
        assertTrue(matchesFilter(dash, listOf("production")))
    }

    @Test
    fun `matchesFilter returns true when filter equals uid`() {
        val dash = """{"url":"/d/abc/X","title":"X","uid":"my-uid"}"""
        assertTrue(matchesFilter(dash, listOf("my-uid")))
    }

    @Test
    fun `matchesFilter returns false when filter is not found`() {
        val dash = """{"url":"/d/abc/test","title":"Test","uid":"test"}"""
        assertEquals(false, matchesFilter(dash, listOf("missing")))
    }

    @Test
    fun `matchesFilter handles leading and trailing slashes in filter`() {
        val dash = """{"url":"/d/abc/test","title":"X","uid":"test"}"""
        assertTrue(matchesFilter(dash, listOf("/test/")))
    }

    @Test
    fun `matchesFilter returns false when filter matches neither url nor title`() {
        val dash = """{"url":"/d/abc/test","title":"Test","uid":"test"}"""
        assertEquals(false, matchesFilter(dash, listOf("nonexistent")))
    }

    @Test
    fun `extractQuerySnippet returns query containing keyword`() {
        val target = """{"expr":"sum(cpu_usage)"}"""
        val out = extractQuerySnippet(target, "cpu")
        assertTrue(out.contains("cpu"))
    }

    @Test
    fun `extractQuerySnippet returns fallback when no field contains keyword`() {
        val target = """{"someField":"value"}"""
        val out = extractQuerySnippet(target, "missing")
        assertTrue(out.contains("Query contains"))
    }
}
