package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlincRunResultTest {
    @Test
    fun `component1 returns exitCode`() {
        val r = KotlincRunResult(exitCode = 0, finished = true, output = "ok")
        assertEquals(0, r.component1())
    }

    @Test
    fun `component2 returns finished`() {
        val r = KotlincRunResult(exitCode = 1, finished = false, output = "")
        assertEquals(false, r.component2())
    }

    @Test
    fun `component3 returns output`() {
        val r = KotlincRunResult(exitCode = 2, finished = true, output = "stderr")
        assertEquals("stderr", r.component3())
    }

    @Test
    fun `data class copy with all fields`() {
        val r = KotlincRunResult(exitCode = 0, finished = true, output = "x")
        val c = r.copy(exitCode = 5)
        assertEquals(5, c.exitCode)
        assertEquals(true, c.finished)
        assertEquals("x", c.output)
    }
}
