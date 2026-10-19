package com.stacklens.shared.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class HttpAuthHeadersTest {
    @Test
    fun `returns Bearer token when username is blank`() {
        assertEquals("Bearer mytoken123", bearerOrBasicAuthHeader("", "mytoken123"))
    }

    @Test
    fun `returns Bearer token when username is whitespace only`() {
        assertEquals("Bearer pat-xyz", bearerOrBasicAuthHeader("   ", "pat-xyz"))
    }

    @Test
    fun `returns Basic header when username is set`() {
        val expectedEncoded = Base64.getEncoder().encodeToString("alice@example.com:secret".toByteArray())
        assertEquals("Basic $expectedEncoded", bearerOrBasicAuthHeader("alice@example.com", "secret"))
    }

    @Test
    fun `Basic header decodes to username colon token`() {
        val header = bearerOrBasicAuthHeader("bob", "apikey456")
        assertTrue(header.startsWith("Basic "))
        val decoded = String(Base64.getDecoder().decode(header.removePrefix("Basic ")))
        assertEquals("bob:apikey456", decoded)
    }
}
