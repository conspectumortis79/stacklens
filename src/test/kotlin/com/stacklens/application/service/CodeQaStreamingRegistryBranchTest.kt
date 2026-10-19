package com.stacklens.application.service.pipeline

import com.stacklens.application.service.codeqa.CodeQaStreamingRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class CodeQaStreamingRegistryBranchTest {
    private val registry = CodeQaStreamingRegistry()

    @Test
    fun `buffer set get clear`() {
        assertNull(registry.getBuffer(1L))
        registry.setBuffer(1L, "content")
        assertEquals("content", registry.getBuffer(1L))
        registry.clear(1L)
        assertNull(registry.getBuffer(1L))
    }

    @Test
    fun `listeners notified on change and can be unregistered`() {
        val calls = AtomicInteger(0)
        val unregister =
            registry.registerChangeListener(1L) {
                calls.incrementAndGet()
                Unit
            }
        registry.registerChangeListener(1L) {
            calls.incrementAndGet()
            Unit
        }
        registry.registerChangeListener(2L) {
            calls.incrementAndGet()
            Unit
        }
        registry.setBuffer(1L, "x")
        assertEquals(2, calls.get())
        unregister()
        registry.setBuffer(1L, "y")
        assertEquals(3, calls.get())
    }

    @Test
    fun `listener exceptions are swallowed`() {
        registry.registerChangeListener(1L) { throw RuntimeException("boom") }
        registry.setBuffer(1L, "x")
        assertEquals("x", registry.getBuffer(1L))
    }

    @Test
    fun `notify with no listeners does nothing`() {
        registry.clear(1L)
        registry.setBuffer(1L, "y")
        assertEquals("y", registry.getBuffer(1L))
    }

    @Test
    fun `unregister unknown listener is safe`() {
        registry.unregisterChangeListener(99L) { }
    }

    @Test
    fun `unregister removes only matching listener`() {
        val calls = AtomicInteger(0)
        val l1 = {
            calls.incrementAndGet()
            Unit
        }
        val l2 = {
            calls.incrementAndGet()
            Unit
        }
        val unregister = registry.registerChangeListener(5L, l1)
        registry.registerChangeListener(5L, l2)
        unregister()
        registry.setBuffer(5L, "z")
        assertEquals(1, calls.get())
    }

    @Test
    fun `multiple listeners on same id`() {
        val calls = AtomicInteger(0)
        registry.registerChangeListener(7L) {
            calls.incrementAndGet()
            Unit
        }
        registry.registerChangeListener(7L) {
            calls.incrementAndGet()
            Unit
        }
        registry.setBuffer(7L, "a")
        registry.setBuffer(7L, "b")
        assertEquals(4, calls.get())
        assertTrue(registry.getBuffer(7L) == "b")
    }
}
