package com.stacklens.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntityConcurrentlyModifiedExceptionTest {
    @Test
    fun `message is exposed via inherited message accessor`() {
        val ex = EntityConcurrentlyModifiedException("optimistic lock failure on row 42")
        assertEquals("optimistic lock failure on row 42", ex.message)
    }

    @Test
    fun `instance is a RuntimeException`() {
        val ex = EntityConcurrentlyModifiedException("oops")
        assertEquals("oops", ex.message)
        val caught: RuntimeException = ex
        assertEquals("oops", caught.message)
    }
}
