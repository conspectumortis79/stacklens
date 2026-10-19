package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TomlFilterParserCoverageTest {
    private val parser = TomlFilterParser()

    private fun parseSpec(
        content: String,
        fileName: String = "x.toml",
    ) = parser.parseOrFail(content, fileName)

    @Test fun `escapes newline tab carriage-return backslash double-quote single-quote`() {
        val toml = "[filters.x]\ndescription = \"a\\nb\\tc\\rd\\\\e\\\"f'g\"\n"
        val (s, _) = parseSpec(toml)
        assertEquals("a\nb\tc\rd\\e\"f'g", s.description)
    }

    @Test fun `escapes backspace and form-feed`() {
        val toml = "[filters.x]\ndescription = \"a" + "\u005C\u0062" + "b" + "\u005C\u0066" + "c\"\n"
        val (s, _) = parseSpec(toml)
        assertEquals("a\u0008b\u000Cc", s.description)
    }

    @Test fun `decodes unicode escape uXXXX`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"caf\\u00e9\"\n")
        assertEquals("café", s.description)
    }

    @Test fun `rejects malformed unicode escape`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec("[filters.x]\ndescription = \"short \\u00\"\n")
            }
        assertTrue(ex.message!!.contains("unicode") || ex.message!!.contains("escape"))
    }

    @Test fun `rejects unknown escape sequence`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec("[filters.x]\ndescription = \"bad \\x escape\"\n")
            }
        assertTrue(ex.message!!.contains("escape"))
    }

    @Test fun `parses single-quoted literal strings (no escape processing)`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = 'literal \\n no escape'\n")
        assertEquals("literal \\n no escape", s.description)
    }

    @Test fun `parses negative integer as scalar then FilterSpec rejects it`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                parseSpec("[filters.x]\ndescription = \"x\"\nmax_lines = -1\n")
            }
        assertTrue(ex.message!!.contains("maxLines"))
    }

    @Test fun `throws on unterminated single-line string`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec("[filters.x]\ndescription = \"never ends\n")
            }
        assertTrue(ex.message!!.contains("unterminated"))
    }

    @Test fun `throws on unterminated multi-line string triple-quote-double`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec("[filters.x]\ndescription = \"\"\"\nbody that never closes\n")
            }
        assertTrue(ex.message!!.contains("unterminated") || ex.message!!.contains("multi-line"))
    }

    @Test fun `parses multiline literal string across lines (triple single)`() {
        val toml =
            "[filters.x]\ndescription = '''multi\nline\nliteral'''\n" +
                "[[tests.x]]\nname = \"n\"\ninput = \"x\"\nexpected = \"y\"\n"
        val (s, tests) = parseSpec(toml)
        assertEquals("multi\nline\nliteral", s.description)
        assertEquals(1, tests.size)
    }

    @Test fun `strips leading newline after triple-double opening per TOML spec`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"\"\"\nstarts with newline stripped\n\"\"\"\n")
        assertEquals("starts with newline stripped\n", s.description)
    }

    @Test fun `keeps leading newline after triple-single opening (literal)`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = '''\nstarts with newline kept\n'''\n")
        assertEquals("\nstarts with newline kept\n", s.description)
    }

    @Test fun `parses match_output with unless clause`() {
        val toml =
            "[filters.x]\ndescription = \"x\"\n" +
                "match_output = [\n  { pattern = \"ok\", message = \"OK\", unless = \"error\" },\n]\n"
        val (s, _) = parseSpec(toml)
        assertEquals(1, s.matchOutput.size)
        assertEquals("ok", s.matchOutput[0].pattern.pattern)
        assertEquals("OK", s.matchOutput[0].message)
        assertEquals("error", s.matchOutput[0].unless?.pattern)
    }

    @Test fun `match_output without unless clause sets unless to null`() {
        val toml =
            "[filters.x]\ndescription = \"x\"\n" +
                "match_output = [\n  { pattern = \"ok\", message = \"OK\" },\n]\n"
        val (s, _) = parseSpec(toml)
        assertEquals(null, s.matchOutput[0].unless)
    }

    @Test fun `throws on invalid regex in match_output pattern`() {
        val toml =
            "[filters.x]\ndescription = \"x\"\n" +
                "match_output = [\n  { pattern = \"[\", message = \"x\" },\n]\n"
        val ex = assertThrows<TomlParseException> { parseSpec(toml) }
        assertTrue(ex.message!!.contains("pattern"))
    }

    @Test fun `throws on invalid regex in match_output unless`() {
        val toml =
            "[filters.x]\ndescription = \"x\"\n" +
                "match_output = [\n  { pattern = \"ok\", message = \"x\", unless = \"[\" },\n]\n"
        val ex = assertThrows<TomlParseException> { parseSpec(toml) }
        assertTrue(ex.message!!.contains("unless"))
    }

    @Test fun `parses empty array`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"x\"\nstrip_lines_matching = []\n")
        assertTrue(s.stripLinesMatching.isEmpty())
    }

    @Test fun `throws on invalid regex in strip_lines_matching element`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec("[filters.x]\ndescription = \"x\"\nstrip_lines_matching = [\"valid\", \"[\"]\n")
            }
        assertTrue(ex.message!!.contains("strip_lines_matching[1]"))
    }

    @Test fun `throws on non-string in regex list`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec("[filters.x]\ndescription = \"x\"\nstrip_lines_matching = [42]\n")
            }
        assertTrue(ex.message!!.contains("strip_lines_matching"))
    }

    @Test fun `parses inline value on same line for key-value`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"single\"\nmax_lines = 50\n")
        assertEquals(50, s.maxLines)
    }

    @Test fun `multi-line array stops at next section header (TOML semantic)`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec(
                    "[filters.x]\ndescription = \"x\"\n" +
                        "strip_lines_matching = [\n  \"^a\",\n" +
                        "[[tests.x]]\nname = \"n\"\ninput = \"x\"\nexpected = \"y\"\n",
                )
            }
        assertTrue(ex.message!!.contains("unterminated") || ex.message!!.contains("next section"))
    }

    @Test fun `multi-line string with closing on same line after opening`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"\"\"compact\"\"\"\n")
        assertEquals("compact", s.description)
    }

    @Test fun `parses true and false booleans`() {
        val (s, _) =
            parseSpec(
                "[filters.x]\ndescription = \"x\"\nstrip_ansi = true\nfilter_stderr = false\n",
            )
        assertTrue(s.stripAnsi)
        assertTrue(!s.filterStderr)
    }

    @Test fun `multiline triple-double string ending on later line`() {
        val (s, _) =
            parseSpec(
                "[filters.x]\ndescription = \"\"\"line1\nline2\nline3\"\"\"\n",
            )
        assertEquals("line1\nline2\nline3", s.description)
    }

    @Test fun `unknown section header stops processing until next known section`() {
        val (s, _) =
            parseSpec(
                "[filters.x]\ndescription = \"x\"\n" +
                    "[some.unknown.section]\njunk = \"ignored\"\n" +
                    "max_lines = 99\n",
            )
        assertEquals(null, s.maxLines)
    }

    @Test fun `multiple test cases in same file are all parsed`() {
        val toml =
            "[filters.x]\ndescription = \"x\"\n" +
                "[[tests.x]]\nname = \"first\"\ninput = \"a\"\nexpected = \"b\"\n" +
                "[[tests.x]]\nname = \"second\"\ninput = \"c\"\nexpected = \"d\"\n" +
                "[[tests.x]]\nname = \"third\"\ninput = \"e\"\nexpected = \"f\"\n"
        val (_, tests) = parseSpec(toml)
        assertEquals(3, tests.size)
        assertEquals(listOf("first", "second", "third"), tests.map { it.name })
    }

    @Test fun `parseFilterFiles returns failed list with one broken file`() {
        val (bundle, failed) =
            parseFilterFiles(
                listOf(
                    "good.toml" to "[filters.good]\ndescription = \"good\"\n",
                    "broken.toml" to "[filters.broken]\ndescription = \"x\"\nstrip_lines_matching = [\"[\"]\n",
                    "another-good.toml" to "[filters.other]\ndescription = \"other\"\n",
                ),
            )
        assertEquals(2, bundle.specs.size)
        assertEquals(listOf("broken.toml"), failed)
    }

    @Test fun `parseFilterFiles silently skips stub file with no filters section`() {
        val (bundle, failed) =
            parseFilterFiles(
                listOf("stub.toml" to "[[tests.orphan]]\nname=\"n\"\ninput=\"x\"\nexpected=\"y\"\n"),
            )
        assertEquals(0, bundle.specs.size)
        assertTrue(failed.isEmpty())
    }

    @Test fun `parse returns ParsedFile with null spec when no filters section`() {
        val parsed = parser.parse("", "empty.toml")
        assertNull(parsed.spec)
        assertTrue(parsed.tests.isEmpty())
    }

    @Test fun `parseOrFail throws when no filters section in input`() {
        val ex =
            assertThrows<TomlParseException> {
                parser.parseOrFail("[[tests.orphan]]\nname=\"n\"\ninput=\"x\"\nexpected=\"y\"\n", "x.toml")
            }
        assertTrue(ex.message!!.contains("no [filters.*] section"))
    }

    @Test fun `hash inside single-quoted string is preserved`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"literal # not a comment\"\n")
        assertEquals("literal # not a comment", s.description)
    }

    @Test fun `hash inside double-quoted string is preserved`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"double # inside\"\n")
        assertEquals("double # inside", s.description)
    }

    @Test fun `escaped quote inside string is skipped by findUnquotedHash`() {
        val (s, _) = parseSpec("[filters.x]\ndescription = \"with \\\" quote\"\n")
        assertEquals("with \" quote", s.description)
    }

    @Test fun `missing test expected field throws clear error`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec(
                    "[filters.x]\ndescription = \"x\"\n[[tests.x]]\nname = \"n\"\ninput = \"x\"\n",
                )
            }
        assertTrue(ex.message!!.contains("expected"))
    }

    @Test fun `missing test input field throws clear error`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec(
                    "[filters.x]\ndescription = \"x\"\n[[tests.x]]\nname = \"n\"\nexpected = \"y\"\n",
                )
            }
        assertTrue(ex.message!!.contains("input"))
    }

    @Test fun `parses spec with every field populated`() {
        val toml =
            """
            [filters.full]
            description = "fully populated"
            match_command = "^foo"
            strip_ansi = false
            filter_stderr = true
            strip_lines_matching = ["^a", "^b"]
            keep_lines_matching = ["KEEP"]
            max_lines = 100
            tail_lines = 10
            truncate_lines_at = 200
            on_empty = "ok"
            match_output = [
              { pattern = "ok", message = "OK", unless = "warn" },
            ]
            """.trimIndent()
        val (s, _) = parseSpec(toml)
        assertNotNull(s.matchCommand)
        assertTrue(!s.stripAnsi)
        assertTrue(s.filterStderr)
        assertEquals(2, s.stripLinesMatching.size)
        assertEquals(1, s.keepLinesMatching.size)
        assertEquals(100, s.maxLines)
        assertEquals(10, s.tailLines)
        assertEquals(200, s.truncateLinesAt)
        assertEquals("ok", s.onEmpty)
        assertEquals(1, s.matchOutput.size)
        assertNotNull(s.matchOutput[0].unless)
    }

    @Test fun `multi-line string that does not close before EOF throws`() {
        val ex =
            assertThrows<TomlParseException> {
                parseSpec("[filters.x]\ndescription = \"\"\"\nno closing triple ever\n")
            }
        assertTrue(ex.message!!.contains("unterminated"))
    }
}
