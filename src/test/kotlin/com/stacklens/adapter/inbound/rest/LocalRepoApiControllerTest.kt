package com.stacklens.adapter.inbound.rest
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.repo.GitlabCloneResult
import com.stacklens.application.port.inbound.repo.GitlabConfigSummary
import com.stacklens.application.port.inbound.repo.GitlabProjectSummary
import com.stacklens.application.port.inbound.repo.LocalRepoDirectoryBrowseUseCase
import com.stacklens.application.port.inbound.repo.LocalRepoUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.repo.GitlabConnectivityErrorMapper
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

@ExtendWith(MockitoExtension::class)
class LocalRepoApiControllerTest {
    @Mock private lateinit var localRepoUseCase: LocalRepoUseCase

    private val directoryBrowse: LocalRepoDirectoryBrowseUseCase = mock()

    private val i18n: I18nUseCase = mock(defaultAnswer = org.mockito.stubbing.Answer<Any> { "" })

    private val i18nPort: I18nPort =
        object : I18nPort {
            override fun t(
                key: String,
                vararg args: Any,
            ): String = i18n.t(key, *args)

            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ): String = i18n.tFor(lang, key, *args)

            override fun translateError(e: Exception): String = i18n.translateError(e)

            override fun resolveLanguage(): String = i18n.resolveLanguage()
        }

    private val connectivityErrorMapper by lazy { GitlabConnectivityErrorMapper(i18nPort) }

    private val controller by lazy { LocalRepoApiController(localRepoUseCase, i18n, connectivityErrorMapper, directoryBrowse) }

    @TempDir
    lateinit var tempDir: File

    private fun mockRepoConfig(id: Long = 1L) =
        LocalRepoConfig(
            id = id,
            name = "My Repo",
            path = tempDir.absolutePath,
            active = true,
            scope = "CODE_ANALYSIS",
        )

    @Test
    fun `list returns all repo configs`() {
        whenever(localRepoUseCase.listLocalRepoConfigs()).thenReturn(listOf(mockRepoConfig()))
        val result = controller.list()
        assertEquals(1, result.size)
        assertEquals("My Repo", result[0]["name"])
    }

    @Test
    fun `list returns empty list when none configured`() {
        whenever(localRepoUseCase.listLocalRepoConfigs()).thenReturn(emptyList())
        val result = controller.list()
        assertEquals(0, result.size)
    }

    @Test
    fun `save returns 400 when path does not exist`() {
        whenever(i18n.t(any<String>(), any())).thenReturn("Path is not a directory")
        val dto =
            LocalRepoApiController.LocalRepoDto(
                name = "My Repo",
                path = "/nonexistent/path/12345",
            )
        val result = controller.save(dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `save returns 200 with valid directory path`() {
        val savedConfig = mockRepoConfig()
        whenever(localRepoUseCase.upsertLocalRepoConfig(anyOrNull(), any(), any(), any(), any()))
            .thenReturn(savedConfig)

        val dto =
            LocalRepoApiController.LocalRepoDto(
                name = "My Repo",
                path = tempDir.absolutePath,
            )
        val result = controller.save(dto)
        assertEquals(200, result.statusCode.value())
        assertEquals("My Repo", result.body!!["name"])
    }

    @Test
    fun `update returns 400 when path does not exist`() {
        whenever(i18n.t(any<String>(), any())).thenReturn("Path is not a directory")
        val dto =
            LocalRepoApiController.LocalRepoDto(
                name = "My Repo",
                path = "/nonexistent/path/12345",
            )
        val result = controller.update(1L, dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `update returns 200 with valid directory path`() {
        val savedConfig = mockRepoConfig()
        whenever(localRepoUseCase.upsertLocalRepoConfig(any(), any(), any(), any(), any()))
            .thenReturn(savedConfig)

        val dto =
            LocalRepoApiController.LocalRepoDto(
                name = "Updated Repo",
                path = tempDir.absolutePath,
            )
        val result = controller.update(1L, dto)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `delete returns 404 when config not found`() {
        whenever(localRepoUseCase.deleteLocalRepoConfig(eq(1L))).thenReturn(false)
        val result = controller.delete(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `delete returns 204 when config deleted`() {
        whenever(localRepoUseCase.deleteLocalRepoConfig(eq(1L))).thenReturn(true)
        val result = controller.delete(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `listDirectory returns home dir contents when path is null`() {
        val result = controller.listDirectory(null)
        assertEquals(200, result.statusCode.value())
        assertNotNull(result.body)
        assertTrue(result.body!!.containsKey("current"))
        assertTrue(result.body!!.containsKey("directories"))
    }

    @Test
    fun `listDirectory returns 400 when path does not exist`() {
        whenever(i18n.t(any<String>(), any())).thenReturn("Path is not a directory")
        val result = controller.listDirectory("/nonexistent/path/xyz12345")
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `listDirectory returns directory listing for valid path`() {
        File(tempDir, "subdir1").mkdir()
        File(tempDir, "subdir2").mkdir()

        val result = controller.listDirectory(tempDir.absolutePath)
        assertEquals(200, result.statusCode.value())
        val body = result.body!!
        assertEquals(tempDir.canonicalPath, body["current"])
        val dirs = body["directories"] as List<*>
        assertTrue(dirs.size >= 2)
    }

    @Test
    fun `listGitlabConfigs returns mapped configs`() {
        val configs = listOf(GitlabConfigSummary(id = 1L, name = "My GitLab", baseUrl = "https://gitlab.example.com"))
        whenever(localRepoUseCase.listVisibleActiveGitlabConfigs()).thenReturn(configs)
        val result = controller.listGitlabConfigs()
        assertEquals(1, result.size)
        assertEquals(1L, result[0]["id"])
        assertEquals("My GitLab", result[0]["name"])
    }

    @Test
    fun `listGitlabProjects returns 404 when config not found`() {
        whenever(localRepoUseCase.listGitlabProjects(eq(1L))).thenReturn(null)
        val result = controller.listGitlabProjects(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `listGitlabProjects returns project list on success`() {
        val projects =
            listOf(
                GitlabProjectSummary(
                    id = 1L,
                    name = "Project A",
                    pathWithNamespace = "group/project-a",
                    httpUrlToRepo = "https://gitlab.example.com/group/project-a.git",
                ),
            )
        whenever(localRepoUseCase.listGitlabProjects(eq(1L))).thenReturn(projects)
        val result = controller.listGitlabProjects(1L)
        assertEquals(200, result.statusCode.value())
        val body = result.body as List<*>
        assertEquals(1, body.size)
    }

    @Test
    fun `listGitlabProjects returns 500 on exception`() {
        whenever(localRepoUseCase.listGitlabProjects(eq(1L))).thenThrow(RuntimeException("API error"))
        val result = controller.listGitlabProjects(1L)
        assertEquals(500, result.statusCode.value())
    }

    @Test
    fun `cloneGitlabProject returns 200 on success`() {
        val cloneResult =
            GitlabCloneResult.Success(
                repoId = 1L,
                repoName = "project-a",
                repoPath = tempDir.absolutePath,
                active = true,
                scope = "CODE_ANALYSIS",
            )
        whenever(localRepoUseCase.cloneGitlabProject(any(), any(), any(), any())).thenReturn(cloneResult)

        val dto =
            LocalRepoApiController.CloneGitlabRepoDto(
                httpUrl = "https://gitlab.example.com/group/project-a.git",
                projectName = "project-a",
                targetDir = tempDir.absolutePath,
            )
        val result = controller.cloneGitlabProject(1L, dto)
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["success"])
    }

    @Test
    fun `cloneGitlabProject returns 400 on BadRequest`() {
        whenever(localRepoUseCase.cloneGitlabProject(any(), any(), any(), any()))
            .thenReturn(GitlabCloneResult.BadRequest(error = "Invalid URL"))

        val dto = LocalRepoApiController.CloneGitlabRepoDto()
        val result = controller.cloneGitlabProject(1L, dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `cloneGitlabProject returns 200 with success false on ExecutionError`() {
        whenever(localRepoUseCase.cloneGitlabProject(any(), any(), any(), any()))
            .thenReturn(GitlabCloneResult.ExecutionError(error = "Clone failed"))

        val dto = LocalRepoApiController.CloneGitlabRepoDto()
        val result = controller.cloneGitlabProject(1L, dto)
        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["success"])
    }

    @Test
    fun `cloneGitlabProject returns 404 when config not found`() {
        whenever(localRepoUseCase.cloneGitlabProject(any(), any(), any(), any()))
            .thenReturn(GitlabCloneResult.ConfigNotFound)

        val dto = LocalRepoApiController.CloneGitlabRepoDto()
        val result = controller.cloneGitlabProject(1L, dto)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `save returns 400 when path is a file not directory`() {
        val file = File(tempDir, "somefile.txt").also { it.writeText("content") }
        whenever(i18n.t(any<String>(), any())).thenReturn("Path is not a directory")
        val dto = LocalRepoApiController.LocalRepoDto(name = "Repo", path = file.absolutePath)
        val result = controller.save(dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `update returns 400 when path is a file not directory`() {
        val file = File(tempDir, "somefile.txt").also { it.writeText("content") }
        whenever(i18n.t(any<String>(), any())).thenReturn("Path is not a directory")
        val dto = LocalRepoApiController.LocalRepoDto(name = "Repo", path = file.absolutePath)
        val result = controller.update(1L, dto)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `listDirectory returns home dir when path is blank`() {
        val result = controller.listDirectory("   ")
        assertEquals(200, result.statusCode.value())
        assertNotNull(result.body!!["current"])
    }

    @Test
    fun `listDirectory returns 400 when path is a file not directory`() {
        val file = File(tempDir, "somefile.txt").also { it.writeText("content") }
        whenever(i18n.t(any<String>(), any())).thenReturn("Path is not a directory")
        val result = controller.listDirectory(file.absolutePath)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `listDirectory filters hidden subdirectories`() {
        File(tempDir, ".hidden").mkdir()
        File(tempDir, "visible").mkdir()
        val result = controller.listDirectory(tempDir.absolutePath)
        assertEquals(200, result.statusCode.value())
        val dirs = result.body!!["directories"] as List<*>
        assertEquals(1, dirs.size)
    }

    @Test
    fun `listGitlabProjects returns 500 with i18n fallback when exception has null message`() {
        whenever(localRepoUseCase.listGitlabProjects(eq(1L))).thenThrow(RuntimeException())
        whenever(i18n.t(any<String>())).thenReturn("MOCK_FALLBACK")
        val result = controller.listGitlabProjects(1L)
        assertEquals(500, result.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = result.body as Map<String, Any>
        assertEquals("MOCK_FALLBACK", body["error"])
    }

    @Test
    fun `listGitlabProjects returns friendly DNS error when UnknownHostException in cause chain`() {
        val dnsError = java.net.UnknownHostException("gitlab.example.com: Name or service not known")
        val wrapped = RuntimeException("GitLab API error (page 1, group=null): Failed to resolve", dnsError)
        whenever(localRepoUseCase.listGitlabProjects(eq(1L))).thenThrow(wrapped)
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_host_unresolved_with_host"), any())).thenReturn("MOCK_DNS_HOST")
        val result = controller.listGitlabProjects(1L)
        assertEquals(500, result.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = result.body as Map<String, Any>
        assertEquals("MOCK_DNS_HOST", body["error"])
    }

    @Test
    fun `listGitlabProjects returns friendly connection refused error`() {
        val connError = RuntimeException("Connection refused: gitlab.example.com/127.0.0.1:443")
        whenever(localRepoUseCase.listGitlabProjects(eq(1L))).thenThrow(connError)
        whenever(i18n.tFor(eq("de"), eq("config.be_gitlab_unreachable"))).thenReturn("MOCK_UNREACHABLE")
        val result = controller.listGitlabProjects(1L)
        assertEquals(500, result.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = result.body as Map<String, Any>
        assertEquals("MOCK_UNREACHABLE", body["error"])
    }

    @Test
    fun `browseDirectory returns the selected path`() {
        whenever(directoryBrowse.pickDirectory()).thenReturn("/selected/dir")
        val result = controller.browseDirectory()
        assertEquals(200, result.statusCode.value())
        assertEquals("/selected/dir", result.body!!["path"])
    }

    @Test
    fun `browseDirectory returns an empty path when nothing is selected`() {
        whenever(directoryBrowse.pickDirectory()).thenReturn(null)
        val result = controller.browseDirectory()
        assertEquals(200, result.statusCode.value())
        assertEquals("", result.body!!["path"])
    }

    @Test
    fun `browseDirectory returns an empty path when the picker fails`() {
        whenever(directoryBrowse.pickDirectory()).thenThrow(RuntimeException("no javafx"))
        val result = controller.browseDirectory()
        assertEquals(200, result.statusCode.value())
        assertEquals("", result.body!!["path"])
    }

    @Test
    fun `LocalRepoDto defaults and equality`() {
        val a = LocalRepoApiController.LocalRepoDto(name = "N", path = "/p")
        assertEquals("CODE_ANALYSIS", a.scope)
        assertTrue(a.active)
        assertNull(a.id)
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `CloneGitlabRepoDto defaults and equality`() {
        val a = LocalRepoApiController.CloneGitlabRepoDto()
        assertEquals("", a.httpUrl)
        assertEquals("", a.projectName)
        assertEquals("", a.targetDir)
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `listDirectory returns 400 when path is null string and home dir invalid`() {
        val result = controller.listDirectory(null)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `listDirectory current dir has null parent returns null parent`() {
        val root = File(System.getProperty("user.home"))
        val result = controller.listDirectory(root.absolutePath)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `listDirectory with null listFilesSafe returns empty children`() {
        val ctrl =
            object : LocalRepoApiController(localRepoUseCase, i18n, connectivityErrorMapper, directoryBrowse) {
                override fun listFilesSafe(dir: File): Array<File>? = null
            }
        val result = ctrl.listDirectory(tempDir.absolutePath)
        assertEquals(200, result.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val children = result.body!!["directories"] as List<*>
        assertTrue(children.isEmpty())
    }

    @Test
    fun `listDirectory with empty listFilesSafe returns empty children`() {
        val ctrl =
            object : LocalRepoApiController(localRepoUseCase, i18n, connectivityErrorMapper, directoryBrowse) {
                override fun listFilesSafe(dir: File): Array<File>? = emptyArray()
            }
        val result = ctrl.listDirectory(tempDir.absolutePath)
        assertEquals(200, result.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val children = result.body!!["directories"] as List<*>
        assertTrue(children.isEmpty())
    }

    @Test
    fun `listDirectory root path returns null parent`() {
        val result = controller.listDirectory("/")
        assertEquals(200, result.statusCode.value())
        assertNull(result.body!!["parent"])
    }

    @Test
    fun `resolveHostHome returns user home when env is null`() {
        val result = controller.resolveHostHome(null)
        assertEquals(System.getProperty("user.home", ""), result)
    }

    @Test
    fun `resolveHostHome returns user home when env is blank`() {
        val result = controller.resolveHostHome("")
        assertEquals(System.getProperty("user.home", ""), result)
    }

    @Test
    fun `resolveHostHome returns user home when env is whitespace`() {
        val result = controller.resolveHostHome("   ")
        assertEquals(System.getProperty("user.home", ""), result)
    }

    @Test
    fun `resolveHostHome returns env value when non-blank`() {
        val result = controller.resolveHostHome("/custom/home")
        assertEquals("/custom/home", result)
    }

    @Test
    fun `resolveHostHome uses default env read when no argument given`() {
        val result = controller.resolveHostHome()
        assertNotNull(result)
    }
}
