package com.stacklens.application.port.out

import com.stacklens.domain.EntityConcurrentlyDeletedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntityConcurrentlyDeletedExceptionTest {
    @Test
    fun `exception has correct message`() {
        val exception = EntityConcurrentlyDeletedException("Entity was deleted by another process")
        assertEquals("Entity was deleted by another process", exception.message)
    }

    @Test
    fun `exception extends RuntimeException`() {
        val exception = EntityConcurrentlyDeletedException("test")
        assertEquals("java.lang.RuntimeException", exception::class.java.genericSuperclass?.typeName)
    }

    @Test
    fun `exception can be thrown and caught`() {
        try {
            throw EntityConcurrentlyDeletedException("concurrent deletion")
        } catch (e: EntityConcurrentlyDeletedException) {
            assertEquals("concurrent deletion", e.message)
        }
    }

    @Test
    fun `exception can be thrown as RuntimeException`() {
        try {
            throw EntityConcurrentlyDeletedException("concurrent deletion")
        } catch (e: RuntimeException) {
            assertTrue(e is EntityConcurrentlyDeletedException)
            assertEquals("concurrent deletion", e.message)
        }
    }
}
