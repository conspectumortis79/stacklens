package com.stacklens.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JiraKeyUtilsTest {
    @Test
    fun `regex matches a plain PROJ-123 key`() {
        val result = JiraKeyUtils.KEY_REGEX.find("PROJ-123")
        assertEquals("PROJ-123", result?.value)
    }

    @Test
    fun `regex matches within a longer string`() {
        val result = JiraKeyUtils.KEY_REGEX.find("see PROJ-456 for details")
        assertEquals("PROJ-456", result?.value)
    }

    @Test
    fun `regex matches keys with digits in the prefix`() {
        val result = JiraKeyUtils.KEY_REGEX.find("ABC2-9")
        assertEquals("ABC2-9", result?.value)
    }

    @Test
    fun `regex matches keys with underscores in the prefix`() {
        val result = JiraKeyUtils.KEY_REGEX.find("FOO_BAR-1")
        assertEquals("FOO_BAR-1", result?.value)
    }

    @Test
    fun `regex does not match lowercase key`() {
        val result = JiraKeyUtils.KEY_REGEX.find("proj-1")
        assertEquals(null, result?.value)
    }

    @Test
    fun `regex finds the first match in a multi-key string`() {
        val result = JiraKeyUtils.KEY_REGEX.find("PROJ-1 and OTHER-22")
        assertEquals("PROJ-1", result?.value)
    }

    @Test
    fun `regex does not match missing dash`() {
        val result = JiraKeyUtils.KEY_REGEX.find("PROJ123")
        assertEquals(null, result?.value)
    }

    @Test
    fun `regex is a Kotlin Regex`() {
        assertNotNull(JiraKeyUtils.KEY_REGEX)
    }
}
