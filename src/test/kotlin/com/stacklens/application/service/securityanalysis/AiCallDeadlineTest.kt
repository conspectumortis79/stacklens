package com.stacklens.application.service.securityanalysis

import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiCallDeadlineTest {
    private val clock = FixedClockPort(1_000L)

    @Test
    fun `deadline stays open while the budget is not used up`() {
        val deadline = AiCallDeadline(clock, budgetSeconds = 10L)
        clock.advanceMillis(9_999L)
        assertFalse(deadline.expired())
    }

    @Test
    fun `deadline expires as soon as the budget is used up`() {
        val deadline = AiCallDeadline(clock, budgetSeconds = 10L)
        clock.advanceMillis(10_000L)
        assertTrue(deadline.expired())
    }

    @Test
    fun `request timeout keeps a grace margin above the budget`() {
        val deadline = AiCallDeadline(clock, budgetSeconds = 10L)
        assertEquals(15L, deadline.requestTimeoutSeconds)
    }

    @Test
    fun `deadline rejects a non positive budget`() {
        assertThrows(IllegalArgumentException::class.java) { AiCallDeadline(clock, budgetSeconds = 0L) }
    }
}
