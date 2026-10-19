package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.ContainerRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileSystemContainerRuntimeProbeAdapterEnvTest {
    @Test
    fun `an environment variable that is not set reads as an empty string`() {
        assertEquals("", FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("STACKLENS_UNSET_RUNTIME_PROBE"))
    }

    @Test
    fun `a set environment variable is reported unchanged`() {
        assertEquals(System.getenv("PATH"), FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("PATH"))
    }

    @Test
    fun `the probe works with its built in environment readers`() {
        val runtime = FileSystemContainerRuntimeProbeAdapter().detectActiveRuntime()

        assertTrue(runtime in ContainerRuntime.entries)
    }
}
