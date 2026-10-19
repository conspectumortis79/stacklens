package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TomlFilterAdapterUnexpectedFailureTest {
    @Test
    fun `a value that blows up outside the toml grammar still marks the file as failed`() {
        val content =
            """
            [filters.demo]
            name = "demo"
            description = "demo filter"
            timeout = 99999999999999999999999
            """.trimIndent()

        val (bundle, failed) = parseFilterFiles(listOf("exploding.toml" to content))

        assertEquals(listOf("exploding.toml"), failed)
        assertTrue(bundle.specs.isEmpty())
    }

    @Test
    fun `an inline table entry that trims away is dropped`() {
        val parsed = TomlValueParser().parseValue("{ a = 1, }", "inline.toml", "table")

        assertEquals(mapOf("a" to 1L), parsed)
    }
}
