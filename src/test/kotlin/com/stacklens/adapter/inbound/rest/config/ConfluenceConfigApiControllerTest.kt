package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.ConfluenceConfigDto
import com.stacklens.application.port.inbound.config.ConfluenceConfigUseCase
import com.stacklens.domain.model.ConfluenceConfig
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
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfluenceConfigApiControllerTest {
    @Mock private lateinit var configManagement: ConfluenceConfigUseCase

    private val controller by lazy { ConfluenceConfigApiController(configManagement) }

    private fun mockConfig(id: Long = 1L) =
        ConfluenceConfig(
            id = id,
            name = "Test Confluence",
            baseUrl = "https://confluence.example.com",
            username = "user@example.com",
            apiToken = "mytoken",
            spaceKeys = "SPACE1,SPACE2",
            cloudMode = true,
            active = true,
        )

    @Test
    fun `listConfluenceConfigs returns mapped DTOs`() {
        whenever(configManagement.listVisibleConfluenceConfigs()).thenReturn(listOf(mockConfig()))
        val result = controller.listConfluenceConfigs()
        assertEquals(1, result.size)
        assertEquals("Test Confluence", result[0].name)
        assertEquals("", result[0].apiToken)
        assertEquals(true, result[0].apiTokenSet)
    }

    @Test
    fun `listConfluenceConfigs returns empty when none configured`() {
        whenever(configManagement.listVisibleConfluenceConfigs()).thenReturn(emptyList())
        val result = controller.listConfluenceConfigs()
        assertEquals(0, result.size)
    }

    @Test
    fun `saveConfluenceConfig creates new entity when id is null`() {
        val dto =
            ConfluenceConfigDto(
                id = null,
                name = "New Config",
                baseUrl = "https://confluence.example.com",
                username = "user",
                apiToken = "newtoken",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveConfluenceConfig(any())).thenReturn(savedConfig)

        val result = controller.saveConfluenceConfig(dto)
        assertNotNull(result)
        assertEquals("Test Confluence", result.name)
    }

    @Test
    fun `saveConfluenceConfig creates new entity when id is 0`() {
        val dto =
            ConfluenceConfigDto(
                id = 0,
                name = "Zero Id",
                baseUrl = "https://confluence.example.com",
                apiToken = "token",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveConfluenceConfig(any())).thenReturn(savedConfig)

        val result = controller.saveConfluenceConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveConfluenceConfig updates existing entity when id is positive`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            ConfluenceConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://confluence.example.com",
                apiToken = "updatedtoken",
            )
        whenever(configManagement.findConfluenceConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveConfluenceConfig(any())).thenReturn(existingConfig)

        val result = controller.saveConfluenceConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveConfluenceConfig creates new entity when existing id not found`() {
        val dto =
            ConfluenceConfigDto(
                id = 99L,
                name = "New Via 99",
                baseUrl = "https://confluence.example.com",
                apiToken = "token",
            )
        whenever(configManagement.findConfluenceConfig(eq(99L))).thenReturn(null)
        val savedConfig = mockConfig()
        whenever(configManagement.saveConfluenceConfig(any())).thenReturn(savedConfig)

        val result = controller.saveConfluenceConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveConfluenceConfig does not update token when apiToken is blank`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            ConfluenceConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://confluence.example.com",
                apiToken = "",
            )
        whenever(configManagement.findConfluenceConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveConfluenceConfig(any())).thenReturn(existingConfig)

        val result = controller.saveConfluenceConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `deleteConfluenceConfig returns 404 when not found`() {
        whenever(configManagement.findConfluenceConfig(1L)).thenReturn(null)
        val result = controller.deleteConfluenceConfig(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `deleteConfluenceConfig returns 204 when found`() {
        whenever(configManagement.findConfluenceConfig(1L)).thenReturn(mockConfig())
        val result = controller.deleteConfluenceConfig(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `testConfluenceConnection returns 404 when config not found`() {
        whenever(configManagement.testConfluenceConnection(1L, null)).thenReturn(null)
        val result = controller.testConfluenceConnection(1L, null)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `testConfluenceConnection returns success payload from port`() {
        val config = mockConfig()
        val payload =
            mapOf<String, Any>(
                "success" to true,
                "user" to "Alice",
                "url" to "https://confluence.example.com/wiki/rest/api/user/current",
                "message" to "Connected",
            )
        whenever(configManagement.findConfluenceConfig(1L)).thenReturn(config)
        whenever(configManagement.testConfluenceConnection(eq(1L), isNull())).thenReturn(payload)

        val result = controller.testConfluenceConnection(1L, null)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
        assertEquals("Alice", result.body!!["user"])
        verify(configManagement).testConfluenceConnection(eq(1L), isNull())
    }

    @Test
    fun `testConfluenceConnection passes keyword through and returns failure payload`() {
        val config = mockConfig()
        val payload = mapOf<String, Any>("success" to false, "message" to "auth failed", "status" to 401)
        whenever(configManagement.findConfluenceConfig(1L)).thenReturn(config)
        whenever(configManagement.testConfluenceConnection(eq(1L), eq("kw"))).thenReturn(payload)

        val result = controller.testConfluenceConnection(1L, "kw")
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!["success"] as Boolean)
        assertEquals(401, result.body!!["status"])
        verify(configManagement).testConfluenceConnection(eq(1L), eq("kw"))
    }
}
