package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TomlValueParserProbeTest {
    private val parser = TomlValueParser()

    private fun parse(text: String): Any? = parser.parseValue(text, "filter.toml", "pattern")

    @Test
    fun `plain scalars are parsed by kind`() {
        assertEquals(true, parse("true"))
        assertEquals(false, parse("false"))
        assertEquals(42L, parse("42"))
        assertEquals(-7L, parse("-7"))
        assertEquals(15L, parse("15L"))
        assertEquals("text", parse("\"text\""))
        assertEquals("raw\\value", parse("'raw\\value'"))
        assertEquals(emptyList<Any>(), parse("[]"))
        assertEquals(emptyMap<String, Any?>(), parse("{}"))
    }

    @Test
    fun `unsupported values raise a parse error`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("nan") }

        assertTrue(failure.message!!.contains("unsupported value"), "unexpected: ${failure.message}")
    }

    @Test
    fun `single line strings resolve every documented escape`() {
        assertEquals("a\nb", parse("\"a\\nb\""))
        assertEquals("a\tb", parse("\"a\\tb\""))
        assertEquals("a\r", parse("\"a\\r\""))
        assertEquals("a\\b", parse("\"a\\\\b\""))
        assertEquals("a\"b", parse("\"a\\\"b\""))
        assertEquals("a'b", parse("\"a\\'b\""))
        assertEquals("a\u0008b", parse("\"a\\bb\""))
        assertEquals("a\u000Cb", parse("\"a\\fb\""))
        assertEquals("aA", parse("\"a\\u0041\""))
    }

    @Test
    fun `an unknown escape is rejected`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("\"a\\q\"") }

        assertTrue(failure.message!!.contains("unknown escape"), "unexpected: ${failure.message}")
    }

    @Test
    fun `a truncated unicode escape is rejected`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("\"a\\u00\"") }

        assertTrue(failure.message!!.contains("malformed"), "unexpected: ${failure.message}")
    }

    @Test
    fun `a trailing backslash ends the string scan`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("\"abc\\") }

        assertTrue(failure.message!!.contains("unterminated string"), "unexpected: ${failure.message}")
    }

    @Test
    fun `literal strings keep backslashes verbatim`() {
        assertEquals("C:\\path", parse("'C:\\path'"))
    }

    @Test
    fun `multi-line basic strings drop the leading newline and unescape the body`() {
        assertEquals("first\nsecond", parse("\"\"\"\nfirst\\nsecond\"\"\""))
        assertEquals("body", parse("\"\"\"body\"\"\""))
    }

    @Test
    fun `multi-line literal strings keep the body untouched`() {
        assertEquals("\nkeep\\n", parse("'''\nkeep\\n'''"))
    }

    @Test
    fun `an unterminated multi-line string is rejected`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("\"\"\"no end") }

        assertTrue(failure.message!!.contains("unterminated multi-line string"), "unexpected: ${failure.message}")
    }

    @Test
    fun `arrays keep every element kind and skip the blanks`() {
        assertEquals(
            listOf("a", 1L, true),
            parse("[\"a\", 1, true, ]"),
        )
    }

    @Test
    fun `an unterminated array is rejected`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("[\"a\", 1") }

        assertTrue(failure.message!!.contains("unterminated array"), "unexpected: ${failure.message}")
    }

    @Test
    fun `inline tables nest and resolve their values`() {
        assertEquals(
            mapOf("name" to "clip", "nested" to mapOf("deep" to 2L)),
            parse("{ name = \"clip\", nested = { deep = 2 } }"),
        )
    }

    @Test
    fun `an unterminated inline table is rejected`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("{ name = \"clip\"") }

        assertTrue(failure.message!!.contains("unterminated inline table"), "unexpected: ${failure.message}")
    }

    @Test
    fun `an inline table entry without a value is rejected`() {
        val failure = assertThrows(TomlParseException::class.java) { parse("{ broken }") }

        assertTrue(failure.message!!.contains("malformed line"), "unexpected: ${failure.message}")
    }

    @Test
    fun `unescapeForFilterParser exposes the shared unescaper`() {
        assertEquals("x\ny", parser.unescapeForFilterParser("x\\ny", "filter.toml", "pattern"))
    }
}
