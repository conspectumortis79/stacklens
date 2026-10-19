package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class TomlFilterParserHeaderBranchTest {
    private val parser = TomlFilterParser()

    private val tripleQuote = "\"\"\""

    @Test
    fun `a skipped section body is abandoned at the first array table header`() {
        val (spec, tests) =
            parser.parseOrFail(
                """
                [other.section]
                [unclosed body line
                value = 1
                [[tests.a]]
                name = "n"
                input = "x"
                expected = "y"

                [filters.a]
                description = "kept"
                """.trimIndent(),
                "headers.toml",
            )

        assertEquals("a", spec.name)
        assertEquals(1, tests.size)
        assertEquals("n", tests[0].name)
        assertEquals("x", tests[0].input)
    }

    @Test
    fun `a basic multi-line string closed on its opening line is unescaped`() {
        val (_, tests) =
            parser.parseOrFail(
                """
                [filters.a]
                description = "kept"
                [[tests.a]]
                name = ${tripleQuote}nm$tripleQuote
                input = "x"
                expected = "y"
                """.trimIndent(),
                "inline-basic.toml",
            )

        assertEquals(1, tests.size)
        assertEquals("nm", tests[0].name)
    }

    @Test
    fun `a literal multi-line string closed on its opening line keeps its escapes`() {
        val (_, tests) =
            parser.parseOrFail(
                """
                [filters.a]
                description = "kept"
                [[tests.a]]
                name = '''C:\Users'''
                input = "x"
                expected = "y"
                """.trimIndent(),
                "inline-literal.toml",
            )

        assertEquals(1, tests.size)
        assertEquals("C:\\Users", tests[0].name)
    }

    @Test
    fun `a skipped section body keeps scanning past a half open array header`() {
        val (spec, tests) =
            parser.parseOrFail(
                """
                [other.section]
                [[unclosed
                value = 1
                [filters.a]
                description = "kept"
                """.trimIndent(),
                "half-open-array-header.toml",
            )

        assertEquals("a", spec.name)
        assertTrue(tests.isEmpty())
    }

    @Test
    fun `an unterminated array stops at the next array table header`() {
        val failure =
            assertThrows(TomlParseException::class.java) {
                parser.parse(
                    """
                    [filters.a]
                    patterns = [
                    "a",
                    [[tests.a]]
                    name = "n"
                    """.trimIndent(),
                    "unterminated-array.toml",
                )
            }

        assertTrue(failure.message!!.contains("hit next section"), "unexpected: ${failure.message}")
    }

    @Test
    fun `an unterminated array stops at the next plain section header`() {
        val failure =
            assertThrows(TomlParseException::class.java) {
                parser.parse(
                    """
                    [filters.a]
                    patterns = [
                    [1,
                    2],
                    [other.section]
                    description = "kept"
                    """.trimIndent(),
                    "unterminated-array-section.toml",
                )
            }

        assertTrue(failure.message!!.contains("hit next section '[other.section]'"), "unexpected: ${failure.message}")
    }

    @Test
    fun `a regex list rejects entries without a runtime class name`() {
        val raw =
            linkedMapOf<String, Any?>(
                "description" to "kept",
                "strip_lines_matching" to listOf<Any?>(null),
            )

        val failure =
            assertThrows(TomlParseException::class.java) {
                ReflectionTestUtils.invokeMethod<Any>(parser, "buildSpec", "n", raw, "null-entry.toml")
            }

        assertTrue(failure.message!!.contains("got null"), "unexpected: ${failure.message}")
    }
}
