package com.stacklens.loader

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException

class ClassCryptoTest {
    private val key = deriveKey("test-passphrase")

    @Test
    fun `deriveKey returns 32 bytes`() {
        assertEquals(32, deriveKey("x").size)
    }

    @Test
    fun `deriveKey is deterministic`() {
        assertArrayEquals(deriveKey("k"), deriveKey("k"))
    }

    @Test
    fun `deriveKey differs for different passphrases`() {
        assertNotEquals(deriveKey("a").toList(), deriveKey("b").toList())
    }

    @Test
    fun `encrypt produces a blob that starts with the SLENC magic`() {
        val blob = encrypt("hello".toByteArray(), key)
        assertTrue(blob.size >= 6)
        for (i in 0 until 6) {
            assertEquals(MAGIC[i], blob[i], "magic mismatch at byte $i")
        }
    }

    @Test
    fun `encrypt produces non-deterministic ciphertext for identical inputs`() {
        val a = encrypt("same".toByteArray(), key)
        val b = encrypt("same".toByteArray(), key)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `decrypt round-trips the plaintext`() {
        val plain = "the quick brown fox jumps over the lazy dog".toByteArray()
        val blob = encrypt(plain, key)
        assertArrayEquals(plain, decrypt(blob, key))
    }

    @Test
    fun `decrypt round-trips empty plaintext`() {
        val blob = encrypt(ByteArray(0), key)
        assertArrayEquals(ByteArray(0), decrypt(blob, key))
    }

    @Test
    fun `decrypt rejects data that is too short`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                decrypt(ByteArray(5), key)
            }
        assertTrue("short" in ex.message!!.lowercase())
    }

    @Test
    fun `decrypt rejects data with wrong magic header`() {
        val bad = ByteArray(64)
        for (i in bad.indices) bad[i] = 0
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                decrypt(bad, key)
            }
        assertTrue("magic" in ex.message!!.lowercase())
    }

    @Test
    fun `decrypt throws when the ciphertext has been tampered with`() {
        val blob = encrypt("payload".toByteArray(), key)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0xFF).toByte()
        assertThrows(AEADBadTagException::class.java) {
            decrypt(blob, key)
        }
    }

    @Test
    fun `decrypt throws when the wrong key is supplied`() {
        val blob = encrypt("payload".toByteArray(), key)
        val wrong = deriveKey("not-the-same-key")
        assertThrows(AEADBadTagException::class.java) {
            decrypt(blob, wrong)
        }
    }

    @Test
    fun `decrypt catches magic mismatch on the very last magic byte`() {
        val blob = encrypt("payload".toByteArray(), key)
        blob[MAGIC.size - 1] = (blob[MAGIC.size - 1].toInt() xor 0xFF).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            decrypt(blob, key)
        }
    }
}
