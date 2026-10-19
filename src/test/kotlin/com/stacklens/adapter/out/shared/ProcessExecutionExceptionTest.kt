package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ProcessExecutionExceptionTest {
    @Test
    fun `ProcessExecutionException with message only uses null cause`() {
        val ex = ProcessExecutionException("process failed")
        assertEquals("process failed", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun `ProcessExecutionException with cause stores both`() {
        val cause = RuntimeException("root cause")
        val ex = ProcessExecutionException("process failed", cause)
        assertEquals("process failed", ex.message)
        assertSame(cause, ex.cause)
    }
}
