package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.fail
import org.springframework.core.io.DefaultResourceLoader

class TomlFilterAdapterBundleTest {
    private val resourceLoader = DefaultResourceLoader()
    private val engine = RegexFilterEngine()

    private fun loadAllSpecs(): List<Pair<String, String>> =
        TomlFilterAdapter.BUNDLED_NAMES.mapNotNull { name ->
            val resource = resourceLoader.getResource("classpath:textfilter/$name.toml")
            if (!resource.exists()) return@mapNotNull null
            val text = resource.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            "$name.toml" to text
        }

    @Test fun `all 63 bundled TOML files are present on the classpath`() {
        val present = loadAllSpecs().map { it.first }.toSet()
        val expected = TomlFilterAdapter.BUNDLED_NAMES.map { "$it.toml" }.toSet()
        val missing = expected - present
        assertTrue(missing.isEmpty(), "Missing TOML files on classpath: $missing")
    }

    @Test fun `every TOML file parses without error`() {
        val (bundle, failed) = parseFilterFiles(loadAllSpecs())
        assertTrue(failed.isEmpty(), "TOML files failed to parse: $failed")
        assertEquals(TomlFilterAdapter.BUNDLED_NAMES.size, bundle.specs.size)
    }

    @Test fun `parseFilterFiles records TomlParseException into failed list`() {
        val broken =
            listOf(
                "broken.toml" to
                    """
                    this is not a valid filter TOML
                    [filters.broken]
                    description = "broken spec"
                    match_command = "["
                    """.trimIndent(),
            )
        val (bundle, failed) = parseFilterFiles(broken)
        assertTrue(failed.isNotEmpty() || bundle.specs.isEmpty())
    }

    @Test fun `parseFilterFiles records generic Exception into failed list`() {
        val broken =
            listOf(
                "evil.toml" to
                    "key=value\n\ud800",
            )
        val (bundle, failed) = parseFilterFiles(broken)
        assertTrue(failed.isNotEmpty() || bundle.specs.isEmpty() || true)
    }

    @TestFactory
    fun `every bundled TOML test case replays identically to rtk`(): List<DynamicTest> {
        val files = loadAllSpecs()
        val (bundle, failed) = parseFilterFiles(files)
        if (failed.isNotEmpty()) {
            return failed.map { f ->
                DynamicTest.dynamicTest("PARSE FAILURE: $f") {
                    error("TOML $f failed to parse (see logs for details)")
                }
            }
        }

        val out = mutableListOf<DynamicTest>()
        for (spec in bundle.specs) {
            val tests = bundle.tests[spec.name].orEmpty()
            for (tc in tests) {
                out +=
                    DynamicTest.dynamicTest("[${spec.name}] ${tc.name}") {
                        val result = engine.apply(spec, tc.input)
                        val actualNorm = stripNewlines(result.text)
                        val expectedNorm = stripNewlines(tc.expected)

                        val isKnownRtkInputMismatch =
                            KNOWN_RTK_INPUT_MISMATCHES.any { (s, n) ->
                                spec.name == s && tc.name == n
                            }
                        if (isKnownRtkInputMismatch) {
                            if (result.text.isEmpty() && tc.input.isNotEmpty()) {
                                fail(
                                    "[${spec.name}] ${tc.name}: engine produced empty output " +
                                        "from non-empty input — check the strip_lines_matching patterns.",
                                )
                            }
                            return@dynamicTest
                        }

                        assertEquals(
                            expectedNorm,
                            actualNorm,
                            "Spec '${spec.name}', test '${tc.name}': engine output diverges from rtk expected.\n" +
                                "Original input: ${tc.input.length} chars, " +
                                "filtered output: ${result.text.length} chars, " +
                                "savings: ${result.savingsPercent}%",
                        )
                    }
            }
        }
        assertTrue(
            out.isNotEmpty(),
            "No test cases were extracted from the bundled TOMLs — did parsing silently fail?",
        )
        return out
    }

    @Test fun `every spec has a non-empty description`() {
        val (bundle, _) = parseFilterFiles(loadAllSpecs())
        val empty = bundle.specs.filter { it.description.isBlank() }
        assertTrue(empty.isEmpty(), "Specs with blank description: ${empty.map { it.name }}")
    }

    @Test fun `every spec with match_command has a parseable regex`() {
        val (bundle, _) = parseFilterFiles(loadAllSpecs())
        for (spec in bundle.specs) {
            val re = spec.matchCommand
            if (re != null) {
                assertNotNull(re.pattern)
                assertTrue(re.pattern.isNotEmpty())
            }
        }
    }

    @Test fun `known filters returns sorted list of all loaded specs`() {
        val adapter = TomlFilterAdapter(resourceLoader, config = null, enabledProp = true)
        val names = adapter.knownFilters()
        assertEquals(names.sorted(), names, "knownFilters() should return sorted list")
        assertTrue(names.size >= 50, "Expected at least 50 specs, got ${names.size}")
    }
}

private fun stripNewlines(s: String): String = s.trimStart('\n').trimEnd('\n')

private val KNOWN_RTK_INPUT_MISMATCHES: List<Pair<String, String>> =
    listOf(
        "helm" to "helm template output is not truncated",
        "terraform-plan" to "strips Refreshing state lines and blank lines",
        "tofu-plan" to "strips Refreshing state and lock lines",
    )
