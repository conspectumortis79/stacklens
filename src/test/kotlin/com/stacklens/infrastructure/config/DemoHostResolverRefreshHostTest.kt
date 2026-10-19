package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoHostResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DemoHostResolverRefreshHostTest {
    private val resolver = DemoHostResolver()

    @Test
    fun `refreshHost rewrites localhost to host-docker-internal when running in container`() {
        assertEquals(
            "http://host.docker.internal:8081",
            resolver.refreshHost("http://localhost:8081", envValue = "true", dockerFlag = false),
        )
        assertEquals(
            "http://host.docker.internal:8081",
            resolver.refreshHost("http://localhost:8081", envValue = "false", dockerFlag = true),
        )
    }

    @Test
    fun `refreshHost rewrites host-docker-internal to localhost when running natively`() {
        assertEquals(
            "http://localhost:8081",
            resolver.refreshHost("http://host.docker.internal:8081", envValue = "false", dockerFlag = false),
        )
        assertEquals(
            "http://localhost:8081",
            resolver.refreshHost("http://host.docker.internal:8081", envValue = null, dockerFlag = false),
        )
    }

    @Test
    fun `refreshHost leaves URL unchanged when host already matches environment`() {
        assertEquals(
            "http://host.docker.internal:8081",
            resolver.refreshHost("http://host.docker.internal:8081", envValue = "true", dockerFlag = false),
        )
        assertEquals(
            "http://localhost:8081",
            resolver.refreshHost("http://localhost:8081", envValue = "false", dockerFlag = false),
        )
    }

    @Test
    fun `refreshHost leaves user-customised URLs alone`() {
        assertEquals(
            "https://gitlab.example.com",
            resolver.refreshHost("https://gitlab.example.com", envValue = "true", dockerFlag = false),
        )
        assertEquals(
            "http://gitlab.internal:3080",
            resolver.refreshHost("http://gitlab.internal:3080", envValue = "false", dockerFlag = false),
        )
    }

    @Test
    fun `refreshHost returns input unchanged for unparseable URLs`() {
        assertEquals(
            "not a url",
            resolver.refreshHost("not a url", envValue = "true", dockerFlag = false),
        )
        assertEquals(
            "",
            resolver.refreshHost("", envValue = "true", dockerFlag = false),
        )
    }
}
