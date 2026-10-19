package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class TomlValueParserCoverageTest {
    private val parser = TomlValueParser()

    @Test
    fun `inline tables drop empty entries produced by trailing separators`() {
        val parsed = parser.parseValue("{a = 1,}", "filters.toml", "filters")

        @Suppress("UNCHECKED_CAST")
        val map = parsed as Map<String, Any?>
        assertEquals(1L, map["a"])
    }

    @Test
    fun `an empty inline table has no keys`() {
        assertEquals(emptyMap<String, Any?>(), parser.parseValue("{}", "filters.toml", "filters"))
    }

    @Test
    fun `a trailing backslash is kept verbatim`() {
        assertEquals("abc\\", parser.unescapeForFilterParser("abc\\", "filters.toml", "pattern"))
    }

    @Test
    fun `known escape sequences are resolved`() {
        assertEquals("a\nb\tc\rd\u0041e\\f", parser.unescapeForFilterParser("a\\nb\\tc\\rd\\u0041e\\\\f", "filters.toml", "pattern"))
    }

    @Test
    fun `an unknown escape sequence is reported`() {
        val failure =
            assertThrows(TomlParseException::class.java) {
                parser.unescapeForFilterParser("a\\qb", "filters.toml", "pattern")
            }

        assertTrue(failure.message!!.contains("pattern"), failure.message)
    }

    @Test
    fun `parseInlineTable refuses text that does not start with a brace`() {
        val method =
            TomlValueParser::class.java.getDeclaredMethod(
                "parseInlineTable",
                String::class.java,
                String::class.java,
                String::class.java,
            )
        method.isAccessible = true

        val failure =
            assertThrows(InvocationTargetException::class.java) {
                method.invoke(parser, "a = 1", "filters.toml", "filters")
            }

        assertTrue((failure.cause as IllegalArgumentException).message!!.contains("non-table text"))
    }
}
