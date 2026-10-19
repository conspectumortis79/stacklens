package com.stacklens.adapter.out.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigEnvelopeTest {
    @Test
    fun `defaults`() {
        val envelope = ConfigEnvelope()
        assertEquals(1, envelope.version)
        assertTrue(envelope.encrypted)
        assertEquals("AES-256-GCM", envelope.algorithm)
        assertEquals("PBKDF2WithHmacSHA256", envelope.kdf)
        assertEquals(310_000, envelope.iterations)
        assertEquals("", envelope.salt)
        assertEquals("", envelope.iv)
        assertEquals("", envelope.data)
    }

    @Test
    fun `equality`() {
        val a =
            ConfigEnvelope(version = 2, encrypted = false, algorithm = "A", kdf = "B", iterations = 1, salt = "s", iv = "i", data = "d")
        val b = a.copy()
        assertEquals(a, b)
    }
}
