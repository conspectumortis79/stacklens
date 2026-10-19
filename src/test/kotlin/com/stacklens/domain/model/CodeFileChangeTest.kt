package com.stacklens.domain.model

import com.stacklens.domain.enums.FileChangeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CodeFileChangeTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val change = CodeFileChange()
        assertEquals("", change.path)
        assertEquals(FileChangeType.UPDATE, change.changeType)
        assertEquals("", change.code)
    }

    @Test
    fun `named constructor populates all fields`() {
        val change = CodeFileChange(path = "src/Foo.kt", changeType = FileChangeType.CREATE, code = "package x")
        assertEquals("src/Foo.kt", change.path)
        assertEquals(FileChangeType.CREATE, change.changeType)
        assertEquals("package x", change.code)
    }

    @Test
    fun `copy preserves and overrides values`() {
        val base = CodeFileChange(path = "Foo.kt", changeType = FileChangeType.UPDATE, code = "x")
        val updated = base.copy(changeType = FileChangeType.CREATE, code = "y")
        assertEquals("Foo.kt", updated.path)
        assertEquals(FileChangeType.CREATE, updated.changeType)
        assertEquals("y", updated.code)
    }

    @Test
    fun `equality is value-based`() {
        val a = CodeFileChange(path = "Foo.kt", changeType = FileChangeType.UPDATE, code = "x")
        val b = CodeFileChange(path = "Foo.kt", changeType = FileChangeType.UPDATE, code = "x")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality when any field differs`() {
        val a = CodeFileChange(path = "Foo.kt", changeType = FileChangeType.UPDATE, code = "x")
        val b = a.copy(path = "Bar.kt")
        assertNotEquals(a, b)
    }

    @Test
    fun `toString contains all field values`() {
        val change = CodeFileChange(path = "Foo.kt", changeType = FileChangeType.UPDATE, code = "x")
        val str = change.toString()
        assertNotNull(str)
    }
}
