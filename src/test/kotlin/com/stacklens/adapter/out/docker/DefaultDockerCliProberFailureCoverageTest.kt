package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DefaultDockerCliProberFailureCoverageTest {
    @Test
    fun `a probe failure without a message reports the exception type`() {
        val prober = DefaultDockerCliProber(resolveDockerExecutable = { throw IllegalStateException() })

        val status = prober.probe()

        assertFalse(status.available)
        assertEquals("docker CLI probe failed: IllegalStateException", status.reason)
    }

    @Test
    fun `a probe failure with a message reports that message`() {
        val prober = DefaultDockerCliProber(resolveDockerExecutable = { throw IllegalStateException("daemon down") })

        val status = prober.probe()

        assertFalse(status.available)
        assertEquals("docker CLI probe failed: daemon down", status.reason)
    }
}
