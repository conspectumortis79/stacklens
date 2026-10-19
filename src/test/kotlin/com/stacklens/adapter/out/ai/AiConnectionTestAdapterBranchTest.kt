package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.platform.ContainerRuntimeProbePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiConnectionTestAdapterBranchTest {
    private val probe = mock<ContainerRuntimeProbePort>()

    @Test
    fun `AiTestHelpers resolveDockerUrl returns url unchanged when runtime is NONE`() {
        whenever(probe.detectActiveRuntime()).thenReturn(ContainerRuntime.NONE)
        val result = AiTestHelpers.resolveDockerUrl("http://localhost:1234", probe)
        assertEquals("http://localhost:1234", result)
    }

    @Test
    fun `AiTestHelpers resolveDockerUrl rewrites localhost to host docker internal when runtime is DOCKER`() {
        whenever(probe.detectActiveRuntime()).thenReturn(ContainerRuntime.DOCKER)
        val result = AiTestHelpers.resolveDockerUrl("http://localhost:1234", probe)
        assertEquals("http://host.docker.internal:1234", result)
    }

    @Test
    fun `AiTestHelpers resolveDockerUrl rewrites 127_0_0_1 to host docker internal when runtime is DOCKER`() {
        whenever(probe.detectActiveRuntime()).thenReturn(ContainerRuntime.DOCKER)
        val result = AiTestHelpers.resolveDockerUrl("http://127.0.0.1:1234", probe)
        assertEquals("http://host.docker.internal:1234", result)
    }

    @Test
    fun `AiTestHelpers maskApiKey masks short keys as three stars`() {
        assertEquals("***", AiTestHelpers.maskApiKey("short"))
    }

    @Test
    fun `AiTestHelpers maskApiKey masks long sk- keys`() {
        val result = AiTestHelpers.maskApiKey("sk-1234567890abcdef")
        assertTrue(result.startsWith("sk-"))
        assertTrue(result.endsWith("cdef"))
    }

    @Test
    fun `AiTestHelpers maskApiKey masks long sk-proj- keys`() {
        val result = AiTestHelpers.maskApiKey("sk-proj-1234567890abcdef")
        assertTrue(result.startsWith("sk-proj-"))
        assertTrue(result.endsWith("cdef"))
    }

    @Test
    fun `AiTestHelpers maskApiKey masks long unknown-prefix keys with first 4 chars`() {
        val result = AiTestHelpers.maskApiKey("abcdefghij1234567")
        assertTrue(result.startsWith("abcd"))
        assertTrue(result.endsWith("4567"))
    }

    @Test
    fun `AiTestHelpers resolveDockerUrl falls back to file probe when probe returns null`() {
        whenever(probe.detectActiveRuntime()).thenReturn(null)
        val result = AiTestHelpers.resolveDockerUrl("http://localhost:1234", probe)
        assertNotNull(result)
    }
}
