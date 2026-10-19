package com.stacklens.adapter.out.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class ConfigEnvelopeCipherAdapterTest {
    private val mapper = ObjectMapper()

    private val adapter = ConfigEnvelopeCipherAdapter(mapper)

    private val enc = Base64.getEncoder()

    @Test
    fun `deriveKey produces 256-bit key from password and salt`() {
        val salt = ByteArray(16) { it.toByte() }
        val key = deriveKey("my-password", salt, 1000)
        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size)
    }

    @Test
    fun `deriveKey produces different keys for different passwords`() {
        val salt = ByteArray(16) { 0 }
        val key1 = deriveKey("password1", salt, 1000)
        val key2 = deriveKey("password2", salt, 1000)
        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun `encrypt produces an envelope that decrypts back to the same payload`() {
        val plaintext = """{"test":"data","value":42}""".toByteArray()
        val password = "secure-password-123"
        val envelope = adapter.encrypt(plaintext, password)
        val decrypted = adapter.decrypt(envelope, password)
        assertEquals(String(plaintext), String(decrypted!!))
    }

    @Test
    fun `encrypt writes the AES-GCM envelope format`() {
        val envelope = adapter.encrypt("plain".toByteArray(), "pw")
        val parsed = mapper.readValue(envelope, ConfigEnvelope::class.java)
        assertTrue(parsed.encrypted)
        assertEquals("AES-256-GCM", parsed.algorithm)
        assertEquals("PBKDF2WithHmacSHA256", parsed.kdf)
        assertEquals(310_000, parsed.iterations)
        assertTrue(parsed.salt.isNotBlank())
        assertTrue(parsed.iv.isNotBlank())
        assertTrue(parsed.data.isNotBlank())
    }

    @Test
    fun `encrypt produces different ciphertext on each call due to random IV`() {
        val plaintext = "same content".toByteArray()
        val e1 = mapper.readValue(adapter.encrypt(plaintext, "pw"), ConfigEnvelope::class.java)
        val e2 = mapper.readValue(adapter.encrypt(plaintext, "pw"), ConfigEnvelope::class.java)
        assertNotEquals(e1.data, e2.data)
        assertNotEquals(e1.iv, e2.iv)
    }

    @Test
    fun `decrypt returns null for a wrong password`() {
        val envelope = adapter.encrypt("secret payload".toByteArray(), "right-password")
        assertNull(adapter.decrypt(envelope, "wrong-password"))
    }

    @Test
    fun `decrypt rejects malformed base64 envelope fields`() {
        val envelope =
            mapper.writeValueAsBytes(
                ConfigEnvelope(
                    iterations = 1,
                    salt = "!!!not-base64!!!",
                    iv = "!!!not-base64!!!",
                    data = "!!!not-base64!!!",
                ),
            )
        assertThrows<IllegalArgumentException> {
            adapter.decrypt(envelope, "pw")
        }
    }

    @Test
    fun `decrypt caps hostile iteration counts`() {
        val envelope =
            mapper.writeValueAsBytes(
                ConfigEnvelope(
                    iterations = 5_000_000,
                    salt = enc.encodeToString(ByteArray(16)),
                    iv = enc.encodeToString(ByteArray(12)),
                    data = enc.encodeToString(ByteArray(16)),
                ),
            )
        assertNull(adapter.decrypt(envelope, "pw"))
    }

    @Test
    fun `decrypt floors zero iterations`() {
        val envelope =
            mapper.writeValueAsBytes(
                ConfigEnvelope(
                    iterations = 0,
                    salt = enc.encodeToString(ByteArray(16)),
                    iv = enc.encodeToString(ByteArray(12)),
                    data = enc.encodeToString(ByteArray(16)),
                ),
            )
        assertNull(adapter.decrypt(envelope, "pw"))
    }
}
