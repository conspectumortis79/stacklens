package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TomlFilterParserTest {
    private val parser = TomlFilterParser()

    @Test fun `parses minimal spec with description only`() {
        val (spec, tests) =
            parser.parseOrFail(
                """
                [filters.minimal]
                description = "tiny spec"
                """.trimIndent(),
                "minimal.toml",
            )
        assertEquals("minimal", spec.name)
        assertEquals("tiny spec", spec.description)
        assertNull(spec.matchCommand)
        assertTrue(spec.stripAnsi)
        assertTrue(spec.stripLinesMatching.isEmpty())
        assertTrue(tests.isEmpty())
    }

    @Test fun `parses all scalar fields`() {
        val (spec, _) =
            parser.parseOrFail(
                """
                [filters.full]
                description = "complete"
                match_command = "^foo\\s+bar"
                strip_ansi = false
                filter_stderr = true
                max_lines = 50
                tail_lines = 10
                truncate_lines_at = 120
                on_empty = "foo bar: ok"
                """.trimIndent(),
                "full.toml",
            )
        assertEquals("^foo\\s+bar", spec.matchCommand?.pattern)
        assertFalse(spec.stripAnsi)
        assertTrue(spec.filterStderr)
        assertEquals(50, spec.maxLines)
        assertEquals(10, spec.tailLines)
        assertEquals(120, spec.truncateLinesAt)
        assertEquals("foo bar: ok", spec.onEmpty)
    }

    @Test fun `parses strip_lines_matching list of regex`() {
        val (spec, _) =
            parser.parseOrFail(
                """
                [filters.lst]
                description = "list"
                strip_lines_matching = [
                  "^noise:",
                  "^trash$",
                ]
                """.trimIndent(),
                "lst.toml",
            )
        assertEquals(2, spec.stripLinesMatching.size)
        assertEquals("^noise:", spec.stripLinesMatching[0].pattern)
        assertEquals("^trash$", spec.stripLinesMatching[1].pattern)
    }

    @Test fun `parses keep_lines_matching whitelist`() {
        val (spec, _) =
            parser.parseOrFail(
                """
                [filters.whitelist]
                description = "whitelist"
                keep_lines_matching = [
                  "ERROR",
                  "WARN",
                ]
                """.trimIndent(),
                "whitelist.toml",
            )
        assertTrue(spec.isWhitelist)
        assertEquals(2, spec.keepLinesMatching.size)
    }

    @Test fun `parses match_output inline tables`() {
        val (spec, _) =
            parser.parseOrFail(
                """
                [filters.short]
                description = "short-circuit"
                match_output = [
                  { pattern = "already installed", message = "ok" },
                  { pattern = "Nothing to (install|update)", message = "up to date" },
                ]
                """.trimIndent(),
                "short.toml",
            )
        assertEquals(2, spec.matchOutput.size)
        assertEquals("ok", spec.matchOutput[0].message)
        assertEquals("already installed", spec.matchOutput[0].pattern.pattern)
        assertEquals("up to date", spec.matchOutput[1].message)
    }

    @Test fun `parses single test case with single-line input`() {
        val (_, tests) =
            parser.parseOrFail(
                """
                [filters.t]
                description = "t"
                [[tests.t]]
                name = "case 1"
                input = "hello"
                expected = "hi"
                """.trimIndent(),
                "t.toml",
            )
        assertEquals(1, tests.size)
        assertEquals("case 1", tests[0].name)
        assertEquals("hello", tests[0].input)
        assertEquals("hi", tests[0].expected)
    }

    @Test fun `parses test case with multiline input via triple single-quotes`() {
        val (_, tests) =
            parser.parseOrFail(
                """
                [filters.m]
                description = "m"
                [[tests.m]]
                name = "multiline"
                input = '''
                line 1
                line 2
                '''
                expected = "x"
                """.trimIndent(),
                "m.toml",
            )
        assertEquals(1, tests.size)
        assertEquals("\nline 1\nline 2\n", tests[0].input)
    }

    @Test fun `parses test case with multiline expected via triple double-quotes and processes escapes`() {
        val (_, tests) =
            parser.parseOrFail(
                """
            [filters.e]
            description = "e"
            [[tests.e]]
            name = "esc"
            input = "raw"
            expected = """ + "\"\"\"" + "a\\nb\\tc" + "\"\"\"" +
                    """
                    """.trimIndent(),
                "e.toml",
            )
        assertEquals("a\nb\tc", tests[0].expected)
    }

    @Test fun `strips inline comments outside strings`() {
        val (_, tests) =
            parser.parseOrFail(
                """
                [filters.c]
                description = "with comment"  # trailing
                [[tests.c]]
                name = "n"
                input = "x"  # another comment
                expected = "y"
                """.trimIndent(),
                "c.toml",
            )
        assertEquals(1, tests.size)
        assertEquals("x", tests[0].input)
    }

    @Test fun `preserves hash inside double-quoted string`() {
        val (_, tests) =
            parser.parseOrFail(
                """
                [filters.h]
                description = "hash in string"
                [[tests.h]]
                name = "n"
                input = "before # after"
                expected = "y"
                """.trimIndent(),
                "h.toml",
            )
        assertEquals("before # after", tests[0].input)
    }

    @Test fun `parses empty file as no-spec`() {
        val (spec, tests) = parser.parse("", "empty.toml")
        assertNull(spec)
        assertTrue(tests.isEmpty())
    }

    @Test fun `parses file with only tests and no filters section as no-spec`() {
        val (spec, _) =
            parser.parse(
                """
                [[tests.orphan]]
                name = "n"
                input = "x"
                expected = "y"
                """.trimIndent(),
                "orphan.toml",
            )
        assertNull(spec)
    }

    @Test fun `throws on missing description`() {
        val ex =
            assertThrows<TomlParseException> {
                parser.parseOrFail("[filters.x]\nmatch_command = \"x\"", "x.toml")
            }
        assertTrue(ex.message!!.contains("description"))
    }

    @Test fun `throws on missing test name`() {
        val ex =
            assertThrows<TomlParseException> {
                parser.parseOrFail(
                    """
                    [filters.t]
                    description = "t"
                    [[tests.t]]
                    input = "x"
                    expected = "y"
                    """.trimIndent(),
                    "t.toml",
                )
            }
        assertTrue(ex.message!!.contains("name"))
    }

    @Test fun `throws on invalid regex in strip_lines_matching`() {
        val ex =
            assertThrows<TomlParseException> {
                parser.parseOrFail(
                    """
                    [filters.bad]
                    description = "bad"
                    strip_lines_matching = ["["]
                    """.trimIndent(),
                    "bad.toml",
                )
            }
        assertTrue(ex.message!!.contains("strip_lines_matching"))
    }

    @Test fun `throws on unsupported value type`() {
        val ex =
            assertThrows<TomlParseException> {
                parser.parseOrFail(
                    """
                    [filters.f]
                    description = 1.5
                    """.trimIndent(),
                    "f.toml",
                )
            }
        assertTrue(ex.message!!.contains("unsupported"))
    }

    @Test fun `parses integers with explicit L suffix`() {
        val (spec, _) =
            parser.parseOrFail(
                """
                [filters.n]
                description = "n"
                max_lines = 50L
                """.trimIndent(),
                "n.toml",
            )
        assertEquals(50, spec.maxLines)
    }

    @Test fun `parseFilterFiles returns empty failed list when all succeed`() {
        val (bundle, failed) =
            parseFilterFiles(
                listOf(
                    "a.toml" to
                        """
                        [filters.a]
                        description = "a"
                        """.trimIndent(),
                    "b.toml" to
                        """
                        [filters.b]
                        description = "b"
                        """.trimIndent(),
                ),
            )
        assertEquals(2, bundle.specs.size)
        assertTrue(failed.isEmpty())
    }

    @Test fun `parseFilterFiles collects failed files without throwing`() {
        val (bundle, failed) =
            parseFilterFiles(
                listOf(
                    "good.toml" to
                        """
                        [filters.good]
                        description = "good"
                        """.trimIndent(),
                    "broken.toml" to
                        """
                        [filters.broken]
                        description =
                        """.trimIndent(),
                ),
            )
        assertEquals(1, bundle.specs.size)
        assertEquals(1, failed.size)
        assertEquals("broken.toml", failed[0])
    }

    @Test fun `parseFilterFiles skips files with no filters section`() {
        val (bundle, failed) =
            parseFilterFiles(
                listOf(
                    "stub.toml" to "[[tests.orphan]]\nname=\"n\"\ninput=\"x\"\nexpected=\"y\"\n",
                ),
            )
        assertEquals(0, bundle.specs.size)
        assertTrue(failed.isEmpty())
    }

    @Test fun `parses multiple test cases in one file`() {
        val (_, tests) =
            parser.parseOrFail(
                """
                [filters.many]
                description = "many"
                [[tests.many]]
                name = "a"
                input = "1"
                expected = "1"
                [[tests.many]]
                name = "b"
                input = "22"
                expected = "22"
                [[tests.many]]
                name = "c"
                input = "333"
                expected = "333"
                """.trimIndent(),
                "many.toml",
            )
        assertEquals(3, tests.size)
        assertEquals(listOf("a", "b", "c"), tests.map { it.name })
    }

    @Test fun `parses real-world rtk gradle spec correctly`() {
        val (spec, tests) =
            parser.parseOrFail(
                """
                [filters.gradle]
                description = "Compact Gradle build output — strip progress, keep tasks and errors"
                match_command = "^(gradle|gradlew|\\./)gradlew?\\b"
                strip_ansi = true
                strip_lines_matching = [
                  "^\\s*${'$'}",
                  "^> Configuring project",
                  "^> Task :.*UP-TO-DATE${'$'}",
                ]
                keep_lines_matching = [
                  "BUILD\\s",
                  "FAILED|FAILURE",
                ]
                max_lines = 50
                on_empty = "gradle: ok"

                [[tests.gradle]]
                name = "strips UP-TO-DATE tasks, keeps build result"
                input = "> Configuring project :app\n> Task :app:test\n3 tests completed, 1 failed\nBUILD FAILED in 12s"
                expected = "> Task :app:test\n3 tests completed, 1 failed\nBUILD FAILED in 12s"
                """.trimIndent(),
                "gradle.toml",
            )
        assertEquals("gradle", spec.name)
        assertNotNull(spec.matchCommand)
        assertEquals(3, spec.stripLinesMatching.size)
        assertEquals(2, spec.keepLinesMatching.size)
        assertTrue(spec.isWhitelist)
        assertEquals(50, spec.maxLines)
        assertEquals(1, tests.size)
    }
}
