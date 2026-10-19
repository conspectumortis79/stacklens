package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoHostResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DemoHostResolverCoverageTest {
    private val resolver = DemoHostResolver()

    @Test
    fun `aligned host rewrites both loopback spellings to the ambient host`() {
        val ambient = resolver.hostFor()

        assertEquals(ambient, resolver.alignedHost(DemoHostResolver.LOCAL_HOST))
        assertEquals(ambient, resolver.alignedHost(DemoHostResolver.DOCKER_HOST))
    }

    @Test
    fun `aligned host leaves unrelated hosts untouched`() {
        assertEquals("confluence.internal", resolver.alignedHost("confluence.internal"))
    }

    @Test
    fun `refresh host keeps urls without a usable host`() {
        assertEquals("", resolver.refreshHost("", "false", false))
        assertEquals("http:8080", resolver.refreshHost("http:8080", "false", false))
        assertEquals("not a uri", resolver.refreshHost("not a uri", "false", false))
    }

    @Test
    fun `refresh host keeps urls whose host already matches the runtime`() {
        assertEquals("http://localhost:8080/api", resolver.refreshHost("http://localhost:8080/api", "false", false))
        assertEquals("http://other:8080/api", resolver.refreshHost("http://other:8080/api", "true", false))
    }

    @Test
    fun `refresh host swaps the loopback spelling that does not match the runtime`() {
        assertEquals("http://host.docker.internal:8080/api", resolver.refreshHost("http://localhost:8080/api", "true", false))
        assertEquals("http://localhost:8080/api", resolver.refreshHost("http://host.docker.internal:8080/api", "false", false))
    }

    @Test
    fun `refresh mcp base url swaps only the mcp defaults`() {
        assertEquals("http://localhost:7456", resolver.refreshMcpBaseUrl("http://stacklens-mcp:7456", "false", false))
        assertEquals("http://stacklens-mcp:7456", resolver.refreshMcpBaseUrl("http://localhost:7456", "true", false))
        assertEquals("http://elsewhere:7456", resolver.refreshMcpBaseUrl("http://elsewhere:7456", "true", false))
        assertEquals("bad uri", resolver.refreshMcpBaseUrl("bad uri", "true", false))
    }

    @Test
    fun `mcp base url and host defaults resolve against the ambient runtime`() {
        assertEquals("http://${resolver.mcpHostFor()}:${DemoHostResolver.MCP_DEFAULT_PORT}", resolver.mcpBaseUrlFor())
        assertEquals(DemoHostResolver.MCP_DOCKER_HOST, resolver.mcpHostFor("true", false))
        assertEquals(DemoHostResolver.LOCAL_HOST, resolver.mcpHostFor("false", false))
        assertEquals(DemoHostResolver.LOCAL_HOST, resolver.hostFor("explicit.docker.host", "false", false))
    }

    @Test
    fun `aligned service url and mcp url delegate to the refresh helpers`() {
        assertEquals("http://other:8080/api", resolver.alignedServiceUrl("http://other:8080/api"))
        assertEquals("http://elsewhere:7456", resolver.alignedMcpServerUrl("http://elsewhere:7456"))
    }
}
