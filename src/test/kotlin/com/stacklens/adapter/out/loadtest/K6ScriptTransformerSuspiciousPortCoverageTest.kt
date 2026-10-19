package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class K6ScriptTransformerSuspiciousPortCoverageTest {
    private val transformer = K6ScriptTransformerAdapter(mock<LocalBaseUrlPort>())

    @Test
    fun `the templated default port on a local host is always suspicious`() {
        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost:8085/x"))
    }

    @Test
    fun `a zero port on a local host is suspicious`() {
        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost:0/x"))
    }

    @Test
    fun `the implicit http port on a local host is suspicious`() {
        assertTrue(transformer.isSuspiciousTemplateDefault("http://127.0.0.1/x"))
    }

    @Test
    fun `the https port on a local host is suspicious`() {
        assertTrue(transformer.isSuspiciousTemplateDefault("https://localhost:443/x"))
    }

    @Test
    fun `a blank url is suspicious`() {
        assertTrue(transformer.isSuspiciousTemplateDefault("   "))
    }
}
