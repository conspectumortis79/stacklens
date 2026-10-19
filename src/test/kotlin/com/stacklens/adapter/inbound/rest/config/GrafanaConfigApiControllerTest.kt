package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.GrafanaConfigDto
import com.stacklens.application.port.inbound.config.GrafanaConfigUseCase
import com.stacklens.domain.model.GrafanaConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrafanaConfigApiControllerTest {
    @Mock private lateinit var configManagement: GrafanaConfigUseCase

    private val controller by lazy { GrafanaConfigApiController(configManagement) }

    private fun mockConfig(id: Long = 1L) =
        GrafanaConfig(
            id = id,
            name = "Test Grafana",
            baseUrl = "https://grafana.example.com",
            username = "admin",
            password = "secret",
            apiToken = "",
            orgId = 1L,
            scanPanelQueries = true,
            active = true,
        )

    private fun mockConfigWithToken(id: Long = 1L) =
        GrafanaConfig(
            id = id,
            name = "Test Grafana Token",
            baseUrl = "https://grafana.example.com",
            username = "",
            password = "",
            apiToken = "myapitoken",
            orgId = 1L,
            scanPanelQueries = true,
            active = true,
        )

    @Test
    fun `listGrafanaConfigs returns mapped DTOs`() {
        whenever(configManagement.listVisibleGrafanaConfigs()).thenReturn(listOf(mockConfig()))
        val result = controller.listGrafanaConfigs()
        assertEquals(1, result.size)
        assertEquals("Test Grafana", result[0].name)
        assertEquals("", result[0].password)
        assertEquals(true, result[0].passwordSet)
        assertEquals(false, result[0].apiTokenSet)
    }

    @Test
    fun `listGrafanaConfigs returns empty when none configured`() {
        whenever(configManagement.listVisibleGrafanaConfigs()).thenReturn(emptyList())
        val result = controller.listGrafanaConfigs()
        assertEquals(0, result.size)
    }

    @Test
    fun `saveGrafanaConfig creates new entity when id is null`() {
        val dto =
            GrafanaConfigDto(
                id = null,
                name = "New Config",
                baseUrl = "https://grafana.example.com",
                username = "admin",
                password = "newpass",
                apiToken = "",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveGrafanaConfig(any())).thenReturn(savedConfig)

        val result = controller.saveGrafanaConfig(dto)
        assertNotNull(result)
        assertEquals("Test Grafana", result.name)
    }

    @Test
    fun `saveGrafanaConfig creates new entity when id is 0`() {
        val dto =
            GrafanaConfigDto(
                id = 0,
                name = "Zero Id",
                baseUrl = "https://grafana.example.com",
                password = "pass",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveGrafanaConfig(any())).thenReturn(savedConfig)

        val result = controller.saveGrafanaConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveGrafanaConfig updates existing entity when id is positive`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            GrafanaConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://grafana.example.com",
                password = "updatedpass",
                apiToken = "updatedtoken",
            )
        whenever(configManagement.findGrafanaConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveGrafanaConfig(any())).thenReturn(existingConfig)

        val result = controller.saveGrafanaConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveGrafanaConfig creates new entity when existing id not found`() {
        val dto =
            GrafanaConfigDto(
                id = 99L,
                name = "New Via 99",
                baseUrl = "https://grafana.example.com",
                password = "pass",
            )
        whenever(configManagement.findGrafanaConfig(eq(99L))).thenReturn(null)
        val savedConfig = mockConfig()
        whenever(configManagement.saveGrafanaConfig(any())).thenReturn(savedConfig)

        val result = controller.saveGrafanaConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveGrafanaConfig does not update password when blank`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            GrafanaConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://grafana.example.com",
                password = "",
                apiToken = "",
            )
        whenever(configManagement.findGrafanaConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveGrafanaConfig(any())).thenReturn(existingConfig)

        val result = controller.saveGrafanaConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveGrafanaConfig with apiToken updates token`() {
        val existingConfig = mockConfigWithToken(id = 5L)
        val dto =
            GrafanaConfigDto(
                id = 5L,
                name = "Token Config",
                baseUrl = "https://grafana.example.com",
                password = "",
                apiToken = "newapitoken",
            )
        whenever(configManagement.findGrafanaConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveGrafanaConfig(any())).thenReturn(existingConfig)

        val result = controller.saveGrafanaConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `deleteGrafanaConfig returns 404 when not found`() {
        whenever(configManagement.findGrafanaConfig(1L)).thenReturn(null)
        val result = controller.deleteGrafanaConfig(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `deleteGrafanaConfig returns 204 when found`() {
        whenever(configManagement.findGrafanaConfig(1L)).thenReturn(mockConfig())
        val result = controller.deleteGrafanaConfig(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `testGrafanaConnection returns 404 when config not found`() {
        whenever(configManagement.testGrafanaConnection(1L)).thenReturn(null)
        val result = controller.testGrafanaConnection(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `testGrafanaConnection returns success payload from port`() {
        val config = mockConfig()
        val payload = mapOf<String, Any>("success" to true, "message" to "Connected to Main Org")
        whenever(configManagement.findGrafanaConfig(1L)).thenReturn(config)
        whenever(configManagement.testGrafanaConnection(eq(1L))).thenReturn(payload)

        val result = controller.testGrafanaConnection(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
        assertEquals("Connected to Main Org", result.body!!["message"])
        verify(configManagement).testGrafanaConnection(eq(1L))
    }

    @Test
    fun `testGrafanaConnection returns failure payload from port`() {
        val config = mockConfig()
        val payload = mapOf<String, Any>("success" to false, "message" to "Auth failed")
        whenever(configManagement.findGrafanaConfig(1L)).thenReturn(config)
        whenever(configManagement.testGrafanaConnection(eq(1L))).thenReturn(payload)

        val result = controller.testGrafanaConnection(1L)
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!["success"] as Boolean)
        assertEquals("Auth failed", result.body!!["message"])
        verify(configManagement).testGrafanaConnection(eq(1L))
    }
}
