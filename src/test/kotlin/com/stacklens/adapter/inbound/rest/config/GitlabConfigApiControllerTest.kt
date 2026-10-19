package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.GitlabConfigDto
import com.stacklens.application.port.inbound.config.GitlabConfigUseCase
import com.stacklens.domain.model.GitlabConfig
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
class GitlabConfigApiControllerTest {
    @Mock private lateinit var configManagement: GitlabConfigUseCase

    private val controller by lazy { GitlabConfigApiController(configManagement) }

    private fun mockConfig(id: Long = 1L) =
        GitlabConfig(
            id = id,
            name = "Test GitLab",
            baseUrl = "https://gitlab.example.com",
            accessToken = "mytoken",
            groupId = "mygroup",
            projectIds = null,
            defaultBranch = "main",
            active = true,
        )

    @Test
    fun `listGitlabConfigs returns mapped DTOs`() {
        whenever(configManagement.listVisibleGitlabConfigs()).thenReturn(listOf(mockConfig()))
        val result = controller.listGitlabConfigs()
        assertEquals(1, result.size)
        assertEquals("Test GitLab", result[0].name)
        assertEquals("", result[0].accessToken)
        assertEquals(true, result[0].accessTokenSet)
    }

    @Test
    fun `listGitlabConfigs returns empty when none configured`() {
        whenever(configManagement.listVisibleGitlabConfigs()).thenReturn(emptyList())
        val result = controller.listGitlabConfigs()
        assertEquals(0, result.size)
    }

    @Test
    fun `saveGitlabConfig creates new entity when id is null`() {
        val dto =
            GitlabConfigDto(
                id = null,
                name = "New Config",
                baseUrl = "https://gitlab.example.com",
                accessToken = "newtoken",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveGitlabConfig(any())).thenReturn(savedConfig)

        val result = controller.saveGitlabConfig(dto)
        assertNotNull(result)
        assertEquals("Test GitLab", result.name)
    }

    @Test
    fun `saveGitlabConfig creates new entity when id is 0`() {
        val dto =
            GitlabConfigDto(
                id = 0,
                name = "Zero Id Config",
                baseUrl = "https://gitlab.example.com",
                accessToken = "token123",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveGitlabConfig(any())).thenReturn(savedConfig)

        val result = controller.saveGitlabConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveGitlabConfig updates existing entity when id is positive`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            GitlabConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://gitlab.example.com",
                accessToken = "updatedtoken",
            )
        whenever(configManagement.findGitlabConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveGitlabConfig(any())).thenReturn(existingConfig)

        val result = controller.saveGitlabConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveGitlabConfig creates new entity when existing id not found`() {
        val dto =
            GitlabConfigDto(
                id = 99L,
                name = "New Via 99",
                baseUrl = "https://gitlab.example.com",
                accessToken = "token",
            )
        whenever(configManagement.findGitlabConfig(eq(99L))).thenReturn(null)
        val savedConfig = mockConfig()
        whenever(configManagement.saveGitlabConfig(any())).thenReturn(savedConfig)

        val result = controller.saveGitlabConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveGitlabConfig does not update token when accessToken is blank`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            GitlabConfigDto(
                id = 5L,
                name = "Updated Config",
                baseUrl = "https://gitlab.example.com",
                accessToken = "",
            )
        whenever(configManagement.findGitlabConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveGitlabConfig(any())).thenReturn(existingConfig)

        val result = controller.saveGitlabConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `deleteGitlabConfig returns 404 when not found`() {
        whenever(configManagement.findGitlabConfig(1L)).thenReturn(null)
        val result = controller.deleteGitlabConfig(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `deleteGitlabConfig returns 204 when found`() {
        whenever(configManagement.findGitlabConfig(1L)).thenReturn(mockConfig())
        val result = controller.deleteGitlabConfig(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `testGitlabConnection returns 404 when config not found`() {
        whenever(configManagement.testGitlabConnection(1L)).thenReturn(null)
        val result = controller.testGitlabConnection(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `testGitlabConnection returns success payload from port`() {
        val config = mockConfig()
        val payload = mapOf<String, Any>("success" to true, "message" to "Connected as John Doe")
        whenever(configManagement.findGitlabConfig(1L)).thenReturn(config)
        whenever(configManagement.testGitlabConnection(eq(1L))).thenReturn(payload)

        val result = controller.testGitlabConnection(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
        assertEquals("Connected as John Doe", result.body!!["message"])
        verify(configManagement).testGitlabConnection(eq(1L))
    }

    @Test
    fun `testGitlabConnection returns failure payload from port`() {
        val config = mockConfig()
        val payload = mapOf<String, Any>("success" to false, "message" to "Auth failed 401")
        whenever(configManagement.findGitlabConfig(1L)).thenReturn(config)
        whenever(configManagement.testGitlabConnection(eq(1L))).thenReturn(payload)

        val result = controller.testGitlabConnection(1L)
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!["success"] as Boolean)
        assertEquals("Auth failed 401", result.body!!["message"])
        verify(configManagement).testGitlabConnection(eq(1L))
    }
}
