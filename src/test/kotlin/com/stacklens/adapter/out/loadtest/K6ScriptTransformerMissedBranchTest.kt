package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class K6ScriptTransformerMissedBranchTest {
    private fun transformer(localBaseUrl: String) =
        K6ScriptTransformerAdapter(
            object : LocalBaseUrlPort {
                override fun currentBaseUrl(): String = localBaseUrl
            },
        )

    @Test
    fun `suspicious local defaults on the shipped template ports are rejected`() {
        val transformer = transformer("http://localhost:3000/")

        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost:8085/api"))
        assertTrue(transformer.isSuspiciousTemplateDefault("http://127.0.0.1:0/api"))
        assertTrue(transformer.isSuspiciousTemplateDefault("http://invalid:80/api"))
        assertTrue(transformer.isSuspiciousTemplateDefault("https://localhost:443/api"))
    }

    @Test
    fun `a local default without an explicit port uses the protocol default`() {
        val transformer = transformer("http://localhost:3000/")

        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost/api"))
        assertTrue(transformer.isSuspiciousTemplateDefault("https://127.0.0.1/api"))
    }

    @Test
    fun `a remote host is never a template default`() {
        val transformer = transformer("http://localhost:8085/api")

        assertFalse(transformer.isSuspiciousTemplateDefault("https://api.example.com:8085/v1"))
    }

    @Test
    fun `blank and unparsable urls count as template defaults`() {
        val transformer = transformer("http://localhost:3000/")

        assertTrue(transformer.isSuspiciousTemplateDefault("   "))
        assertTrue(transformer.isSuspiciousTemplateDefault("localhost:3000"))
    }

    @Test
    fun `a local port that differs from the running server is suspicious`() {
        val transformer = transformer("http://localhost:3000/")

        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost:9999/api"))
        assertFalse(transformer.isSuspiciousTemplateDefault("http://localhost:3000/api"))
    }

    @Test
    fun `a server url without a port cannot veto the local default`() {
        val transformer = transformer("http://example.internal/")

        assertFalse(transformer.isSuspiciousTemplateDefault("http://localhost:9999/api"))
    }

    @Test
    fun `per scenario ramp down is promoted into the options block`() {
        val script =
            """
            export const options = {
              scenarios: {
                smoke: {
                  executor: 'constant-vus',
                  gracefulRampDown: '30s',
                },
              },
            }

            export default function main() {}
            """.trimIndent()

        val result = transformer("http://localhost:3000/").sanitizeScriptForK6V2(script)

        assertTrue(result.contains("gracefulStop: '5s'"))
        assertEquals(1, Regex("gracefulRampDown").findAll(result).count())
        assertTrue(result.contains("gracefulRampDown: '30s'"))
    }

    @Test
    fun `the largest ramp down is promoted and a missing options block is left untouched`() {
        val script =
            """
            const scenarios = {
              a: {
                executor: 'constant-vus',
                gracefulRampDown: '10s',
              },
              b: {
                executor: 'ramping-vus',
                gracefulRampDown: '1m',
              },
            }

            export default function main() {}
            """.trimIndent()

        val result = transformer("http://localhost:3000/").sanitizeScriptForK6V2(script)

        assertFalse(result.contains("gracefulRampDown: '1m'"))
        assertFalse(result.contains("gracefulRampDown"))
    }

    @Test
    fun `an existing top level ramp down survives sanitising`() {
        val script =
            """
            export const options = {
              gracefulRampDown: '15s',
              gracefulStop: '10s',
              scenarios: {
                smoke: { gracefulRampDown: '5s', },
              },
            }
            """.trimIndent()

        val result = transformer("http://localhost:3000/").sanitizeScriptForK6V2(script)

        assertTrue(result.contains("gracefulRampDown: '15s'"))
        assertEquals(1, Regex("gracefulStop").findAll(result).count())
    }
}
