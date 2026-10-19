package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class K6ScriptTransformerBranchTest {
    private fun transformer(localUrl: String = "http://localhost:8090"): K6ScriptTransformerAdapter =
        K6ScriptTransformerAdapter(
            localBaseUrl =
                object : LocalBaseUrlPort {
                    override fun currentBaseUrl(): String = localUrl
                },
        )

    private fun options(body: List<String>): String =
        buildString {
            append("export const options = {\n")
            body.forEach { append(it).append('\n') }
            append("};\n")
        }

    private fun scenarios(vararg inner: String): List<String> = listOf("  scenarios: {") + inner.map { "    $it" } + listOf("  },")

    private fun stages(vararg entries: String): List<String> = listOf("  stages: [") + entries.map { "    $it" } + listOf("  ],")

    @Test
    fun `a top level array field is moved together with its nested brackets`() {
        val script =
            options(
                stages("[1, 2],", "{ duration: '30s', target: 10 },") +
                    scenarios("smoke: {", "  executor: 'constant-vus',", "},"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(result.contains("default: {"), result)
        assertFalse(result.substringBefore("scenarios:").contains("stages:"), result)
        assertTrue(result.contains("executor: 'ramping-vus',"), result)
        assertTrue(result.contains("exec: 'default',"), result)
    }

    @Test
    fun `a top level object field is moved with its braces`() {
        val script =
            options(
                listOf("  stages: {", "    ramp: { duration: '1s' },", "  },") +
                    scenarios("smoke: {", "  vus: 1,", "},"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(result.contains("executor: 'ramping-vus',"), result)
        assertTrue(result.substringAfter("default: {").contains("stages: {"), result)
    }

    @Test
    fun `an explicit top level executor wins over the derived one`() {
        val script =
            options(
                listOf("  executor: 'ramping-vus',", "  executor: 'constant-vus',") +
                    stages("{ duration: '1s', target: 2 },") +
                    scenarios("smoke: {", "  duration: '10s',", "},"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertEquals(1, Regex("""executor:""").findAll(result).count(), result)
        assertTrue(result.contains("executor: 'ramping-vus',"), result)
        assertFalse(result.contains("executor: 'constant-vus',"), result)
    }

    @Test
    fun `a top level exec field is not duplicated into the default scenario`() {
        val script =
            options(
                listOf("  exec: 'default',") +
                    stages("{ duration: '1s', target: 1 },") +
                    scenarios("smoke: {", "  executor: 'constant-vus',", "},"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertFalse(result.substringBefore("scenarios:").contains("exec:"), result)
        assertTrue(result.contains("executor: 'ramping-vus',"), result)
    }

    @Test
    fun `vus and duration on their own select the constant vus executor`() {
        val script =
            options(
                listOf("  vus: 3,", "  duration: '30s',") +
                    scenarios("smoke: {", *stages("{ duration: '1s', target: 1 },").map { "  $it" }.toTypedArray(), "},"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(result.contains("executor: 'constant-vus',"), result)
    }

    @Test
    fun `duration alone selects the constant vus executor`() {
        val script =
            options(
                listOf("  duration: '30s',") +
                    scenarios("smoke: {", *stages("{ duration: '1s', target: 1 },").map { "  $it" }.toTypedArray(), "},"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(result.contains("executor: 'constant-vus',"), result)
    }

    @Test
    fun `neither stages nor a fixed load profile falls back to ramping vus`() {
        val script =
            options(
                listOf("  iterations: 100,", "  startVUs: 1,") +
                    scenarios("smoke: {", *stages("{ duration: '1s', target: 1 },").map { "  $it" }.toTypedArray(), "},"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)
        val moved = result.substringAfter("default: {")

        assertTrue(moved.contains("executor: 'ramping-vus',"), moved)
        assertTrue(moved.contains("iterations: 100,"), moved)
        assertTrue(moved.contains("startVUs: 1,"), moved)
    }

    @Test
    fun `an unbalanced scenarios block still receives the default scenario`() {
        val script =
            options(
                stages("{ duration: '1s', target: 1 },") +
                    listOf("  scenarios: {", "    smoke: {", "      executor: 'constant-vus',", "    },"),
            )

        val result = transformer().moveTopLevelStagesIntoDefaultScenario(script)

        assertTrue(result.contains("default: {"), result)
    }

    @Test
    fun `a script without the options header is returned untouched`() {
        val script = "export default function () {}"

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `a script without a closing brace is returned untouched`() {
        val script = "export const options = {\n  stages: [\n  scenarios: {\n"

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `stages or scenarios missing on their own leaves the script untouched`() {
        val withoutStages = options(listOf("  vus: 1,") + scenarios("smoke: {", "  exec: 'smoke',", "},"))
        val withoutScenarios = options(stages("{ duration: '1s', target: 1 },"))

        assertEquals(withoutStages, transformer().moveTopLevelStagesIntoDefaultScenario(withoutStages))
        assertEquals(withoutScenarios, transformer().moveTopLevelStagesIntoDefaultScenario(withoutScenarios))
    }

    @Test
    fun `an existing default scenario is never rewritten again`() {
        val script =
            options(
                stages("{ duration: '1s', target: 1 },") +
                    listOf("  scenarios: {", "    default: {", "      exec: 'default',", "    },", "  },"),
            )

        assertEquals(script, transformer().moveTopLevelStagesIntoDefaultScenario(script))
    }

    @Test
    fun `a graceful ramp down without a trailing newline stays at the top level`() {
        val script = "export const options = {\n  vus: 1,\n  gracefulRampDown: '30s'"

        val result = transformer().sanitizeScriptForK6V2(script)

        assertTrue(result.contains("gracefulRampDown: '30s'"), result)
        assertTrue(result.contains("gracefulStop: '5s',"), result)
    }

    @Test
    fun `the longest per scenario ramp down is promoted to the top level`() {
        val script =
            options(
                listOf(
                    "  scenarios: {",
                    "    smoke: {",
                    "      gracefulRampDown: '30s',",
                    "    },",
                    "    soak: {",
                    "      gracefulRampDown: '5m',",
                    "    },",
                    "    broken: {",
                    "      gracefulRampDown: 'not-a-duration',",
                    "    },",
                    "  },",
                ),
            )

        val result = transformer().sanitizeScriptForK6V2(script)

        assertTrue(result.contains("gracefulRampDown: '5m',"), result)
        assertFalse(result.contains("gracefulRampDown: '30s'"), result)
        assertFalse(result.contains("gracefulRampDown: 'not-a-duration'"), result)
    }

    @Test
    fun `a single ramp down is promoted even when it cannot be parsed`() {
        val script =
            options(
                listOf(
                    "  scenarios: {",
                    "    smoke: {",
                    "      gracefulRampDown: 'nope',",
                    "    },",
                    "  },",
                ),
            )

        val result = transformer().sanitizeScriptForK6V2(script)

        assertTrue(result.contains("gracefulRampDown: 'nope',"), result)
    }

    @Test
    fun `a script without the options header keeps its ramp down untouched`() {
        val script = "export default function () {}"

        assertEquals(script, transformer().sanitizeScriptForK6V2(script))
    }

    @Test
    fun `every suspicious template port is recognised and a real local port is not`() {
        val k6 = transformer("http://localhost:8090")

        listOf(
            "http://localhost:8085",
            "http://localhost:0",
            "http://localhost:80",
            "http://localhost:443",
            "http://localhost",
            "https://127.0.0.1",
            "http://invalid",
            "http://localhost:8091",
            "",
            "   ",
            "not a url",
            "ftp://localhost:21",
        ).forEach { assertTrue(k6.isSuspiciousTemplateDefault(it), it) }

        listOf(
            "http://localhost:8090",
            "https://load.example.com:8443",
            "http://10.0.0.5:8080",
        ).forEach { assertFalse(k6.isSuspiciousTemplateDefault(it), it) }
    }
}
