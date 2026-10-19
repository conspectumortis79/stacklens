package com.stacklens.adapter.out.github

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GithubTreeEntryDtoTest {
    @Test
    fun `GithubTreeEntryDto stores path and type`() {
        val dto = GithubTreeEntryDto(path = "src/Main.kt", type = "blob")
        assertEquals("src/Main.kt", dto.path)
        assertEquals("blob", dto.type)
    }

    @Test
    fun `GithubTreeEntryDto equals and hashCode`() {
        val a = GithubTreeEntryDto("src/Main.kt", "blob")
        val b = GithubTreeEntryDto("src/Main.kt", "blob")
        val c = GithubTreeEntryDto("src/Other.kt", "tree")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `GithubTreeEntryDto copy and toString`() {
        val a = GithubTreeEntryDto("src/Main.kt", "blob")
        val b = a.copy(type = "tree")
        assertEquals("tree", b.type)
        assertNotNull(a.toString())
    }
}
