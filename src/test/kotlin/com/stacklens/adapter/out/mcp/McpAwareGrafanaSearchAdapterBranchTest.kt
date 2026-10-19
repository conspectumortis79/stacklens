package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareGrafanaSearchAdapterBranchTest {
    @Test
    fun `McpAwareGrafanaSearchAdapter is constructible`() {
        val adp =
            McpAwareGrafanaSearchAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.search.GrafanaSearchPort>(),
            )
        assertNotNull(adp)
    }

    @Test
    fun `McpAwareGrafanaSearchAdapter exposes a shared executor instance`() {
        val adp =
            McpAwareGrafanaSearchAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.search.GrafanaSearchPort>(),
            )
        val method = McpAwareGrafanaSearchAdapter::class.java.getDeclaredMethod("getScanExecutor")
        method.isAccessible = true
        val first = method.invoke(adp)
        val second = method.invoke(adp)
        assertNotNull(first, "scanExecutor must produce a non-null executor")
        assertNotNull(second, "scanExecutor must produce a non-null executor on subsequent calls")
        @Suppress("UNCHECKED_CAST")
        val firstExec = first as? java.util.concurrent.ExecutorService

        @Suppress("UNCHECKED_CAST")
        val secondExec = second as? java.util.concurrent.ExecutorService
        assertNotNull(firstExec, "scanExecutor must return an ExecutorService")
        assertNotNull(secondExec, "scanExecutor must return an ExecutorService on subsequent calls")
        assertTrue(
            firstExec === secondExec,
            "scanExecutor must return a singleton instance to avoid paying thread-creation cost on every scan",
        )
    }
}
