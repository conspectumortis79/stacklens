package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoHostResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DemoHostResolverHostForEnvTest {
    @Test
    fun `hostFor returns dockerHost when envValue is true`() {
        assertEquals("host.docker.internal", DemoHostResolver().hostFor("host.docker.internal", envValue = "true"))
    }

    @Test
    fun `hostFor returns dockerHost when dockerFlag is true`() {
        assertEquals("host.docker.internal", DemoHostResolver().hostFor("host.docker.internal", dockerFlag = true))
    }

    @Test
    fun `hostFor returns localhost when both flags are false`() {
        assertEquals("localhost", DemoHostResolver().hostFor("host.docker.internal", envValue = "false", dockerFlag = false))
        assertEquals("localhost", DemoHostResolver().hostFor("host.docker.internal", envValue = null, dockerFlag = false))
    }

    @Test
    fun `hostFor returns localhost when envValue is something other than true`() {
        assertEquals("localhost", DemoHostResolver().hostFor("host.docker.internal", envValue = "1", dockerFlag = false))
        assertEquals("localhost", DemoHostResolver().hostFor("host.docker.internal", envValue = "yes", dockerFlag = false))
        assertEquals("localhost", DemoHostResolver().hostFor("host.docker.internal", envValue = "", dockerFlag = false))
    }
}
