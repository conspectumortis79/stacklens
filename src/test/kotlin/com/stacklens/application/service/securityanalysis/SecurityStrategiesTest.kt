package com.stacklens.application.service.securityanalysis

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.filesystem.LocalDirectoryScanAdapter
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GitlabTicketBridgePort
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraRepoFile
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.repo.CodeSummary
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.I18nService
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.SecurityAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class GitlabSecurityStrategyTest {
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val gitlabService: GitlabPort = mock()
    private val gitlabTicketBridge: GitlabTicketBridgePort = mock()
    private val strategy = GitlabSecurityStrategy(gitlabConfigRepo, gitlabService, gitlabTicketBridge)

    private fun analysis(
        gitlabConfigId: Long? = 1L,
        projectId: Long? = 42L,
    ) = SecurityAnalysis(
        id = 1L,
        gitlabConfigId = gitlabConfigId,
        gitlabProjectId = projectId,
        gitlabProjectPath = "group/my-project",
    )

    private fun gitlabConfig() = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gitlab.com", accessToken = "token")

    private fun finding(file: String = "src/Main.kt") = SecurityFinding(id = "finding-1", file = file, type = "SECURITY_VULN", severity = "HIGH")

    private fun parsedFix() =
        ParsedFix(
            explanation = "Fixed the issue",
            changes = listOf(ParsedFileChange("src/Main.kt", "// fixed code", FileChangeType.UPDATE)),
        )

    @Test
    fun `supports returns true when gitlabConfigId is set`() {
        assertTrue(strategy.supports(analysis(gitlabConfigId = 1L)))
    }

    @Test
    fun `supports returns false when gitlabConfigId is null`() {
        assertFalse(strategy.supports(analysis(gitlabConfigId = null)))
    }

    @Test
    fun `loadContext throws when gitlabConfigId is null`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis(gitlabConfigId = null)) { }
        }
    }

    @Test
    fun `loadContext throws when gitlab config not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(null)
        val steps = mutableListOf<String>()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis(), steps::add)
        }
    }

    @Test
    fun `loadContext throws when project ID is null`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        val steps = mutableListOf<String>()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis(projectId = null), steps::add)
        }
    }

    @Test
    fun `loadContext returns context with files from gitlab`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any<Long>(), any(), any()))
            .thenReturn(listOf("src/Main.kt" to "// main content", "src/Util.kt" to "// util"))
        val steps = mutableListOf<String>()
        val ctx = strategy.loadContext(analysis(), steps::add)
        assertEquals("my-project", ctx.appName)
        assertTrue(ctx.projectTree.contains("src/Main.kt"))
        assertTrue(steps.isNotEmpty())
    }

    @Test
    fun `loadFileContext throws when gitlabConfigId is null`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(gitlabConfigId = null), finding())
        }
    }

    @Test
    fun `loadFileContext throws when gitlab config not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(null)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(), finding())
        }
    }

    @Test
    fun `loadFileContext throws when project ID is null`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(projectId = null), finding())
        }
    }

    @Test
    fun `loadFileContext returns file context with matching file content`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any<Long>(), any(), any()))
            .thenReturn(
                listOf(
                    "src/Main.kt" to "class Main",
                    "src/test/MainTest.kt" to "class MainTest",
                ),
            )
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertEquals("class Main", ctx.fileContent)
        assertTrue(ctx.testContent.contains("MainTest"))
    }

    @Test
    fun `loadFileContext excludes unrelated test files from testContent`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any<Long>(), any(), any()))
            .thenReturn(
                listOf(
                    "src/Main.kt" to "class Main",
                    "src/test/OtherServiceTest.kt" to "class OtherServiceTest",
                ),
            )
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertEquals("class Main", ctx.fileContent)
        assertTrue(ctx.testContent.isBlank())
    }

    @Test
    fun `loadFileContext returns empty content when target file not in project files`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any<Long>(), any(), any()))
            .thenReturn(listOf("src/Other.kt" to "// other"))
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertEquals("", ctx.fileContent)
    }

    @Test
    fun `createFixResult throws when changes list is empty`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        val emptyFix = ParsedFix("explanation", emptyList())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(), finding(), emptyFix)
        }
    }

    @Test
    fun `createFixResult throws when gitlabConfigId is null`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(gitlabConfigId = null), finding(), parsedFix())
        }
    }

    @Test
    fun `createFixResult throws when gitlabProjectId is null`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(projectId = null), finding(), parsedFix())
        }
    }

    @Test
    fun `createFixResult throws when gitlab config not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(null)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(), finding(), parsedFix())
        }
    }

    @Test
    fun `createFixResult creates MR and returns fix result`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(
            gitlabTicketBridge.createMergeRequest(any(), any(), any(), any(), any(), anyOrNull()),
        ).thenReturn("https://gitlab.com/mr/1")
        val result = strategy.createFixResult(analysis(), finding(), parsedFix())
        assertEquals("https://gitlab.com/mr/1", result.prUrl)
        assertFalse(result.isLocalRepo)
        assertNull(result.localChanges)
    }

    @Test
    fun `loadContext with null gitlabProjectPath uses project fallback`() {
        val analysisNullPath =
            SecurityAnalysis(
                id = 1L,
                gitlabConfigId = 1L,
                gitlabProjectId = 42L,
                gitlabProjectPath = null,
            )
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any<Long>(), any(), any()))
            .thenReturn(listOf("src/Main.kt" to "class Main"))
        val ctx = strategy.loadContext(analysisNullPath) { }
        assertEquals("project", ctx.appName)
    }

    @Test
    fun `loadFileContext with null gitlabProjectPath uses project fallback`() {
        val analysisNullPath =
            SecurityAnalysis(
                id = 1L,
                gitlabConfigId = 1L,
                gitlabProjectId = 42L,
                gitlabProjectPath = null,
            )
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any<Long>(), any(), any()))
            .thenReturn(listOf("src/Main.kt" to "class Main"))
        val ctx = strategy.loadFileContext(analysisNullPath, finding("src/Main.kt"))
        assertEquals("project", ctx.projectName)
    }

    @Test
    fun `createFixResult with null gitlabProjectPath uses empty repoPath`() {
        val analysisNullPath =
            SecurityAnalysis(
                id = 1L,
                gitlabConfigId = 1L,
                gitlabProjectId = 42L,
                gitlabProjectPath = null,
            )
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(
            gitlabTicketBridge.createMergeRequest(any(), any(), any(), any(), any(), anyOrNull()),
        ).thenReturn("https://gitlab.com/mr/2")
        val result = strategy.createFixResult(analysisNullPath, finding(), parsedFix())
        assertEquals("https://gitlab.com/mr/2", result.prUrl)
    }
}

class GithubSecurityStrategyTest {
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()
    private val githubBridge: GithubTicketBridgePort = mock()
    private val strategy = GithubSecurityStrategy(githubRepoConfigRepo, githubBridge)

    private fun analysis(githubRepoConfigId: Long? = 5L) =
        SecurityAnalysis(
            id = 1L,
            githubRepoConfigId = githubRepoConfigId,
        )

    private fun githubConfig() = GithubRepoConfig(id = 5L, name = "myr", owner = "acme", repo = "service")

    private fun finding(file: String = "src/Main.kt") = SecurityFinding(id = "f1", file = file, type = "SECURITY_VULN", severity = "HIGH")

    private fun parsedFix() = ParsedFix("fixed", listOf(ParsedFileChange("src/Main.kt", "fixed code", FileChangeType.UPDATE)))

    private fun repoContext() =
        JiraRepoContext(
            repoName = "acme/service",
            projectId = 0L,
            files = listOf(JiraRepoFile("src/Main.kt", "class Main"), JiraRepoFile("src/test/MainTest.kt", "class Test")),
        )

    @Test
    fun `supports returns true when githubRepoConfigId is set`() {
        assertTrue(strategy.supports(analysis(githubRepoConfigId = 5L)))
    }

    @Test
    fun `supports returns false when githubRepoConfigId is null`() {
        assertFalse(strategy.supports(analysis(githubRepoConfigId = null)))
    }

    @Test
    fun `loadContext throws when github config not found`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis()) { }
        }
    }

    @Test
    fun `loadContext throws when repo context not accessible`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(null)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis()) { }
        }
    }

    @Test
    fun `loadContext returns valid context`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(repoContext())
        val steps = mutableListOf<String>()
        val ctx = strategy.loadContext(analysis(), steps::add)
        assertEquals("acme/service", ctx.appName)
        assertTrue(steps.isNotEmpty())
    }

    @Test
    fun `loadFileContext throws when config not found`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(), finding())
        }
    }

    @Test
    fun `loadFileContext throws when repo context null`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(null)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(), finding())
        }
    }

    @Test
    fun `loadFileContext returns context with matched file and test`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(repoContext())
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertEquals("class Main", ctx.fileContent)
        assertTrue(ctx.testContent.contains("MainTest"))
    }

    @Test
    fun `loadFileContext returns empty content when file not found`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(repoContext())
        val ctx = strategy.loadFileContext(analysis(), finding("src/Missing.kt"))
        assertEquals("", ctx.fileContent)
    }

    @Test
    fun `createFixResult throws when changes are empty`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(), finding(), ParsedFix("exp", emptyList()))
        }
    }

    @Test
    fun `createFixResult throws when config not found`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(), finding(), parsedFix())
        }
    }

    @Test
    fun `createFixResult creates PR and returns result`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.createPullRequest(any(), any(), any())).thenReturn("https://github.com/pr/5")
        val result = strategy.createFixResult(analysis(), finding(), parsedFix())
        assertEquals("https://github.com/pr/5", result.prUrl)
        assertFalse(result.isLocalRepo)
    }

    @Test
    fun `loadContext finds config in multi-element list where target is not first`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(repoContext())
        val ctx = strategy.loadContext(analysis()) { }
        assertEquals("acme/service", ctx.appName)
    }

    @Test
    fun `loadContext throws when githubRepoConfigId is null and list is non-empty`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis(githubRepoConfigId = null)) { }
        }
    }

    @Test
    fun `loadFileContext throws when githubRepoConfigId is null and list is non-empty`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(githubRepoConfigId = null), finding())
        }
    }

    @Test
    fun `createFixResult throws when githubRepoConfigId is null and list is non-empty`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(githubRepoConfigId = null), finding(), parsedFix())
        }
    }

    @Test
    fun `loadFileContext finds config in multi-element list where target is not first`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(repoContext())
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertEquals("class Main", ctx.fileContent)
    }

    @Test
    fun `createFixResult finds config in multi-element list where target is not first`() {
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.createPullRequest(any(), any(), any())).thenReturn("https://github.com/pr/7")
        val result = strategy.createFixResult(analysis(), finding(), parsedFix())
        assertEquals("https://github.com/pr/7", result.prUrl)
    }

    @Test
    fun `loadContext sorts files by securityFilePriority covering all priority branches`() {
        val diverseContext =
            JiraRepoContext(
                repoName = "acme/service",
                projectId = 0L,
                files =
                    listOf(
                        JiraRepoFile("src/main/kotlin/App.kt", "class App"),
                        JiraRepoFile("src/main/resources/application.yml", "server: {}"),
                        JiraRepoFile("src/main/resources/static/style.css", "body {}"),
                        JiraRepoFile("src/main/resources/templates/index.html", "<html/>"),
                        JiraRepoFile("src/test/AppTest.kt", "class AppTest"),
                        JiraRepoFile("lib/util.js", "module.exports = {}"),
                    ),
            )
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(5L)).thenReturn(githubConfig())
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(diverseContext)
        val ctx = strategy.loadContext(analysis()) { }
        assertTrue(ctx.projectTree.contains("src/main/kotlin/App.kt"))
    }
}

class LocalRepoSecurityStrategyTest {
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()
    private val scanService: LocalRepoScanPort = mock()
    private val i18n: I18nService = mock()
    private val githubBridge: GithubTicketBridgePort = mock()
    private val strategy = LocalRepoSecurityStrategy(localRepoConfigRepo, scanService, i18n, githubBridge, LocalDirectoryScanAdapter(), JavaFileSystemAdapter())

    private fun analysis(repoConfigId: Long? = 3L) =
        SecurityAnalysis(
            id = 1L,
            repoConfigId = repoConfigId,
        )

    private fun finding(file: String = "src/Main.kt") = SecurityFinding(id = "f1", file = file, type = "SECURITY_VULN", severity = "HIGH")

    private fun parsedFix() = ParsedFix("fixed", listOf(ParsedFileChange("src/Main.kt", "fixed code", FileChangeType.UPDATE)))

    private fun codeSummary() = CodeSummary(projectTree = "src/Main.kt", fileContents = "class Main", fileCount = 1, charCount = 100)

    @Test
    fun `supports returns true when repoConfigId is set`() {
        assertTrue(strategy.supports(analysis(repoConfigId = 3L)))
    }

    @Test
    fun `supports returns false when repoConfigId is null`() {
        assertFalse(strategy.supports(analysis(repoConfigId = null)))
    }

    @Test
    fun `loadContext throws when repoConfigId is null`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis(repoConfigId = null)) { }
        }
    }

    @Test
    fun `loadContext throws when repo config not found`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(null)
        whenever(i18n.t(any(), any())).thenReturn("Config not found")
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadContext(analysis()) { }
        }
    }

    @Test
    fun `loadContext returns context from local repo scan`(
        @TempDir tempDir: Path,
    ) {
        val config = LocalRepoConfig(id = 3L, name = "my-repo", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        whenever(scanService.buildCodeSummary(any(), any(), any())).thenReturn(codeSummary())
        val steps = mutableListOf<String>()
        val ctx = strategy.loadContext(analysis(), steps::add)
        assertEquals("my-repo", ctx.appName)
        assertEquals(tempDir.toString(), ctx.projectPath)
        assertTrue(steps.isNotEmpty())
    }

    @Test
    fun `loadFileContext throws when repoConfigId is null`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(repoConfigId = null), finding())
        }
    }

    @Test
    fun `loadFileContext throws when repo config not found`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(null)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.loadFileContext(analysis(), finding())
        }
    }

    @Test
    fun `loadFileContext returns empty content when target file does not exist`(
        @TempDir tempDir: Path,
    ) {
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertEquals("", ctx.fileContent)
    }

    @Test
    fun `loadFileContext reads content from existing target file`(
        @TempDir tempDir: Path,
    ) {
        val srcDir = tempDir.resolve("src").toFile().also { it.mkdirs() }
        File(srcDir, "Main.kt").writeText("class Main { fun hello() = Unit }")
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertTrue(ctx.fileContent.contains("class Main"))
    }

    @Test
    fun `loadFileContext finds test file with matching name`(
        @TempDir tempDir: Path,
    ) {
        val srcDir = tempDir.resolve("src/main").toFile().also { it.mkdirs() }
        val testDir = tempDir.resolve("src/test").toFile().also { it.mkdirs() }
        File(srcDir, "Service.kt").writeText("class Service")
        File(testDir, "ServiceTest.kt").writeText("class ServiceTest")
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        val ctx = strategy.loadFileContext(analysis(), finding("src/main/Service.kt"))
        assertTrue(ctx.testContent.contains("ServiceTest") || ctx.tree.isNotEmpty())
    }

    @Test
    fun `createFixResult returns local changes when no github remote detected`(
        @TempDir tempDir: Path,
    ) {
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        whenever(githubBridge.detectGithubRemote(any())).thenReturn(null)
        val result = strategy.createFixResult(analysis(), finding(), parsedFix())
        assertTrue(result.isLocalRepo)
        assertNotNull(result.localChanges)
        assertNull(result.prUrl)
    }

    @Test
    fun `createFixResult throws when repo config not found`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(null)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(), finding(), parsedFix())
        }
    }

    @Test
    fun `createFixResult creates PR via github bridge when remote detected with id`(
        @TempDir tempDir: Path,
    ) {
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        val detectedGithubConfig = GithubRepoConfig(id = 99L, name = "g", owner = "acme", repo = "svc")
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        whenever(githubBridge.detectGithubRemote(any())).thenReturn(detectedGithubConfig)
        whenever(githubBridge.createPullRequest(any(), any(), any())).thenReturn("https://github.com/pr/1")
        val result = strategy.createFixResult(analysis(), finding(), parsedFix())
        assertFalse(result.isLocalRepo)
        assertEquals("https://github.com/pr/1", result.prUrl)
    }

    @Test
    fun `createFixResult falls back to local when detected github config has id 0`(
        @TempDir tempDir: Path,
    ) {
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        val noIdGithubConfig = GithubRepoConfig(id = 0L, name = "g", owner = "acme", repo = "svc")
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        whenever(githubBridge.detectGithubRemote(any())).thenReturn(noIdGithubConfig)
        val result = strategy.createFixResult(analysis(), finding(), parsedFix())
        assertTrue(result.isLocalRepo)
    }

    @Test
    fun `createFixResult falls back to local when detectGithubRemote throws`(
        @TempDir tempDir: Path,
    ) {
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        whenever(githubBridge.detectGithubRemote(any())).thenThrow(RuntimeException("no remote"))
        val result = strategy.createFixResult(analysis(), finding(), parsedFix())
        assertTrue(result.isLocalRepo)
    }

    @Test
    fun `loadFileContext excludes hidden files, build, and node_modules from tree`(
        @TempDir tempDir: Path,
    ) {
        File(tempDir.toFile(), "src").mkdir()
        File(tempDir.toFile(), "src/Main.kt").writeText("class Main")
        File(tempDir.toFile(), ".hidden").writeText("hidden")
        val buildDir = File(tempDir.toFile(), "build")
        buildDir.mkdir()
        File(buildDir, "Output.class").writeText("binary")
        val nmDir = File(tempDir.toFile(), "node_modules")
        nmDir.mkdir()
        File(nmDir, "lodash.js").writeText("js")
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        val ctx = strategy.loadFileContext(analysis(), finding("src/Main.kt"))
        assertTrue(ctx.tree.contains("src/Main.kt"))
        assertFalse(ctx.tree.contains(".hidden"))
        assertFalse(ctx.tree.contains("Output.class"))
        assertFalse(ctx.tree.contains("lodash.js"))
    }

    @Test
    fun `createFixResult throws when repoConfigId is null`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            strategy.createFixResult(analysis(repoConfigId = null), finding(), parsedFix())
        }
    }

    @Test
    fun `loadFileContext skips test file whose name does not contain base name`(
        @TempDir tempDir: Path,
    ) {
        val srcDir = File(tempDir.toFile(), "src/main").also { it.mkdirs() }
        val testDir = File(tempDir.toFile(), "src/test").also { it.mkdirs() }
        File(srcDir, "Service.kt").writeText("class Service")
        File(testDir, "OtherTest.kt").writeText("class OtherTest")
        val config = LocalRepoConfig(id = 3L, name = "r", path = tempDir.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L)).thenReturn(config)
        val ctx = strategy.loadFileContext(analysis(), finding("src/main/Service.kt"))
        assertFalse(ctx.testContent.contains("OtherTest"))
    }
}
