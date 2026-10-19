package com.stacklens.adapter.out.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileSystemContainerRuntimeProbeAdapterCompanionTest {
    @Test
    fun `readEnvVarOrEmpty returns value for existing env var`() {
        val path = FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("PATH")
        assertEquals(System.getenv("PATH"), path)
    }

    @Test
    fun `readEnvVarOrEmpty returns empty string for missing env var`() {
        val result = FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("THIS_VAR_SHOULD_NOT_EXIST_XYZ123")
        assertEquals("", result)
    }

    @Test
    fun `readEnvVarOrEmpty returns empty string for null env var`() {
        val result = FileSystemContainerRuntimeProbeAdapter.readEnvVarOrEmpty("UNLIKELY_ENV_VAR_NAME")
        assertEquals("", result)
    }
}
