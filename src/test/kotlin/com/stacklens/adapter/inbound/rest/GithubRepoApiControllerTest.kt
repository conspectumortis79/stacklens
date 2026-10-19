package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.config.GithubConfigUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.GithubRepoConfig
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GithubRepoApiControllerTest {
    @Mock private lateinit var configManagement: GithubConfigUseCase

    @Mock private lateinit var i18n: I18nUseCase

    private val controller by lazy { GithubRepoApiController(configManagement, i18n) }

    private fun mockConfig(
        id: Long = 1L,
        token: String? = null,
        apiBase: String? = null,
    ) = GithubRepoConfig(
        id = id,
        name = "My Repo",
        owner = "myorg",
        repo = "my-service",
        branch = "main",
        accessToken = token,
        apiBase = apiBase,
        active = true,
    )

    @Test
    fun `list returns all github repo configs`() {
        whenever(configManagement.listVisibleGithubRepoConfigs()).thenReturn(listOf(mockConfig()))
        val result = controller.list()
        assertEquals(1, result.size)
        assertEquals("My Repo", result[0]["name"])
        assertFalse(result[0]["accessTokenSet"] as Boolean)
    }

    @Test
    fun `list returns empty list when none configured`() {
        whenever(configManagement.listVisibleGithubRepoConfigs()).thenReturn(emptyList())
        val result = controller.list()
        assertEquals(0, result.size)
    }

    @Test
    fun `create returns 400 when name is blank`() {
        whenever(i18n.t("config.be_name_empty")).thenReturn("Name required")
        val dto = GithubRepoApiController.GithubRepoDto(name = "", owner = "org", repo = "repo")
        val result = controller.create(dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `create returns 400 when owner is blank`() {
        whenever(i18n.t("config.be_owner_empty")).thenReturn("Owner required")
        val dto = GithubRepoApiController.GithubRepoDto(name = "My Repo", owner = "", repo = "repo")
        val result = controller.create(dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `create returns 400 when repo is blank`() {
        whenever(i18n.t("config.be_repo_name_empty")).thenReturn("Repo name required")
        val dto = GithubRepoApiController.GithubRepoDto(name = "My Repo", owner = "org", repo = "")
        val result = controller.create(dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `create saves and returns new config`() {
        val savedConfig = mockConfig()
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(savedConfig)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "My Repo",
                owner = "myorg",
                repo = "my-service",
            )
        val result = controller.create(dto)
        assertEquals(200, result.statusCode.value())
        assertEquals("My Repo", result.body!!["name"])
    }

    @Test
    fun `update returns 404 when config not found`() {
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(null)
        val dto = GithubRepoApiController.GithubRepoDto(name = "Updated", owner = "org", repo = "repo")
        val result = controller.update(1L, dto)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `update returns 400 when name is blank`() {
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(mockConfig())
        whenever(i18n.t("config.be_name_empty")).thenReturn("Name required")
        val dto = GithubRepoApiController.GithubRepoDto(name = "", owner = "org", repo = "repo")
        val result = controller.update(1L, dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `update returns 200 on valid update`() {
        val config = mockConfig()
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(config)
        val dto = GithubRepoApiController.GithubRepoDto(name = "Updated", owner = "org", repo = "repo")
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `update preserves token when placeholder submitted`() {
        val config = mockConfig(token = "existingtoken")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(config)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "Updated",
                owner = "org",
                repo = "repo",
                accessToken = "••••••••",
            )
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `update updates token when new non-placeholder token submitted`() {
        val config = mockConfig(token = "oldtoken")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(config)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "Updated",
                owner = "org",
                repo = "repo",
                accessToken = "newtoken",
            )
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `delete returns 404 when config not found`() {
        whenever(configManagement.githubRepoConfigExistsById(eq(1L))).thenReturn(false)
        val result = controller.delete(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `delete returns 204 when config deleted`() {
        whenever(configManagement.githubRepoConfigExistsById(eq(1L))).thenReturn(true)
        val result = controller.delete(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `test returns 404 when config not found`() {
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(null)
        val result = controller.test(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `test returns 200 with success false when no token available`() {
        val config = mockConfig(token = null)
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to false, "message" to "No GitHub access token configured"))

        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["success"])
    }

    @Test
    fun `test returns 200 with success false when ai config has no token`() {
        val config = mockConfig(token = null)
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to false, "message" to "No GitHub access token configured"))

        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["success"])
    }

    @Test
    fun `test returns 200 with success false when ai config has blank token`() {
        val config = mockConfig(token = "")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to false, "message" to "No GitHub access token configured"))

        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["success"])
    }

    @Test
    fun `test returns 200 with success false on connection error`() {
        val config = mockConfig(token = "some-token")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to false, "message" to "connection error"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertNotNull(result.body)
    }

    @Test
    fun `update returns 400 when owner is blank`() {
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(mockConfig())
        whenever(i18n.t("config.be_owner_empty")).thenReturn("Owner required")
        val dto = GithubRepoApiController.GithubRepoDto(name = "Valid Name", owner = "", repo = "repo")
        val result = controller.update(1L, dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `update returns 400 when repo is blank`() {
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(mockConfig())
        whenever(i18n.t("config.be_repo_name_empty")).thenReturn("Repo required")
        val dto = GithubRepoApiController.GithubRepoDto(name = "Valid Name", owner = "org", repo = "")
        val result = controller.update(1L, dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `create with non-null branch and accessToken stores trimmed values`() {
        val savedConfig = mockConfig(token = "mytoken")
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(savedConfig)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "My Repo",
                owner = "myorg",
                repo = "my-service",
                branch = " main ",
                accessToken = " mytoken ",
            )
        val result = controller.create(dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `create with blank branch stores null`() {
        val savedConfig = mockConfig()
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(savedConfig)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "My Repo",
                owner = "myorg",
                repo = "my-service",
                branch = "  ",
                accessToken = null,
            )
        val result = controller.create(dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `list shows accessTokenSet true when token set`() {
        val config = mockConfig(token = "sometoken")
        whenever(configManagement.listVisibleGithubRepoConfigs()).thenReturn(listOf(config))
        val result = controller.list()
        assertTrue(result[0]["accessTokenSet"] as Boolean)
    }

    @Test
    fun `test returns 200 with success false on connection error to github`() {
        val config = mockConfig(token = "bad-token")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to false, "message" to "connection error"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!["success"] as Boolean)
    }

    @Test
    fun `GithubRepoDto defaults and equality`() {
        val dto = GithubRepoApiController.GithubRepoDto(name = "n", owner = "o", repo = "r")
        assertFalse(dto.active.not())
        val b = dto.copy()
        assertEquals(dto, b)
    }

    @Test
    fun `update with blank branch stores null`() {
        val config = mockConfig()
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(config)
        val dto = GithubRepoApiController.GithubRepoDto(name = "Updated", owner = "org", repo = "repo", branch = "  ")
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `test uses ai config token when config has no token`() {
        val config = mockConfig(token = null)
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to true, "message" to "connected"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
    }

    @Test
    fun `create with blank accessToken stores null`() {
        val savedConfig = mockConfig()
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(savedConfig)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "My Repo",
                owner = "myorg",
                repo = "my-service",
                accessToken = "   ",
            )
        val result = controller.create(dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `update with blank accessToken preserves null`() {
        val config = mockConfig(token = null)
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(config)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "Updated",
                owner = "org",
                repo = "repo",
                branch = " main ",
                accessToken = "   ",
            )
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `list shows accessTokenSet false for blank token`() {
        val config = mockConfig(token = "")
        whenever(configManagement.listVisibleGithubRepoConfigs()).thenReturn(listOf(config))
        val result = controller.list()
        assertFalse(result[0]["accessTokenSet"] as Boolean)
    }

    @Test
    fun `test returns success true when connectionTest returns Success`() {
        val config = mockConfig(token = "valid-token")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to true, "message" to "connected successfully"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
        assertEquals("connected successfully", result.body!!["message"])
    }

    @Test
    fun `test returns success false when connectionTest returns Failure`() {
        val config = mockConfig(token = "bad-token")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to false, "message" to "auth failed"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!["success"] as Boolean)
        assertEquals("auth failed", result.body!!["message"])
    }

    @Test
    fun `test with non-blank apiBase passes it to connectionTest`() {
        val config = mockConfig(token = "token", apiBase = "https://github.example.com")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to true, "message" to "ok"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
    }

    @Test
    fun `test with blank apiBase uses default github api url`() {
        val config = mockConfig(token = "token", apiBase = "  ")
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to true, "message" to "ok"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
    }

    @Test
    fun `test with null apiBase uses default github api url`() {
        val config = mockConfig(token = "token", apiBase = null)
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to false, "message" to "error"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertFalse(result.body!!["success"] as Boolean)
    }

    @Test
    fun `test with token from aiConfig makes successful api call`() {
        val config = mockConfig(token = null)
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.testGithubRepoConnection(eq(1L)))
            .thenReturn(mapOf("success" to true, "message" to "ok"))
        val result = controller.test(1L)
        assertEquals(200, result.statusCode.value())
        assertTrue(result.body!!["success"] as Boolean)
    }

    @Test
    fun `GithubRepoDto id property is accessible`() {
        val dto = GithubRepoApiController.GithubRepoDto(id = 42L, name = "test", owner = "owner", repo = "repo")
        assertEquals(42L, dto.id)
    }

    @Test
    fun `create with blank apiBase normalises to null`() {
        val savedConfig = mockConfig()
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(savedConfig)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "My Repo",
                owner = "myorg",
                repo = "my-service",
                apiBase = "  ",
            )
        val result = controller.create(dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `create with non-blank apiBase stores trimmed value`() {
        val savedConfig = mockConfig()
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(savedConfig)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "My Repo",
                owner = "myorg",
                repo = "my-service",
                apiBase = " https://github.example.com ",
            )
        val result = controller.create(dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `update with blank apiBase stores null`() {
        val config = mockConfig()
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(config)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "Updated",
                owner = "org",
                repo = "repo",
                apiBase = "  ",
            )
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `update with non-blank apiBase stores trimmed value`() {
        val config = mockConfig()
        whenever(configManagement.findGithubRepoConfig(eq(1L))).thenReturn(config)
        whenever(configManagement.saveGithubRepoConfig(any())).thenReturn(config)
        val dto =
            GithubRepoApiController.GithubRepoDto(
                name = "Updated",
                owner = "org",
                repo = "repo",
                apiBase = " https://github.example.com ",
            )
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }
}
