package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class K6ScriptTransformerTemplateDefaultTest {
    private fun transformer(localUrl: String) =
        K6ScriptTransformerAdapter(
            object : LocalBaseUrlPort {
                override fun currentBaseUrl(): String = localUrl
            },
        )

    @Test
    fun `blank and unparsable urls count as a template default`() {
        val transformer = transformer("http://localhost:8085/")

        assertTrue(transformer.isSuspiciousTemplateDefault(""))
        assertTrue(transformer.isSuspiciousTemplateDefault("   "))
        assertTrue(transformer.isSuspiciousTemplateDefault("localhost:8085"))
        assertTrue(transformer.isSuspiciousTemplateDefault("ftp://localhost:21"))
    }

    @Test
    fun `any remote host is accepted as a real target`() {
        val transformer = transformer("http://localhost:8085/")

        assertFalse(transformer.isSuspiciousTemplateDefault("https://api.shop.example"))
        assertFalse(transformer.isSuspiciousTemplateDefault("https://api.shop.example:9000/orders"))
    }

    @Test
    fun `the well known loopback ports are treated as template defaults`() {
        val transformer = transformer("http://localhost:9999/")

        assertTrue(transformer.isSuspiciousTemplateDefault("http://localhost:8085"))
        assertTrue(transformer.isSuspiciousTemplateDefault("http://127.0.0.1:0"))
        assertTrue(transformer.isSuspiciousTemplateDefault("http://invalid"))
        assertTrue(transformer.isSuspiciousTemplateDefault("https://localhost:443/x"))
    }

    @Test
    fun `a loopback port that differs from the local instance is suspicious`() {
        assertTrue(transformer("http://localhost:9000/").isSuspiciousTemplateDefault("http://localhost:8086"))
        assertFalse(transformer("http://localhost:8086/").isSuspiciousTemplateDefault("http://localhost:8086"))
    }

    @Test
    fun `a local instance url without a port cannot contradict the script port`() {
        assertFalse(transformer("http://localhost/").isSuspiciousTemplateDefault("http://localhost:8086"))
    }

    @Test
    fun `the base url literal is extracted only when it carries a value`() {
        val transformer = transformer("http://localhost:8085/")

        assertEquals("https://api.shop.example", transformer.extractBaseUrlLiteral("const BASE_URL = 'https://api.shop.example'"))
        assertEquals(null, transformer.extractBaseUrlLiteral("export default {}"))
        assertEquals(null, transformer.extractBaseUrlLiteral("const BASE_URL = \"  \""))
        assertEquals(
            "http://localhost:9000",
            transformer.extractBaseUrlLiteral("let BASE_URL = __ENV.BASE_URL || \"http://localhost:9000\""),
        )
        assertNull(transformer.extractBaseUrlLiteral(""))
    }

    @Test
    fun `sanitising promotes the largest scenario ramp down and injects a graceful stop`() {
        val script =
            """
            export const options = {
              scenarios: {
                a: { executor: 'constant-arrival-rate', duration: '10s', gracefulRampDown: '5s' },
                b: { executor: 'constant-vus', vus: 2, gracefulRampDown: '30s' },
              },
            };
            export default function () {}
            """.trimIndent()

        val sanitised = transformer("http://localhost:8085/").sanitizeScriptForK6V2(script)

        assertTrue(sanitised.contains("gracefulRampDown: '30s'"), sanitised)
        assertTrue(sanitised.contains("gracefulStop"), sanitised)
    }

    @Test
    fun `sanitising a script without a newline after the options brace leaves the ramp down in place`() {
        val script = "export const options = { scenarios: { a: { gracefulRampDown: '30s' } } };"

        val sanitised = transformer("http://localhost:8085/").sanitizeScriptForK6V2(script)

        assertTrue(sanitised.contains("gracefulRampDown: '30s'"), sanitised)
    }

    @Test
    fun `duration parsing accepts every k6 suffix and rejects noise`() {
        val transformer = transformer("http://localhost:8085/")

        assertEquals(30.0, transformer.parseK6DurationSeconds("30s"))
        assertEquals(120.0, transformer.parseK6DurationSeconds("2m"))
        assertEquals(3600.0, transformer.parseK6DurationSeconds("1h"))
        assertEquals(0.5, transformer.parseK6DurationSeconds("500ms"))
        assertEquals(null, transformer.parseK6DurationSeconds("soon"))
    }

    @Test
    fun `verification of a patched script falls back to a placeholder`() {
        val transformer = transformer("http://localhost:8085/")

        assertEquals("<not found>", transformer.verifyPatchedBaseUrl("no literal here"))
        assertEquals("http://a", transformer.verifyPatchedBaseUrl("const BASE_URL = 'http://a'"))
    }

    @Test
    fun `patching keeps a script that has no base url literal`() {
        val transformer = transformer("http://localhost:8085/")

        assertEquals("export default () => {}", transformer.patchScriptForLocalDefault("export default () => {}"))
    }

    @Test
    fun `patching skips a script that already points at the local instance`() {
        val transformer = transformer("http://localhost:8085/")

        assertEquals(
            "const BASE_URL = 'http://localhost:8085/'",
            transformer.patchScriptForLocalDefault("const BASE_URL = 'http://localhost:8085/'"),
        )
    }
}
