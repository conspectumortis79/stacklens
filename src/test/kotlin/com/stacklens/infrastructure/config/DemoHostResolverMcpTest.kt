package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DemoHostResolverMcpTest {
    private val resolver = DemoHostResolver()

    private var previousDockerFlag: Boolean = false

    @BeforeEach
    fun clearDockerFlag() {
        previousDockerFlag = java.lang.Boolean.getBoolean(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        System.clearProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
    }

    @AfterEach
    fun restoreDockerFlag() {
        if (previousDockerFlag) {
            System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        } else {
            System.clearProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        }
    }

    @Test
    fun `mcpHostFor returns stacklens-mcp when envValue is true`() {
        assertEquals("stacklens-mcp", resolver.mcpHostFor(envValue = "true"))
    }

    @Test
    fun `mcpHostFor returns stacklens-mcp when dockerFlag is true`() {
        assertEquals("stacklens-mcp", resolver.mcpHostFor(dockerFlag = true))
    }

    @Test
    fun `mcpHostFor returns localhost when both flags are false`() {
        assertEquals("localhost", resolver.mcpHostFor(envValue = "false", dockerFlag = false))
        assertEquals("localhost", resolver.mcpHostFor(envValue = null, dockerFlag = false))
    }

    @Test
    fun `mcpBaseUrlFor uses the default port`() {
        assertEquals(
            "http://stacklens-mcp:7456",
            resolver.mcpBaseUrlFor(envValue = "true"),
        )
        assertEquals(
            "http://localhost:7456",
            resolver.mcpBaseUrlFor(envValue = "false"),
        )
    }

    @Test
    fun `mcpBaseUrlFor honours a custom port`() {
        assertEquals(
            "http://stacklens-mcp:9000",
            resolver.mcpBaseUrlFor(port = 9000, envValue = "true"),
        )
    }

    @Test
    fun `refreshMcpBaseUrl rewrites stacklens-mcp to localhost when running natively`() {
        assertEquals(
            "http://localhost:7456",
            resolver.refreshMcpBaseUrl("http://stacklens-mcp:7456", envValue = "false"),
        )
    }

    @Test
    fun `refreshMcpBaseUrl rewrites localhost to stacklens-mcp when running in container`() {
        assertEquals(
            "http://stacklens-mcp:7456",
            resolver.refreshMcpBaseUrl("http://localhost:7456", envValue = "true"),
        )
    }

    @Test
    fun `refreshMcpBaseUrl leaves user-customised hosts alone`() {
        assertEquals(
            "http://my-mcp.lan:7456",
            resolver.refreshMcpBaseUrl("http://my-mcp.lan:7456", envValue = "true"),
        )
        assertEquals(
            "http://my-mcp.lan:7456",
            resolver.refreshMcpBaseUrl("http://my-mcp.lan:7456", envValue = "false"),
        )
    }

    @Test
    fun `refreshMcpBaseUrl leaves the URL unchanged when host already matches environment`() {
        assertEquals(
            "http://localhost:7456",
            resolver.refreshMcpBaseUrl("http://localhost:7456", envValue = "false"),
        )
        assertEquals(
            "http://stacklens-mcp:7456",
            resolver.refreshMcpBaseUrl("http://stacklens-mcp:7456", envValue = "true"),
        )
    }

    @Test
    fun `refreshMcpBaseUrl returns input unchanged for unparseable URLs`() {
        assertEquals(
            "not a url",
            resolver.refreshMcpBaseUrl("not a url", envValue = "true"),
        )
        assertEquals(
            "",
            resolver.refreshMcpBaseUrl("", envValue = "true"),
        )
    }
}
