package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import com.stacklens.testsupport.DigitHashCollisions
import com.stacklens.testsupport.HashCollisions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class K6ScriptTransformerPortCollisionCoverageTest {
    private fun transformer(localBaseUrl: String) =
        K6ScriptTransformerAdapter(
            object : LocalBaseUrlPort {
                override fun currentBaseUrl(): String = localBaseUrl
            },
        )

    private fun collisionFor(port: String): String {
        val collision = DigitHashCollisions.sharingHashCodeOf(port)
        assertTrue(collision.all { it.isDigit() }, "$collision must consist of digits only")
        assertNotEquals(port, collision)
        assertEquals(HashCollisions.unsignedHashCodeOf(port), HashCollisions.unsignedHashCodeOf(collision))
        return collision
    }

    @ParameterizedTest
    @ValueSource(strings = ["8085", "0", "80", "443"])
    fun `a digit string that only collides with a template default port is compared by value`(port: String) {
        val collision = collisionFor(port)
        val transformer = transformer("http://localhost:3000/")

        assertTrue(
            transformer.isSuspiciousTemplateDefault("http://localhost:$port/api"),
            "the real template default $port stays suspicious",
        )
        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost:$collision/api"))
        assertFalse(transformer.isSuspiciousTemplateDefault("http://localhost:3000/api"))
    }

    @Test
    fun `an unparsable per-scenario ramp down is ranked as zero seconds`() {
        val script =
            """
            export const options = {
              scenarios: {
                a: {
                  executor: 'constant-vus',
                  gracefulRampDown: 'nonsense',
                },
                b: {
                  executor: 'constant-vus',
                  gracefulRampDown: '30s',
                },
              },
            }
            """.trimIndent()

        val sanitized = transformer("http://localhost:3000/").sanitizeScriptForK6V2(script)

        assertTrue(sanitized.contains("gracefulRampDown: '30s'"), "the parsable duration wins the promotion:\n$sanitized")
        assertFalse(sanitized.contains("nonsense"))
    }
}
