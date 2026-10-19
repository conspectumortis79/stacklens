package com.stacklens.adapter.out.codeqa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class IntegrationTestStreamRegistryTest {
    @Test
    fun `open creates a buffer that read reflects appends on`() {
        val registry = IntegrationTestStreamRegistry()
        val buffer = registry.open(1L)
        buffer.append("a")
        assertEquals("a", registry.read(1L))
        buffer.append("b")
        assertEquals("ab", registry.read(1L))
    }

    @Test
    fun `open returns the same buffer instance for the same id`() {
        val registry = IntegrationTestStreamRegistry()
        assertSame(registry.open(2L), registry.open(2L))
    }

    @Test
    fun `read returns null for an unknown id`() {
        val registry = IntegrationTestStreamRegistry()
        assertNull(registry.read(3L))
    }

    @Test
    fun `close removes the buffer`() {
        val registry = IntegrationTestStreamRegistry()
        registry.open(4L).append("x")
        registry.close(4L)
        assertNull(registry.read(4L))
    }
}
