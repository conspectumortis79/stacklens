package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoHostResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DemoHostResolverUrlCoverageTest {
    private val resolver = DemoHostResolver()

    @Test
    fun `a url without a scheme keeps the browser facing host`() {
        assertEquals("://demo.internal", resolver.refreshHost("://demo.internal"))
        assertEquals("://demo.internal", resolver.refreshMcpBaseUrl("://demo.internal"))
    }

    @Test
    fun `a url without a host keeps the browser facing host`() {
        assertEquals("http://", resolver.refreshHost("http://"))
        assertEquals("http://", resolver.refreshMcpBaseUrl("http://"))
    }

    @Test
    fun `a url that cannot be parsed keeps the browser facing host`() {
        assertEquals("not a url at all", resolver.refreshHost("not a url at all"))
        assertEquals("not a url at all", resolver.refreshMcpBaseUrl("not a url at all"))
    }

    @Test
    fun `the docker host is swapped for the loopback host outside a container`() {
        assertEquals(
            "http://localhost:8080",
            resolver.refreshHost("http://host.docker.internal:8080", envValue = null, dockerFlag = false),
        )
    }

    @Test
    fun `the loopback host is swapped for the docker host inside a container`() {
        assertEquals(
            "http://host.docker.internal:8080",
            resolver.refreshHost("http://localhost:8080", envValue = "true", dockerFlag = false),
        )
    }

    @Test
    fun `a third party host is never rewritten`() {
        assertEquals("https://gitlab.example", resolver.refreshHost("https://gitlab.example"))
        assertEquals("https://gitlab.example", resolver.refreshMcpBaseUrl("https://gitlab.example"))
    }

    @Test
    fun `mcp urls are aligned with the mcp service name of the container`() {
        assertEquals(
            "http://localhost:7456",
            resolver.refreshMcpBaseUrl("http://stacklens-mcp:7456", envValue = null, dockerFlag = false),
        )
        assertEquals(
            "http://stacklens-mcp:7456",
            resolver.refreshMcpBaseUrl("http://localhost:7456", envValue = null, dockerFlag = true),
        )
        assertEquals("http://stacklens-mcp:7456", resolver.refreshMcpBaseUrl("http://stacklens-mcp:7456", dockerFlag = true))
    }

    @Test
    fun `the mcp base url follows the container flag`() {
        assertEquals("http://localhost:7456", resolver.mcpBaseUrlFor(envValue = null, dockerFlag = false))
        assertEquals("http://stacklens-mcp:9999", resolver.mcpBaseUrlFor(port = 9999, envValue = "true", dockerFlag = false))
        assertEquals("host.docker.internal", resolver.hostFor(envValue = "true"))
        assertEquals("stacklens-mcp", resolver.mcpHostFor(dockerFlag = true))
    }

    @Test
    fun `the known default hosts are replaced by the host of the current environment`() {
        val expected = resolver.hostFor()

        assertEquals(expected, resolver.alignedHost(DemoHostResolver.LOCAL_HOST))
        assertEquals(expected, resolver.alignedHost(DemoHostResolver.DOCKER_HOST))
        assertEquals("gitlab.example", resolver.alignedHost("gitlab.example"))
    }
}
