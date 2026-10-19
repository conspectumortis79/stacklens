package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class K6ScriptTransformerCoverageTest {
    private val localBaseUrl = mock<LocalBaseUrlPort>()
    private val transformer = K6ScriptTransformerAdapter(localBaseUrl)

    @ParameterizedTest
    @CsvSource(
        "'',true",
        "'not a url',true",
        "'ftp://localhost:8085/',true",
        "'http://example.com:8085/api',false",
        "'http://localhost:8085/api',true",
        "'http://127.0.0.1:0/api',true",
        "'http://localhost:80/api',true",
        "'https://invalid:443/api',true",
        "'http://localhost/api',true",
    )
    fun `isSuspiciousTemplateDefault flags template defaults without consulting the local port`(
        url: String,
        expected: Boolean,
    ) {
        assertEquals(expected, transformer.isSuspiciousTemplateDefault(url))
    }

    @Test
    fun `isSuspiciousTemplateDefault accepts the port of the configured local base url`() {
        whenever(localBaseUrl.currentBaseUrl()).thenReturn("http://localhost:3000/")

        assertFalse(transformer.isSuspiciousTemplateDefault("http://localhost:3000/api"))
    }

    @Test
    fun `isSuspiciousTemplateDefault rejects ports that differ from the configured local port`() {
        whenever(localBaseUrl.currentBaseUrl()).thenReturn("http://localhost:3000/")

        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost:4000/api"))
    }

    @Test
    fun `isSuspiciousTemplateDefault accepts unknown ports when the local url has no port`() {
        whenever(localBaseUrl.currentBaseUrl()).thenReturn("http://localhost/")

        assertFalse(transformer.isSuspiciousTemplateDefault("http://localhost:4000/api"))
    }

    @Test
    fun `extractBaseUrlLiteral returns the quoted literal and null when blank or absent`() {
        assertEquals("http://localhost:8085", transformer.extractBaseUrlLiteral("const BASE_URL = 'http://localhost:8085'"))
        assertNull(transformer.extractBaseUrlLiteral("const BASE_URL = \"\""))
        assertNull(transformer.extractBaseUrlLiteral("no declaration"))
    }

    @Test
    fun `verifyPatchedBaseUrl reports the placeholder when no literal survives`() {
        assertEquals("<not found>", transformer.verifyPatchedBaseUrl("const URL = 'x'"))
    }
}
