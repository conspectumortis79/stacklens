package com.stacklens.application.port.out.health

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConnectionProbeResultTest {
    @Test
    fun `Ok default constructor yields toolCount 0`() {
        assertEquals(0, ConnectionProbeResult.Ok().toolCount)
    }

    @Test
    fun `Ok explicit toolCount is preserved`() {
        assertEquals(7, ConnectionProbeResult.Ok(toolCount = 7).toolCount)
    }

    @Test
    fun `Ok errorMessage getter always returns null`() {
        assertNull(ConnectionProbeResult.Ok().errorMessage)
        assertNull(ConnectionProbeResult.Ok(toolCount = 99).errorMessage)
    }

    @Test
    fun `Failure exposes its errorMessage and is not equal to Ok`() {
        val failure = ConnectionProbeResult.Failure("boom")
        assertEquals("boom", failure.errorMessage)
        assertNotEquals(failure as Any, ConnectionProbeResult.Ok() as Any)
    }

    @Test
    fun `Failure with a different message is a different value`() {
        assertNotEquals(
            ConnectionProbeResult.Failure("a") as Any,
            ConnectionProbeResult.Failure("b") as Any,
        )
    }
}
