package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class TomlFilterParserMalformedTest {
    private val parser = TomlFilterParser()

    private fun toml(vararg lines: String): String = lines.joinToString("\n")

    private fun parseFailure(content: String): TomlParseException = assertThrows(TomlParseException::class.java) { parser.parseOrFail(content, "broken.toml") }

    @Test fun `headers that are not closed are ignored`() {
        val parsed =
            parser.parse(
                """
                [[tests.broken
                name = "n"
                [filters.broken
                description = "kept"
                """.trimIndent(),
                "junk.toml",
            )

        assertEquals(null, parsed.spec)
        assertTrue(parsed.tests.isEmpty())
    }

    @Test fun `unknown table headers skip their body`() {
        val (spec, tests) =
            parser.parseOrFail(
                """
                [[output.formats]]
                name = "ignored"
                value = 3

                [other.section]
                description = "not a filter"

                [filters.keep]
                description = "kept"
                """.trimIndent(),
                "sections.toml",
            )

        assertEquals("keep", spec.name)
        assertTrue(tests.isEmpty())
    }

    @Test fun `test blocks must declare name input and expected`() {
        assertTrue(
            parseFailure(
                """
                [filters.a]
                description = "d"
                [[tests.a]]
                input = "x"
                expected = "y"
                """.trimIndent(),
            ).message!!.contains("missing 'name'"),
        )
        assertTrue(
            parseFailure(
                """
                [filters.a]
                description = "d"
                [[tests.a]]
                name = "n"
                expected = "y"
                """.trimIndent(),
            ).message!!.contains("missing 'input'"),
        )
        assertTrue(
            parseFailure(
                """
                [filters.a]
                description = "d"
                [[tests.a]]
                name = "n"
                input = "x"
                """.trimIndent(),
            ).message!!.contains("missing 'expected'"),
        )
    }

    @Test fun `a test block at the end of the file is still parsed`() {
        val (spec, tests) =
            parser.parseOrFail(
                """
                [filters.a]
                description = "d"
                [[tests.a]]
                name = "last"
                input = "x"
                expected = "y"
                """.trimIndent(),
                "eof.toml",
            )

        assertEquals("a", spec.name)
        assertEquals("last", tests.single().name)
    }

    @Test fun `body lines without an equals sign are rejected`() {
        val failure =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    """
                    [filters.a]
                    description = "d"
                    [[tests.a]]
                    name = "n"
                    this line has no assignment
                    input = "x"
                    expected = "y"
                    """.trimIndent(),
                    "noequals.toml",
                )
            }

        assertTrue(failure.message!!.contains("malformed line"))
    }

    @Test fun `literal multi-line values keep escape sequences`() {
        val (spec, _) =
            parser.parseOrFail(
                toml(
                    "[filters.a]",
                    "description = \'\'\'raw \\t stays\'\'\'",
                    "on_empty = ${TQ}unescaped \\n body$TQ",
                ),
                "literal.toml",
            )

        assertEquals("raw \\t stays", spec.description)
        assertEquals("unescaped \n body", spec.onEmpty)
    }

    @Test fun `multi-line strings that never close are reported`() {
        val failure =
            parseFailure(
                toml(
                    "[filters.a]",
                    """description = "d"""",
                    "[[tests.a]]",
                    """name = "n"""",
                    "input = $TQ",
                    "never closed",
                    """expected = "y"""",
                ),
            )

        assertTrue(failure.message!!.contains("unterminated multi-line string"))
    }

    @Test fun `arrays that run into the next section are reported`() {
        val sectionFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                strip_lines_matching = [
                  "one",
                [filters.b]
                description = "other"
                """.trimIndent(),
            )
        val doubleBracketFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                strip_lines_matching = [
                  "one",
                [[tests.a]]
                name = "n"
                """.trimIndent(),
            )
        val eofFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                strip_lines_matching = ["one",
                """.trimIndent(),
            )

        assertTrue(sectionFailure.message!!.contains("hit next section"))
        assertTrue(doubleBracketFailure.message!!.contains("hit next section"))
        assertTrue(eofFailure.message!!.contains("unterminated array"))
    }

    @Test fun `values that need more lines but are no container fall back to the value parser`() {
        val failure =
            assertThrows(Exception::class.java) {
                parser.parseOrFail(
                    """
                    [filters.a]
                    description = "unclosed quoted text that spans
                    """.trimIndent(),
                    "odd.toml",
                )
            }

        assertNotNull(failure)
    }

    @Test fun `regex lists reject non string entries`() {
        val failure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                strip_lines_matching = ["fine", 42]
                """.trimIndent(),
            )

        assertTrue(failure.message!!.contains("must be a string"))
    }

    @Test fun `buildSpec accepts integer values that arrive as int`() {
        val raw =
            linkedMapOf<String, Any?>(
                "description" to "d",
                "max_lines" to 12,
                "tail_lines" to 3L,
                "truncate_lines_at" to "not a number",
            )

        val spec =
            ReflectionTestUtils.invokeMethod<Any>(
                parser,
                "buildSpec",
                "numeric",
                raw,
                "numeric.toml",
            )!!

        assertEquals(12, ReflectionTestUtils.getField(spec, "maxLines"))
        assertEquals(3, ReflectionTestUtils.getField(spec, "tailLines"))
        assertEquals(null, ReflectionTestUtils.getField(spec, "truncateLinesAt"))
    }

    @Test fun `invalid regular expressions are reported with their key`() {
        val commandFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                match_command = "(("
                """.trimIndent(),
            )
        val listFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                keep_lines_matching = ["))"]
                """.trimIndent(),
            )
        val ruleFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                match_output = [{ pattern = "((", message = "m" }]
                """.trimIndent(),
            )
        val unlessFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                match_output = [{ pattern = "ok", message = "m", unless = "((" }]
                """.trimIndent(),
            )
        val notATableFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                match_output = ["plain string"]
                """.trimIndent(),
            )
        val missingPatternFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                match_output = [{ message = "m" }]
                """.trimIndent(),
            )
        val missingMessageFailure =
            parseFailure(
                """
                [filters.a]
                description = "d"
                match_output = [{ pattern = "p" }]
                """.trimIndent(),
            )

        assertTrue(commandFailure.message!!.contains("invalid regex in 'match_command'"))
        assertTrue(listFailure.message!!.contains("invalid regex in 'keep_lines_matching[0]'"))
        assertTrue(ruleFailure.message!!.contains("match_output[0].pattern"))
        assertTrue(unlessFailure.message!!.contains("match_output[0].unless"))
        assertTrue(notATableFailure.message!!.contains("must be an inline table"))
        assertTrue(missingPatternFailure.message!!.contains("pattern' missing"))
        assertTrue(missingMessageFailure.message!!.contains("message' missing"))
    }

    @Test fun `a description must be a string`() {
        assertTrue(parseFailure("[filters.a]\ndescription = 7\n").message!!.contains("'description' missing"))
    }

    private companion object {
        const val TQ = "\"\"\""
    }
}
