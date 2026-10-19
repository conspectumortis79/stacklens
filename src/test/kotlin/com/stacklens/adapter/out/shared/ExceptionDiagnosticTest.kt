package com.stacklens.adapter.out.shared

import com.stacklens.domain.util.exceptionDiagnostic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class ExceptionDiagnosticTest {
    @Test
    fun `a message is quoted verbatim`() {
        assertEquals("connection refused", exceptionDiagnostic(IOException("connection refused")))
    }

    @Test
    fun `an exception without a message is named by its class`() {
        assertEquals("IllegalStateException", exceptionDiagnostic(IllegalStateException()))
    }
}
