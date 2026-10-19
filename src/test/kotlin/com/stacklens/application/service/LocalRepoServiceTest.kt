package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.repo.GitlabCloneResult
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.repo.GitClonePort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import java.io.File

class LocalRepoServiceTest {
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val gitlabPort: GitlabPort = mock()
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()
    private val codeAnalysisRepo: CodeAnalysisPort = mock()
    private val gitClonePort: GitClonePort = mock()
    private val i18n: I18nService =
        mock(defaultAnswer = Answer { if (it.method.returnType == String::class.java) "" else null })

    private val service =
        LocalRepoService(
            gitlabConfigRepo = gitlabConfigRepo,
            gitlabPort = gitlabPort,
            localRepoConfigRepo = localRepoConfigRepo,
            codeAnalysisRepo = codeAnalysisRepo,
            gitClonePort = gitClonePort,
            i18n = i18n,
            demoConfigFilter = PassthroughDemoConfigFilter.instance,
            fileSystem = JavaFileSystemAdapter(),
        )

    private fun gitlabConfig(accessToken: String = "tok") = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gitlab.example.com", accessToken = accessToken)

    @Test
    fun `listLocalRepoConfigs delegates to port`() {
        val configs = listOf(LocalRepoConfig(name = "repo", path = "/repo"))
        whenever(localRepoConfigRepo.findAllLocalRepoConfigs()).thenReturn(configs)
        assertEquals(configs, service.listLocalRepoConfigs())
    }

    @Test
    fun `upsertLocalRepoConfig creates new entity when id is null`() {
        val saved = LocalRepoConfig(id = 1L, name = "repo", path = "/repo")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(saved)

        val result = service.upsertLocalRepoConfig(null, "repo", "/repo", true, "REPO_ANALYSIS")

        assertEquals(saved, result)
        val captor = argumentCaptor<LocalRepoConfig>()
        verify(localRepoConfigRepo).saveLocalRepoConfig(captor.capture())
        assertEquals("repo", captor.firstValue.name)
        assertEquals("/repo", captor.firstValue.path)
        verify(localRepoConfigRepo, never()).findLocalRepoConfigById(any())
    }

    @Test
    fun `upsertLocalRepoConfig creates new entity when id is zero`() {
        val saved = LocalRepoConfig(id = 5L, name = "n", path = "/p")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(saved)

        service.upsertLocalRepoConfig(0L, "n", "/p", false, "CODE_ANALYSIS")

        verify(localRepoConfigRepo, never()).findLocalRepoConfigById(any())
    }

    @Test
    fun `upsertLocalRepoConfig dedups by path and scope when id is null`() {
        val existing =
            LocalRepoConfig(
                id = 11L,
                name = "old-name",
                path = "/private/tmp",
                active = false,
                scope = "LOGGING_ANALYSIS",
            )
        whenever(localRepoConfigRepo.findFirstByPathAndScope("/private/tmp", "LOGGING_ANALYSIS"))
            .thenReturn(existing)
        whenever(localRepoConfigRepo.saveLocalRepoConfig(existing)).thenAnswer {
            val saved = it.arguments[0] as LocalRepoConfig
            saved
        }

        val result =
            service.upsertLocalRepoConfig(
                id = null,
                name = "fresh-name",
                canonicalPath = "/private/tmp",
                active = true,
                scope = "LOGGING_ANALYSIS",
            )

        assertEquals(11L, result.id)
        assertEquals("fresh-name", result.name)
        assertEquals(true, result.active)
        verify(localRepoConfigRepo, never()).saveLocalRepoConfig(argThat<LocalRepoConfig> { config -> config.id == 0L })
    }

    @Test
    fun `upsertLocalRepoConfig falls back to a fresh insert when no (path, scope) match exists`() {
        whenever(localRepoConfigRepo.findFirstByPathAndScope("/brand-new", "LOGGING_ANALYSIS"))
            .thenReturn(null)
        val saved = LocalRepoConfig(id = 42L, name = "n", path = "/brand-new", scope = "LOGGING_ANALYSIS")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(saved)

        val result =
            service.upsertLocalRepoConfig(
                id = null,
                name = "n",
                canonicalPath = "/brand-new",
                active = true,
                scope = "LOGGING_ANALYSIS",
            )

        assertEquals(42L, result.id)
    }

    @Test
    fun `upsertLocalRepoConfig updates existing entity when id is positive`() {
        val existing = LocalRepoConfig(id = 7L, name = "old", path = "/old")
        val saved = LocalRepoConfig(id = 7L, name = "new", path = "/new")
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(existing)
        whenever(localRepoConfigRepo.saveLocalRepoConfig(existing)).thenReturn(saved)

        val result = service.upsertLocalRepoConfig(7L, "new", "/new", false, "CODE_ANALYSIS")

        assertEquals(saved, result)
        verify(localRepoConfigRepo).findLocalRepoConfigById(7L)
    }

