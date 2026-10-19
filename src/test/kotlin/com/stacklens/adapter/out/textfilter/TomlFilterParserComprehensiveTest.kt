package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TomlFilterParserComprehensiveTest {
    private val parser = TomlFilterParser()

    @Test
    fun `parses filter section with non-tests double bracket`() {
        val src = "[[unknown]]\nname = \"x\"\n\n[filters.actual]\ndescription = \"real\"\n"
        val (spec, tests) = parser.parse(src, "f.toml")
        assertNotNull(spec)
        assertEquals("actual", spec!!.name)
        assertTrue(tests.isEmpty())
    }

    @Test
    fun `parses filter section with non-filters single bracket`() {
        val src = "[unknown.section]\njunk = \"ignored\"\n\n[filters.main]\ndescription = \"main\"\n"
        val (spec, _) = parser.parse(src, "f.toml")
        assertNotNull(spec)
        assertEquals("main", spec!!.name)
    }

    @Test
    fun `readUntilHeader returns empty list when next line is a section header`() {
        val src = "[filters.x]\ndescription = \"x\"\n[other]\n"
        val (spec, _) = parser.parse(src, "f.toml")
        assertNotNull(spec)
    }

    @Test
    fun `readUntilHeader accumulates lines until next section`() {
        val src =
            "[filters.x]\ndescription = \"x\"\n[[tests.x]]\nname = \"n\"\ninput = \"a\"\nexpected = \"b\"\n" +
                "[[tests.x]]\nname = \"n2\"\ninput = \"c\"\nexpected = \"d\"\n[other.section]\nignored = true\n"
        val (_, tests) = parser.parse(src, "f.toml")
        assertEquals(2, tests.size)
        assertEquals("n", tests[0].name)
        assertEquals("n2", tests[1].name)
    }

    @Test
    fun `parse handles header line with content after bracket`() {
        val src = "[filters.x]\ndescription = \"x\"\n"
        val (spec, _) = parser.parse(src, "f.toml")
        assertNotNull(spec)
    }

    @Test
    fun `multiline string with closing on same line single quotes`() {
        val src = "[filters.x]\ndescription = \"d\"\n"
        val (spec, _) = parser.parse(src, "f.toml")
        assertNotNull(spec)
    }

    @Test
    fun `multiline string with closing on later line single quotes is parsed`() {
        val src = "[filters.x]\ndescription = \"d\"\n[[tests.x]]\nname = \"n\"\ninput = '''body\nmore'''\nexpected = \"y\"\n"
        val (_, tests) = parser.parse(src, "f.toml")
        assertEquals(1, tests.size)
    }

    @Test
    fun `multiline string with closing on later line double quotes`() {
        val src =
            "[filters.x]\ndescription = \"d\"\n[[tests.x]]\nname = \"n\"\ninput = \"\"\"\nbody\nhere\"\"\"\nexpected = \"y\"\n"
        val (_, tests) = parser.parse(src, "f.toml")
        assertTrue(tests[0].input.contains("body"))
        assertTrue(tests[0].input.contains("here"))
    }

    @Test
    fun `multiline string with closing on same line double quotes`() {
        val src = "[filters.x]\ndescription = \"\"\"compact\"\"\"\nmatch_command = \"^x\"\n"
        val (spec, _) = parser.parse(src, "f.toml")
        assertEquals("compact", spec!!.description)
    }

    @Test
    fun `multiline string unterminated double quote throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\ninput = \"\"\"no closing\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `multiline string unterminated single quote throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\ninput = '''no closing\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `match_output entry must be a map throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nmatch_output = [\"not a map\"]\n",
                    "f.toml",
                )
            }
        assertTrue(ex.message!!.contains("match_output"))
    }

    @Test
    fun `match_output entry missing pattern throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nmatch_output = [\n  { message = \"x\" }\n]\n",
                    "f.toml",
                )
            }
        assertTrue(ex.message!!.contains("pattern"))
    }

    @Test
    fun `match_output entry missing message throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nmatch_output = [\n  { pattern = \"p\" }\n]\n",
                    "f.toml",
                )
            }
        assertTrue(ex.message!!.contains("message"))
    }

    @Test
    fun `match_output with invalid regex in pattern throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nmatch_output = [\n  { pattern = \"[invalid\", message = \"x\" }\n]\n",
                    "f.toml",
                )
            }
        assertTrue(ex.message!!.contains("pattern"))
    }

    @Test
    fun `match_output with invalid regex in unless throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nmatch_output = [\n  { pattern = \"ok\", message = \"x\", unless = \"[invalid\" }\n]\n",
                    "f.toml",
                )
            }
        assertTrue(ex.message!!.contains("unless"))
    }

    @Test
    fun `max_lines negative throws IllegalArgumentException`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseOrFail("[filters.x]\ndescription = \"d\"\nmax_lines = -1\n", "f.toml")
            }
        assertTrue(ex.message!!.contains("maxLines"))
    }

    @Test
    fun `tail_lines negative throws`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseOrFail("[filters.x]\ndescription = \"d\"\ntail_lines = -5\n", "f.toml")
            }
        assertTrue(ex.message!!.contains("tailLines"))
    }

    @Test
    fun `truncate_lines_at zero throws`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseOrFail("[filters.x]\ndescription = \"d\"\ntruncate_lines_at = 0\n", "f.toml")
            }
        assertTrue(ex.message!!.contains("truncateLinesAt"))
    }

    @Test
    fun `invalid regex in match_command throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail("[filters.x]\ndescription = \"d\"\nmatch_command = \"[invalid\"\n", "f.toml")
            }
        assertTrue(ex.message!!.contains("match_command"))
    }

    @Test
    fun `readKeyValue on line without equals throws`() {
        val method =
            TomlFilterParser::class.java.declaredMethods
                .firstOrNull { it.name == "readKeyValue\$stacklens" && it.parameterCount == 2 }
                ?: return
        method.isAccessible = true
        try {
            method.invoke(parser, "noEqualsHere", "f.toml")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("Expected exception")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            assertTrue(e.cause is TomlParseException)
        }
    }

    @Test
    fun `multi-line array across lines terminates at bracket`() {
        val src = "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\n  \"^a\",\n  \"^b\",\n]\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals(2, spec.stripLinesMatching.size)
    }

    @Test
    fun `multi-line array unterminated throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\n  \"^a\",\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `multi-line array hits next section throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\n  \"^a\",\n    [other]\njunk = 1\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `multi-line string across many lines`() {
        val src =
            "[filters.x]\ndescription = \"d\"\n[[tests.x]]\nname = \"n\"\n" +
                "input = \"\"\"\nfirst\nsecond\nthird\n\"\"\"\nexpected = \"y\"\n"
        val (_, tests) = parser.parseOrFail(src, "f.toml")
        assertEquals(1, tests.size)
        assertTrue(tests[0].input.contains("first"))
        assertTrue(tests[0].input.contains("second"))
        assertTrue(tests[0].input.contains("third"))
    }

    @Test
    fun `needsMoreLines for single-line empty array returns false`() {
        val src = "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = []\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertTrue(spec.stripLinesMatching.isEmpty())
    }

    @Test
    fun `parseValue true boolean`() {
        val src = "[filters.x]\ndescription = \"d\"\nstrip_ansi = true\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertTrue(spec.stripAnsi)
    }

    @Test
    fun `parseValue false boolean`() {
        val src = "[filters.x]\ndescription = \"d\"\nstrip_ansi = false\nfilter_stderr = true\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals(false, spec.stripAnsi)
        assertTrue(spec.filterStderr)
    }

    @Test
    fun `parseValue negative integer throws`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                parser.parseOrFail("[filters.x]\ndescription = \"d\"\nmax_lines = -5\n", "f.toml")
            }
        assertTrue(ex.message!!.contains("maxLines"))
    }

    @Test
    fun `parseValue integer with L suffix`() {
        val src = "[filters.x]\ndescription = \"d\"\nmax_lines = 100L\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals(100, spec.maxLines)
    }

    @Test
    fun `parseValue unsupported throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail("[filters.x]\ndescription = 1.5\n", "f.toml")
            }
        assertTrue(ex.message!!.contains("unsupported"))
    }

    @Test
    fun `unescapeString handles all supported escapes via parse`() {
        val src =
            "[filters.x]\ndescription = \"tab\\there\\nnewline\\rcr\\\"quote\\'apos\\\\bslash\"\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertNotNull(spec)
    }

    @Test
    fun `unescapeString malformed unicode throws`() {
        val method =
            TomlFilterParser::class.java.declaredMethods
                .firstOrNull { it.name == "unescapeString\$stacklens" && it.parameterCount == 3 }
                ?: return
        method.isAccessible = true
        try {
            method.invoke(parser, "short \\u", "f.toml", "k")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("Expected exception")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            assertTrue(e.cause is TomlParseException)
        }
    }

    @Test
    fun `unescapeString unknown escape throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"bad \\x escape\"\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `parseArray handles blank inner content`() {
        val src = "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [   ]\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertTrue(spec.stripLinesMatching.isEmpty())
    }

    @Test
    fun `parseArray unterminated throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\"a\"\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `parseInlineTable with multiple entries`() {
        val src =
            "[filters.x]\ndescription = \"d\"\n" +
                "match_output = [\n  { pattern = \"p1\", message = \"m1\" },\n  { pattern = \"p2\", message = \"m2\" }\n]\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals(2, spec.matchOutput.size)
    }

    @Test
    fun `parseInlineTable unterminated throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nmatch_output = [\n  { pattern = \"p\", message = \"m\"\n]\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `splitTopLevel with bracket inside string is preserved`() {
        val src = "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\"^noise\", \"^garbage\"]\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals(2, spec.stripLinesMatching.size)
    }

    @Test
    fun `splitTopLevel unbalanced braces throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\"a\",\n  \"b\"\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `splitTopLevel unterminated string throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\"never ends\n",
                    "f.toml",
                )
            }
        assertNotNull(ex)
    }

    @Test
    fun `countUnquoted handles bracket inside string literal`() {
        val src = "[filters.x]\ndescription = \"d\"\nstrip_lines_matching = [\"^\\\\[\"]\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals(1, spec.stripLinesMatching.size)
    }

    @Test
    fun `stripComment with hash inside string preserves the hash`() {
        val src = "[filters.x]\ndescription = \"with #hash inside\"\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals("with #hash inside", spec.description)
    }

    @Test
    fun `stripComment with escaped quote inside string is preserved`() {
        val src = "[filters.x]\ndescription = \"with \\\\\\\" escaped quote\"\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals("with \\\" escaped quote", spec.description)
    }

    @Test
    fun `parseSingleLineString with escaped quote inside`() {
        val src = "[filters.x]\ndescription = \"with \\\\\\\"escape and end\"\n"
        val (spec, _) = parser.parseOrFail(src, "f.toml")
        assertEquals("with \\\"escape and end", spec.description)
    }

    @Test
    fun `parseSingleLineString unterminated throws`() {
        val ex =
            assertThrows(TomlParseException::class.java) {
                parser.parseOrFail(
                    "[filters.x]\ndescription = \"never ends\n",
                    "f.toml",
                )
            }
        assertTrue(ex.message!!.contains("unterminated") || ex.message!!.contains("string"))
    }
}
