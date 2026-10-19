package com.stacklens.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntityConcurrentlyDeletedExceptionTest {
    @Test
    fun `message is exposed via inherited message accessor`() {
        val ex = EntityConcurrentlyDeletedException("entity was deleted concurrently")
        assertEquals("entity was deleted concurrently", ex.message)
    }

    @Test
    fun `instance is a RuntimeException`() {
        val ex = EntityConcurrentlyDeletedException("oops")
        assertEquals("oops", ex.message)
        val caught: RuntimeException = ex
        assertEquals("oops", caught.message)
    }
}
