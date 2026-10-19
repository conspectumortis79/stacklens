package com.stacklens.adapter.out.loadtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class K6ScriptTransformerProbeTest {
    private fun transformer(localUrl: String = "http://localhost:6333") =
        K6ScriptTransformerAdapter(
            localBaseUrl =
                object : com.stacklens.application.port.out.loadtest.LocalBaseUrlPort {
                    override fun currentBaseUrl(): String = localUrl
                },
        )

    @Test
    fun `a script without a BASE_URL literal is returned untouched`() {
        val script = "export default function () {}"

        assertEquals(script, transformer().patchScriptForLocalDefault(script))
        assertNull(transformer().extractBaseUrlLiteral(script))
    }

    @Test
    fun `a BASE_URL that already matches the local instance is kept`() {
        val script = "const BASE_URL = 'http://localhost:6333'\n"

        assertEquals(script, transformer("http://localhost:6333").patchScriptForLocalDefault(script))
    }

    @Test
    fun `patchScriptForLocalDefault replaces the literal with the local instance url`() {
        val script = "const BASE_URL = 'https://load.example.com'\n"

        val patched = transformer("http://localhost:6333").patchScriptForLocalDefault(script)

        assertEquals("const BASE_URL = 'http://localhost:6333'\n", patched)
    }

    @Test
    fun `patchScriptForLocalDefault honours an explicit replacement and env defaults`() {
        val script = "let BASE_URL = __ENV.BASE_URL || \"http://localhost:8085\";\n"

        val patched = transformer("http://localhost:6333").patchScriptForLocalDefault(script, forcedReplacement = "http://forced:1")

        assertEquals("const BASE_URL = 'http://forced:1';\n", patched)
    }

    @Test
    fun `the verification log tolerates a script without a const declaration`() {
        val script = "const BASE_URL = 'http://a:1'\nvar OTHER = 2\n"

        assertEquals("const BASE_URL = 'http://localhost:6333'\nvar OTHER = 2\n", transformer().patchScriptForLocalDefault(script))
    }

    @Test
    fun `template defaults are recognised as suspicious`() {
        val k6 = transformer()

        listOf("", "   ", "not a url", "http://localhost:8085", "http://localhost", "https://127.0.0.1", "http://invalid:443", "http://localhost:0")
            .forEach { url ->
                assertTrue(k6.isSuspiciousTemplateDefault(url), "$url must be treated as a template default")
            }
    }

    @Test
    fun `a concrete instance url is not suspicious but a foreign port is`() {
        val k6 = transformer("http://localhost:6333")

        assertFalse(k6.isSuspiciousTemplateDefault("http://localhost:6333"))
        assertTrue(k6.isSuspiciousTemplateDefault("http://localhost:7000"))
        assertFalse(k6.isSuspiciousTemplateDefault("https://api.example.com"))
    }

    @Test
    fun `a local url without a port matches the default ports`() {
        val k6 = transformer("http://localhost:6333")

        assertTrue(k6.isSuspiciousTemplateDefault("http://localhost"))
        assertTrue(k6.isSuspiciousTemplateDefault("https://localhost:80"))
        assertTrue(k6.isSuspiciousTemplateDefault("https://localhost:8443"))
        assertFalse(k6.isSuspiciousTemplateDefault("https://localhost:6333"))
    }

    @Test
    fun `the local port is ignored when the instance url has no port`() {
        val k6 = transformer("http://localhost:6333")

        assertTrue(k6.isSuspiciousTemplateDefault("http://localhost:8085"))
    }

    @Test
    fun `a local port that is blank cannot be compared`() {
        val k6 = transformer("http://localhost:")

        assertFalse(k6.isSuspiciousTemplateDefault("http://localhost:7000"))
    }

    @Test
    fun `k6 durations are converted to seconds`() {
        val k6 = transformer()

        assertEquals(1.5, k6.parseK6DurationSeconds("1500ms"))
        assertEquals(30.0, k6.parseK6DurationSeconds("30s"))
        assertEquals(120.0, k6.parseK6DurationSeconds("2m"))
        assertEquals(3600.0, k6.parseK6DurationSeconds("1h"))
        assertNull(k6.parseK6DurationSeconds("1h 30s 500ms"))
        assertNull(k6.parseK6DurationSeconds("nonsense"))
        assertNull(k6.parseK6DurationSeconds("0s"))
    }

    @Test
    fun `scripts without an options block are returned unchanged`() {
        val script = "export default function () {}"

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
        assertEquals(script, transformer().sanitizeScriptForK6V2(script))
    }

    @Test
    fun `an options block without a closing line is returned unchanged`() {
        val script = "export const options = {\n  stages: [{ duration: '1s', target: 2 }]\n"

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `top level fields are kept when only stages are present`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
            };
            """.trimIndent()

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `an existing default scenario is left alone`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
              scenarios: {
                default: { executor: 'constant-vus' },
              },
            };
            """.trimIndent()

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `top level stages move into a generated default scenario`() {
        val script =
            """
            export const options = {
              stages: [
                { duration: '1s', target: 2 },
              ],
              scenarios: {
                smoke: { exec: 'smoke', duration: '1s' },
              },
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("default: {"), moved)
        assertTrue(moved.contains("executor: 'ramping-vus',"), moved)
        assertTrue(moved.contains("exec: 'default',"), moved)
        assertTrue(moved.contains("smoke: { exec: 'smoke', duration: '1s' },"), moved)
    }

    @Test
    fun `a constant vus block moves with its executor and keeps an explicit exec`() {
        val script =
            """
            export const options = {
              vus: 3,
              duration: '10s',
              exec: 'run',
              scenarios: {
                smoke: { executor: 'constant-vus', duration: '1s' },
              },
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("executor: 'constant-vus',"), moved)
        assertFalse(moved.contains("exec: 'default',"), moved)
        assertTrue(moved.contains("vus: 3,"), moved)
    }

    @Test
    fun `an explicit executor line is preserved and unknown fields stay at the top level`() {
        val script =
            """
            export const options = {
              throws: false,
              executor: 'ramping-arrival-rate',
              iterations: 5,
              scenarios: {
                smoke: { duration: '1s' },
              },
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("executor: 'ramping-arrival-rate',"), moved)
        assertTrue(moved.contains("throws: false,"), moved)
        assertTrue(moved.contains("iterations: 5,"), moved)
    }

    @Test
    fun `a trailing comma is added when the last scenario line has none`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
              scenarios: {
                smoke: { duration: '1s' }
              },
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("smoke: { duration: '1s' }\n"), moved)
        assertTrue(moved.contains("default: {"), moved)
    }

    @Test
    fun `an empty scenarios block is tolerated`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
              scenarios: {},
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("default: {"), moved)
    }

    @Test
    fun `sanitizing strips per-scenario ramp downs and promotes the largest one`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
              scenarios: {
                smoke: {
                  gracefulRampDown: '30s',
                },
                soak: {
                  gracefulRampDown: '2m',
                },
              },
            };
            """.trimIndent()

        val sanitized = transformer().sanitizeScriptForK6V2(script)

        assertFalse(sanitized.contains("gracefulRampDown: '30s'"), sanitized)
        assertTrue(sanitized.contains("gracefulRampDown: '2m',"), sanitized)
        assertTrue(sanitized.contains("gracefulStop: '5s',"), sanitized)
    }

    @Test
    fun `the patch verification reports a missing const declaration`() {
        val k6 = transformer()

        assertEquals("<not found>", k6.verifyPatchedBaseUrl("let BASE_URL = \"http://localhost:6333\""))
        assertEquals("http://localhost:6333", k6.verifyPatchedBaseUrl("const BASE_URL = 'http://localhost:6333'"))
    }

    @Test
    fun `an empty base url literal counts as no literal`() {
        val k6 = transformer()

        assertNull(k6.extractBaseUrlLiteral("const BASE_URL = \"\"\n"))
        assertEquals("http://x", k6.extractBaseUrlLiteral("var BASE_URL = 'http://x'"))
    }

    @Test
    fun `a local instance url without a port is compared against the default ports`() {
        val k6 = transformer("http://localhost:6333")

        assertTrue(k6.isSuspiciousTemplateDefault("https://localhost:443"))
        assertTrue(k6.isSuspiciousTemplateDefault("http://127.0.0.1:8085"))
        assertTrue(k6.isSuspiciousTemplateDefault("http://invalid:1"))
        assertTrue(k6.isSuspiciousTemplateDefault("http://localhost:0"))
        assertFalse(k6.isSuspiciousTemplateDefault("http://localhost:6333"))
    }

    @Test
    fun `an instance url without a usable port never matches by port`() {
        val k6 = transformer("http://localhost:keins")

        assertFalse(k6.isSuspiciousTemplateDefault("http://localhost:9999"))
        assertTrue(k6.isSuspiciousTemplateDefault("http://localhost:8085"))
    }

    @Test
    fun `top level fields inside the scenarios block are not extracted again`() {
        val script =
            """
            export const options = {
              scenarios: {
                smoke: {
                  stages: [{ duration: '1s', target: 2 }],
                },
              },
            };
            """.trimIndent()

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `an unterminated array swallows the scenarios block so nothing is moved`() {
        val script =
            """
            export const options = {
              freiTextOhneDoppelpunkt
              thresholds: {
                'http_req_failed': [{ threshold: 'rate<0.01' }]
              },
              stages: [
                { duration: '1s', target: 3 },
              scenarios: {
                smoke: { duration: '1s' }
              },
            };
            """.trimIndent()

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `a script without top level stages is never rewritten`() {
        val script =
            """
            export const options = {
              exec: 'run',
              vus: 1,
              scenarios: {
                smoke: { duration: '1s' },
              },
            };
            """.trimIndent()

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `an exec line at the top level is kept inside the default scenario`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
              exec: 'run',
              scenarios: {
                smoke: { duration: '1s' },
              },
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("executor: 'ramping-vus',"), moved)
        assertTrue(moved.contains("stages: [{ duration: '1s', target: 2 }],"), moved)
        assertTrue(!moved.contains("exec: 'default',"), moved)
    }

    @Test
    fun `a nested stages block keeps the constant executor and gains a trailing comma`() {
        val script =
            """
            export const options = {
              vus: 2,
              duration: '5s',
              scenarios: {
                smoke: {
                  stages: [{ duration: '1s', target: 1 }],
                }
              }
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("executor: 'constant-vus',"), moved)
        assertTrue(moved.contains(",\n    default: {"), moved)
        assertTrue(moved.contains("exec: 'default',"), moved)
    }

    @Test
    fun `a scenarios block without an opening brace is left alone`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
              scenarios: REFERENZ,
            };
            """.trimIndent()

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `only iterations are moved with the ramping executor`() {
        val script =
            """
            export const options = {
              stages: [{ duration: '1s', target: 2 }],
              iterations: 3,
              scenarios: {
                smoke: { duration: '1s' },
              },
            };
            """.trimIndent()

        val moved = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(moved.contains("iterations: 3,"), moved)
        assertTrue(moved.contains("executor: 'ramping-vus',"), moved)
    }

    @Test
    fun `sanitizing without an options block keeps the script unchanged`() {
        val script = "const x = 1\n"

        assertEquals(script, transformer().sanitizeScriptForK6V2(script))
    }

    @Test
    fun `an existing top level ramp down is kept while per-scenario ones vanish`() {
        val script =
            """
            export const options = {
              gracefulRampDown: '60s',
              scenarios: {
                main: {
                  gracefulRampDown: '5s',
                },
              },
            };
            """.trimIndent()

        val sanitized = transformer().sanitizeScriptForK6V2(script)

        assertTrue(sanitized.contains("gracefulRampDown: '60s'"), sanitized)
        assertFalse(sanitized.contains("gracefulRampDown: '5s'"), sanitized)
    }

    @Test
    fun `sanitizing keeps an existing top level graceful stop`() {
        val script =
            """
            export const options = {
              gracefulStop: '30s',
              scenarios: {
                smoke: {
                  gracefulRampDown: '10s',
                },
              },
            };
            """.trimIndent()

        val sanitized = transformer().sanitizeScriptForK6V2(script)

        assertTrue(sanitized.contains("gracefulStop: '30s',"), sanitized)
        assertFalse(sanitized.contains("gracefulStop: '5s'"), sanitized)
        assertTrue(sanitized.contains("gracefulRampDown: '10s',"), sanitized)
    }
}