    @Test
    fun `upsertLocalRepoConfig creates new entity when id is positive but not found`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(99L)).thenReturn(null)
        val saved = LocalRepoConfig(id = 99L, name = "r", path = "/r")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(saved)

        service.upsertLocalRepoConfig(99L, "r", "/r", true, "REPO_ANALYSIS")

        verify(localRepoConfigRepo).findLocalRepoConfigById(99L)
    }

    @Test
    fun `deleteLocalRepoConfig returns false when config does not exist`() {
        whenever(localRepoConfigRepo.localRepoConfigExistsById(10L)).thenReturn(false)

        assertFalse(service.deleteLocalRepoConfig(10L))
        verify(localRepoConfigRepo, never()).deleteLocalRepoConfigById(any())
        verify(codeAnalysisRepo, never()).deleteCodeAnalysisById(any())
    }

    @Test
    fun `deleteLocalRepoConfig deletes analyses and config when config exists`() {
        whenever(localRepoConfigRepo.localRepoConfigExistsById(5L)).thenReturn(true)
        val matching = CodeAnalysis(id = 1L, repoConfigId = 5L)
        val other = CodeAnalysis(id = 2L, repoConfigId = 99L)
        whenever(codeAnalysisRepo.findAllCodeAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(matching, other))

        val result = service.deleteLocalRepoConfig(5L)

        assertTrue(result)
        verify(codeAnalysisRepo).deleteCodeAnalysisById(1L)
        verify(codeAnalysisRepo, never()).deleteCodeAnalysisById(2L)
        verify(localRepoConfigRepo).deleteLocalRepoConfigById(5L)
    }

    @Test
    fun `listActiveGitlabConfigs maps configs to summaries`() {
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(gitlabConfig()))

        val summaries = service.listActiveGitlabConfigs()

        assertEquals(1, summaries.size)
        assertEquals(1L, summaries[0].id)
        assertEquals("gl", summaries[0].name)
        assertEquals("https://gitlab.example.com", summaries[0].baseUrl)
    }

    @Test
    fun `listGitlabProjects returns null when config not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(99L)).thenReturn(null)

        assertNull(service.listGitlabProjects(99L))
    }

    @Test
    fun `listGitlabProjects returns project summaries when config is found`() {
        val config = gitlabConfig()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        val info = GitlabProjectInfo(42L, "repo", "ns/repo", "main", "https://gitlab.example.com/ns/repo.git")
        whenever(gitlabPort.listAccessibleProjects(config)).thenReturn(listOf(info))

        val projects = service.listGitlabProjects(1L)

        assertNotNull(projects)
        assertEquals(1, projects!!.size)
        assertEquals(42L, projects[0].id)
        assertEquals("ns/repo", projects[0].pathWithNamespace)
    }

    @Test
    fun `cloneGitlabProject returns ConfigNotFound when config is missing`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(null)

        val result = service.cloneGitlabProject(1L, "https://gitlab.example.com/repo.git", "repo", "/tmp")

        assertEquals(GitlabCloneResult.ConfigNotFound, result)
    }

    @Test
    fun `cloneGitlabProject returns BadRequest when targetDir does not exist`(
        @TempDir base: File,
    ) {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())

        val missingDir = File(base, "does-not-exist").absolutePath

        val result =
            service.cloneGitlabProject(1L, "https://gitlab.example.com/ns/repo.git", "repo", missingDir)

        assertTrue(result is GitlabCloneResult.BadRequest)
    }

    @Test
    fun `cloneGitlabProject returns BadRequest when targetDir is a file`(
        @TempDir base: File,
    ) {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())

        val file = File(base, "not-a-dir.txt").also { it.writeText("x") }

        val result =
            service.cloneGitlabProject(1L, "https://gitlab.example.com/ns/repo.git", "repo", file.absolutePath)

        assertTrue(result is GitlabCloneResult.BadRequest)
    }

    @Test
    fun `cloneGitlabProject returns BadRequest when clone target directory already exists`(
        @TempDir base: File,
    ) {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        File(base, "repo").mkdirs()

        val result =
            service.cloneGitlabProject(1L, "https://gitlab.example.com/ns/repo.git", "repo", base.absolutePath)

        assertTrue(result is GitlabCloneResult.BadRequest)
    }

    @Test
    fun `cloneGitlabProject returns BadRequest for ssh url`(
        @TempDir base: File,
    ) {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())

        val result =
            service.cloneGitlabProject(1L, "git@gitlab.example.com:ns/repo.git", "repo", base.absolutePath)

        assertTrue(result is GitlabCloneResult.BadRequest)
    }

    @Test
    fun `cloneGitlabProject returns Success and saves repo config when clone succeeds with token`(
        @TempDir base: File,
    ) {
        val config = gitlabConfig(accessToken = "mytoken")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        whenever(gitClonePort.clone(any(), any(), any(), any())).thenReturn(base.canonicalPath + "/repo")
        val saved =
            LocalRepoConfig(id = 3L, name = "repo", path = base.canonicalPath + "/repo").apply {
                active = true
                scope = "CODE_ANALYSIS"
            }
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(saved)

        val result =
            service.cloneGitlabProject(
                1L,
                "https://gitlab.example.com/ns/repo.git",
                "repo",
                base.absolutePath,
            )

        assertTrue(result is GitlabCloneResult.Success)
        val success = result as GitlabCloneResult.Success
        assertEquals(3L, success.repoId)
        assertEquals("CODE_ANALYSIS", success.scope)
    }

    @Test
    fun `cloneGitlabProject returns Success without auth prefix when accessToken is blank`(
        @TempDir base: File,
    ) {
        val config = gitlabConfig(accessToken = "")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        whenever(gitClonePort.clone(any(), any(), any(), any())).thenReturn(base.canonicalPath + "/repo")
        val saved = LocalRepoConfig(id = 4L, name = "repo", path = base.canonicalPath + "/repo")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(saved)

        val result =
            service.cloneGitlabProject(
                1L,
                "https://gitlab.example.com/ns/repo.git",
                "repo",
                base.absolutePath,
            )

        assertTrue(result is GitlabCloneResult.Success)
    }

    @Test
    fun `cloneGitlabProject sanitizes blank and invalid project name characters`(
        @TempDir base: File,
    ) {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig(accessToken = ""))
        whenever(gitClonePort.clone(any(), any(), any(), any())).thenReturn(base.canonicalPath + "/repo")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenAnswer { invocation -> invocation.arguments[0] as LocalRepoConfig }

        val result = service.cloneGitlabProject(1L, "https://gitlab.example.com/ns/repo.git", "  ", base.absolutePath)

        assertTrue(result is GitlabCloneResult.Success)
        val captor = argumentCaptor<LocalRepoConfig>()
        verify(localRepoConfigRepo).saveLocalRepoConfig(captor.capture())
        assertEquals("repo", captor.firstValue.name)
    }

    @Test
    fun `cloneGitlabProject supports plain http urls with auth token`(
        @TempDir base: File,
    ) {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig(accessToken = "tok"))
        whenever(gitClonePort.clone(any(), any(), any(), any())).thenReturn(base.canonicalPath + "/repo")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(
            LocalRepoConfig(
                id = 8L,
                name = "repo",
                path =
                    base.canonicalPath + "/repo",
            ),
        )

        service.cloneGitlabProject(1L, "http://gitlab.example.com/ns/repo.git", "repo", base.absolutePath)

        val urlCaptor = argumentCaptor<String>()
        verify(gitClonePort).clone(urlCaptor.capture(), any(), any(), any())
        assertTrue(urlCaptor.firstValue.startsWith("http://oauth2:tok@"))
    }

    @Test
    fun `cloneGitlabProject returns ExecutionError when clone throws exception`(
        @TempDir base: File,
    ) {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        doThrow(RuntimeException("clone failed")).whenever(gitClonePort).clone(any(), any(), any(), any())

        val result =
            service.cloneGitlabProject(
                1L,
                "https://gitlab.example.com/ns/repo.git",
                "repo",
                base.absolutePath,
            )

        assertTrue(result is GitlabCloneResult.ExecutionError)
    }

    @Test
    fun `cloneGitlabProject handle http url with token correctly`(
        @TempDir base: File,
    ) {
        val config = gitlabConfig(accessToken = "tok")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        whenever(gitClonePort.clone(any(), any(), any(), any())).thenReturn(base.canonicalPath + "/repo")
        val saved = LocalRepoConfig(id = 5L, name = "repo", path = base.canonicalPath + "/repo")
        whenever(localRepoConfigRepo.saveLocalRepoConfig(any())).thenReturn(saved)

        val result =
            service.cloneGitlabProject(
                1L,
                "http://gitlab.example.com/ns/repo.git",
                "repo",
                base.absolutePath,
            )

        assertTrue(result is GitlabCloneResult.Success)
    }

    @Test
    fun `deleteLocalRepoConfig filter passes null repoConfigId analyses`(
        @TempDir base: File,
    ) {
        whenever(localRepoConfigRepo.localRepoConfigExistsById(5L)).thenReturn(true)
        val nullConfig = CodeAnalysis(id = 3L, repoConfigId = null)
        val matching = CodeAnalysis(id = 1L, repoConfigId = 5L)
        whenever(codeAnalysisRepo.findAllCodeAnalysesOrderByCreatedAtDesc()).thenReturn(listOf(nullConfig, matching))

        val result = service.deleteLocalRepoConfig(5L)

        assertTrue(result)
        verify(codeAnalysisRepo, never()).deleteCodeAnalysisById(3L)
        verify(codeAnalysisRepo).deleteCodeAnalysisById(1L)
    }

    @Test
    fun `cloneGitlabProject returns ExecutionError with Unknown when exception has no message`(
        @TempDir base: File,
    ) {
        val config = gitlabConfig(accessToken = "tok")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        doThrow(RuntimeException()).whenever(gitClonePort).clone(any(), any(), any(), any())

        val result = service.cloneGitlabProject(1L, "https://gitlab.example.com/ns/repo.git", "repo", base.absolutePath)

        assertTrue(result is GitlabCloneResult.ExecutionError)
    }
}
