package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.JiraConfigDto
import com.stacklens.application.port.inbound.config.JiraConfigUseCase
import com.stacklens.domain.model.JiraConfig
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
class JiraConfigApiControllerTest {
    @Mock private lateinit var configManagement: JiraConfigUseCase

    private val controller by lazy { JiraConfigApiController(configManagement) }

    private fun mockConfig(id: Long = 1L) =
        JiraConfig(
            id = id,
            name = "Test Jira",
            baseUrl = "https://jira.example.com",
            projectKey = "PROJ",
            username = "user@example.com",
            apiToken = "mytoken",
            cloudMode = true,
            active = true,
        )

    @Test
    fun `listJiraConfigs returns mapped DTOs`() {
        whenever(configManagement.listVisibleJiraConfigs()).thenReturn(listOf(mockConfig()))
        val result = controller.listJiraConfigs()
        assertEquals(1, result.size)
        assertEquals("Test Jira", result[0].name)
        assertEquals("", result[0].apiToken)
        assertEquals(true, result[0].apiTokenSet)
    }

    @Test
    fun `listJiraConfigs returns empty when none configured`() {
        whenever(configManagement.listVisibleJiraConfigs()).thenReturn(emptyList())
        val result = controller.listJiraConfigs()
        assertEquals(0, result.size)
    }

    @Test
    fun `saveJiraConfig creates new entity when id is null`() {
        val dto =
            JiraConfigDto(
                id = null,
                name = "New Config",
                baseUrl = "https://jira.example.com",
                projectKey = "NEW",
                username = "user",
                apiToken = "newtoken",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveJiraConfig(any())).thenReturn(savedConfig)

        val result = controller.saveJiraConfig(dto)
        assertNotNull(result)
        assertEquals("Test Jira", result.name)
    }

    @Test
    fun `saveJiraConfig creates new entity when id is 0`() {
        val dto =
            JiraConfigDto(
                id = 0,
                name = "Zero Id",
                baseUrl = "https://jira.example.com",
                apiToken = "token",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveJiraConfig(any())).thenReturn(savedConfig)

        val result = controller.saveJiraConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveJiraConfig updates existing entity when id is positive`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            JiraConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://jira.example.com",
                apiToken = "updatedtoken",
            )
        whenever(configManagement.findJiraConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveJiraConfig(any())).thenReturn(existingConfig)

        val result = controller.saveJiraConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveJiraConfig creates new entity when existing id not found`() {
        val dto =
            JiraConfigDto(
                id = 99L,
                name = "New Via 99",
                baseUrl = "https://jira.example.com",
                apiToken = "token",
            )
        whenever(configManagement.findJiraConfig(eq(99L))).thenReturn(null)
        val savedConfig = mockConfig()
        whenever(configManagement.saveJiraConfig(any())).thenReturn(savedConfig)

        val result = controller.saveJiraConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveJiraConfig does not update token when apiToken is blank`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            JiraConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://jira.example.com",
                apiToken = "",
            )
        whenever(configManagement.findJiraConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveJiraConfig(any())).thenReturn(existingConfig)

        val result = controller.saveJiraConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveJiraConfig persists trimmed commentVisibilityRole on new entity`() {
        val dto =
            JiraConfigDto(
                id = null,
                name = "Restricted Jira",
                baseUrl = "https://jira.example.com",
                apiToken = "tok",
                commentVisibilityRole = "  Developers  ",
            )
        val captor = org.mockito.kotlin.argumentCaptor<JiraConfig>()
        whenever(configManagement.saveJiraConfig(captor.capture())).thenAnswer { it.arguments[0] as JiraConfig }

        val result = controller.saveJiraConfig(dto)

        assertEquals("Developers", captor.firstValue.commentVisibilityRole)
        assertEquals("Developers", result.commentVisibilityRole)
    }

    @Test
    fun `saveJiraConfig clears commentVisibilityRole when DTO has blank role`() {
        val existing = mockConfig(id = 5L).also { it.commentVisibilityRole = "Developers" }
        val dto =
            JiraConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://jira.example.com",
                apiToken = "",
                commentVisibilityRole = "",
            )
        whenever(configManagement.findJiraConfig(eq(5L))).thenReturn(existing)
        val captor = org.mockito.kotlin.argumentCaptor<JiraConfig>()
        whenever(configManagement.saveJiraConfig(captor.capture())).thenAnswer { it.arguments[0] as JiraConfig }

        controller.saveJiraConfig(dto)

        assertEquals("", captor.firstValue.commentVisibilityRole)
    }

    @Test
    fun `deleteJiraConfig returns 404 when not found`() {
        whenever(configManagement.findJiraConfig(1L)).thenReturn(null)
        val result = controller.deleteJiraConfig(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `deleteJiraConfig returns 204 when found`() {
        whenever(configManagement.findJiraConfig(1L)).thenReturn(mockConfig())
        val result = controller.deleteJiraConfig(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `testJiraConnection returns 404 when config not found`() {
        whenever(configManagement.testJiraConnection(1L, null)).thenReturn(null)
        val result = controller.testJiraConnection(1L, null)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `testJiraConnection returns success payload from port`() {
        val config = mockConfig()
        val payload =
            mapOf<String, Any>(
                "success" to true,
                "apiVersion" to "3",
                "totalAccessibleIssues" to 5,
                "message" to "Connected",
            )
        whenever(configManagement.findJiraConfig(1L)).thenReturn(config)
        whenever(configManagement.testJiraConnection(eq(1L), isNull())).thenReturn(payload)

        val result = controller.testJiraConnection(1L, null)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
        assertEquals(5, result.body!!["totalAccessibleIssues"])
        verify(configManagement).testJiraConnection(eq(1L), isNull())
    }

    @Test
    fun `testJiraConnection passes keyword through and returns failure payload`() {
        val config = mockConfig()
        val payload = mapOf<String, Any>("success" to false, "message" to "auth failed", "status" to 401)
        whenever(configManagement.findJiraConfig(1L)).thenReturn(config)
        whenever(configManagement.testJiraConnection(eq(1L), eq("PROJ-42"))).thenReturn(payload)

        val result = controller.testJiraConnection(1L, "PROJ-42")
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!["success"] as Boolean)
        assertEquals(401, result.body!!["status"])
        verify(configManagement).testJiraConnection(eq(1L), eq("PROJ-42"))
    }
}
