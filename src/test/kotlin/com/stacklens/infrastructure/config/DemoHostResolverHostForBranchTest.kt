package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoHostResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DemoHostResolverHostForBranchTest {
    @Test
    fun `hostFor returns dockerHost when env value is true`() {
        val result =
            DemoHostResolver().hostFor(
                dockerHost = "my-docker-host.example.com",
                envValue = "true",
                dockerFlag = false,
            )
        assertEquals("my-docker-host.example.com", result)
    }

    @Test
    fun `hostFor returns dockerHost when dockerFlag is true`() {
        val result =
            DemoHostResolver().hostFor(
                dockerHost = "my-docker-host.example.com",
                envValue = "false",
                dockerFlag = true,
            )
        assertEquals("my-docker-host.example.com", result)
    }

    @Test
    fun `hostFor returns LOCAL_HOST when neither env value nor dockerFlag is set`() {
        val result =
            DemoHostResolver().hostFor(
                dockerHost = "my-docker-host.example.com",
                envValue = "false",
                dockerFlag = false,
            )
        assertEquals("localhost", result)
    }

    @Test
    fun `hostFor returns LOCAL_HOST when env value is null`() {
        val result =
            DemoHostResolver().hostFor(
                dockerHost = "my-docker-host.example.com",
                envValue = null,
                dockerFlag = false,
            )
        assertEquals("localhost", result)
    }

    @Test
    fun `hostFor returns dockerHost when env value is true even if dockerFlag is false`() {
        val result =
            DemoHostResolver().hostFor(
                dockerHost = "my-docker-host.example.com",
                envValue = "true",
                dockerFlag = false,
            )
        assertEquals("my-docker-host.example.com", result)
    }

    @Test
    fun `hostFor returns dockerHost when dockerFlag is true even if env value is null`() {
        val result =
            DemoHostResolver().hostFor(
                dockerHost = "my-docker-host.example.com",
                envValue = null,
                dockerFlag = true,
            )
        assertEquals("my-docker-host.example.com", result)
    }

    @Test
    fun `hostFor treats env value other than true as false`() {
        val result =
            DemoHostResolver().hostFor(
                dockerHost = "my-docker-host.example.com",
                envValue = "yes",
                dockerFlag = false,
            )
        assertEquals("localhost", result)
    }
}
