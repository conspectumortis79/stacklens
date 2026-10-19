package com.stacklens.application.service

import com.stacklens.application.collaboration.DevEnvironmentToggle
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.service.config.UiPreferencesService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UiPreferencesServiceTest {
    private val port: UiPreferencesPort = mock()
    private val toggle: DevEnvironmentToggle = mock()

    private fun service() = UiPreferencesService(port, toggle)

    @Test
    fun `allPreferences returns the stored map`() {
        whenever(port.getAll()).thenReturn(mapOf("theme" to "dark"))

        assertEquals(mapOf("theme" to "dark"), service().allPreferences())
    }

    @Test
    fun `preference answers an empty string for an unknown key`() {
        whenever(port.getAll()).thenReturn(mapOf("theme" to "dark"))

        assertEquals("dark", service().preference("theme"))
        assertEquals("", service().preference("missing"))
    }

    @Test
    fun `setPreference merges the new value into the stored map`() {
        whenever(port.getAll()).thenReturn(mapOf("theme" to "dark", "language" to "en"))
        val merged = argumentCaptor<Map<String, String>>()

        service().setPreference("theme", "legacy")

        verify(port).replaceAll(merged.capture())
        assertEquals("legacy", merged.firstValue["theme"])
        assertEquals("en", merged.firstValue["language"])
    }

    @Test
    fun `the docker test environment key reconciles the development environment`() {
        whenever(port.getAll()).thenReturn(emptyMap())

        service().setPreference("dev_docker_test_env", "true")

        verify(toggle).reconcileDockerTestEnv(true)

        service().setPreference("dev_docker_test_env", "false")

        verify(toggle).reconcileDockerTestEnv(false)
    }

    @Test
    fun `other keys leave the development environment alone`() {
        whenever(port.getAll()).thenReturn(emptyMap())

        service().setPreference("theme", "dark")

        verify(toggle, never()).reconcileDockerTestEnv(any())
    }

    @Test
    fun `a missing toggle collaborator keeps the write working`() {
        whenever(port.getAll()).thenReturn(emptyMap())
        val withoutToggle = UiPreferencesService(port, mock())

        withoutToggle.setPreference("dev_docker_test_env", "true")

        verify(port).replaceAll(mapOf("dev_docker_test_env" to "true"))
    }
}
