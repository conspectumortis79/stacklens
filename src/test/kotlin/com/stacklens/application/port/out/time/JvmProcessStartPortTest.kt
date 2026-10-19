package com.stacklens.application.port.out.time

import com.stacklens.adapter.out.time.JvmProcessStartPort
import com.stacklens.infrastructure.config.TimeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class JvmProcessStartPortTest {
    @Test
    fun `the process start lies between the test run start and now`() {
        val startedAt = JvmProcessStartPort.startedAt()
        val now = LocalDateTime.now()

        assertTrue(startedAt.isBefore(now) || startedAt.isEqual(now))
        assertTrue(Duration.between(startedAt, now).toMinutes() < 120)
    }

    @Test
    fun `the boundary is stable across lookups`() {
        assertEquals(JvmProcessStartPort.startedAt(), JvmProcessStartPort.startedAt())
    }

    @Test
    fun `the time config exposes the jvm boundary`() {
        assertEquals(JvmProcessStartPort, TimeConfig().processStartPort())
    }
}
