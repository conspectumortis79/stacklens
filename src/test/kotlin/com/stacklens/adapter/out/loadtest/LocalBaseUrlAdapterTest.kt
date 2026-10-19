package com.stacklens.adapter.out.loadtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class LocalBaseUrlAdapterTest {
    @Test
    fun `currentBaseUrl returns http localhost with default port 8090`() {
        val adapter = LocalBaseUrlAdapter()
        ReflectionTestUtils.setField(adapter, "serverPort", 8090)
        assertEquals("http://localhost:8090", adapter.currentBaseUrl())
    }

    @Test
    fun `currentBaseUrl honours an overridden server port`() {
        val adapter = LocalBaseUrlAdapter()
        ReflectionTestUtils.setField(adapter, "serverPort", 9090)
        assertEquals("http://localhost:9090", adapter.currentBaseUrl())
    }

    @Test
    fun `currentBaseUrl handles a non-default high port`() {
        val adapter = LocalBaseUrlAdapter()
        ReflectionTestUtils.setField(adapter, "serverPort", 18090)
        assertEquals("http://localhost:18090", adapter.currentBaseUrl())
    }
}
