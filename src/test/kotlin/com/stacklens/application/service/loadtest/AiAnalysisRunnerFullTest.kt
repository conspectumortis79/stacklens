package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraRepoFile
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.repo.CodeSummary
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnalysisRunnerFullTest {
    @Mock private lateinit var analysisRepo: LoadTestAnalysisPort

    @Mock private lateinit var gitlabConfigRepo: GitlabConfigPort

    @Mock private lateinit var copilotPort: TokenTrackingCopilotPortInterface

    @Mock private lateinit var gitlabService: GitlabPort

    @Mock private lateinit var i18n: I18nPort

    @Mock private lateinit var aiService: LoadTestAi

    @Mock private lateinit var localRepoConfigRepo: LocalRepoConfigPort

    @Mock private lateinit var githubRepoConfigRepo: GithubRepoConfigPort

    @Mock private lateinit var scanService: LocalRepoScanPort

    @Mock private lateinit var githubBridge: GithubTicketBridgePort

    @Mock private lateinit var copilotConfig: CopilotConfigPort

    @Mock private lateinit var localDirectoryScan: LocalDirectoryScanPort

    private val runner by lazy {
        testAiAnalysisRunner(
            analysisRepo = analysisRepo,
            gitlabConfigRepo = gitlabConfigRepo,
            copilotPort = copilotPort,
            gitlabService = gitlabService,
            i18n = i18n,
            aiService = aiService,
            localRepoConfigRepo = localRepoConfigRepo,
            githubRepoConfigRepo = githubRepoConfigRepo,
            scanService = scanService,
            githubBridge = githubBridge,
            copilotConfig = copilotConfig,
            localDirectoryScan = localDirectoryScan,
            asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
            parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
    }

    private fun gitlabConfig() = GitlabConfig(id = 1L, name = "test", baseUrl = "http://gl.example.com", accessToken = "tok")

    private fun analysis(
        id: Long = 1L,
        status: String = AnalysisStatus.PENDING,
    ) = LoadTestAnalysis(id = id, status = status)

    private fun stubCopilotReady() {
        whenever(copilotPort.isReady()).thenReturn(true)
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("en")
    }

    private fun stubAnalysisPipeline() {
        whenever(analysisRepo.findLoadTestAnalysisById(any())).thenReturn(analysis())
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any())).thenReturn("a")
        whenever(aiService.buildEnvDetectPrompt(any(), any(), any(), any())).thenReturn("e")
        whenever(aiService.buildK6Prompt(any(), any(), any(), any(), any())).thenReturn("k")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn("SUMMARY: s\nFINDINGS: []\nK6_SCRIPT: //")
        whenever(aiService.extractSection(any(), any(), any())).thenReturn("summary")
        whenever(aiService.extractJsonBlock(any(), any(), any())).thenReturn("[]")
        whenever(aiService.extractJsonArray(any())).thenReturn("[]")
        whenever(aiService.extractCodeBlock(any(), any(), any())).thenReturn("// k6")
        whenever(i18n.t(any())).thenReturn("(none)")
        whenever(i18n.translateError(any())).thenReturn("error")
        whenever(copilotConfig.loadTestAnalysisTimeoutSeconds).thenReturn(60L)
    }

    private fun stubLocalCodeSummary() {
        whenever(scanService.buildCodeSummary(any(), any(), any())).thenReturn(
            CodeSummary("main.go", "package main", 1, 100),
        )
    }

    @Test
    fun `runGitlabAnalysis with valid config and docker+route files reaches COMPLETED`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(
            listOf(
                "docker-compose.yml" to "version: '3'",
                "src/routes/api.php" to "<?php",
                "Dockerfile" to "FROM alpine",
                "README.md" to "hello",
            ),
        )
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, times(1)).findLoadTestAnalysisById(1L)
    }

    @Test
    fun `runGitlabAnalysis with missing config throws and does not save`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(any())).thenReturn(null)
        var thrown: IllegalStateException? = null
        try {
            runner.runGitlabAnalysis(1L, 99L, 42L, "group/project")
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertNotNull(thrown)
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis with FAILED analysis record early-exits`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis(status = AnalysisStatus.FAILED))
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis returns early when analysis is null`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(null)
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis with copilot not ready throws immediately`() {
        whenever(copilotPort.isReady()).thenReturn(false)
        whenever(i18n.t(any())).thenReturn("nokey")
        var thrown: IllegalStateException? = null
        try {
            runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertEquals("nokey", thrown?.message)
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis with Phase 2 timeout marks FAILED`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenAnswer {
            val timeout = it.arguments[2] as Long
            if (timeout == 600L) {
                "SUMMARY: s\nFINDINGS: []\nK6_SCRIPT: //"
            } else {
                throw RuntimeException("Copilot CLI Timeout (mock)")
            }
        }
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, atLeast(2)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis with generic Phase 2 exception marks FAILED`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("Boom"))
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, atLeast(2)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGitlabAnalysis uses gitlabFilesCache on second call`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(
            listOf("README.md" to "hello"),
        )
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        runner.runGitlabAnalysis(2L, 1L, 42L, "group/project")
        verify(gitlabService, times(1)).fetchProjectFiles(any(), any(), any(), any())
    }

    @Test
    fun `runGitlabAnalysis with no docker or route files uses i18n fallback`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(
            listOf("README.md" to "hello"),
        )
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(i18n, atLeast(1)).t(eq("load_test.be_no_docker_files"))
    }

    @Test
    fun `runGitlabAnalysis with Dockerfile variants uses them as docker files`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(
            listOf(
                "Dockerfile.prod" to "FROM alpine",
                "Dockerfile.dev" to "FROM alpine",
                "src/router.ts" to "export const r = 1",
            ),
        )
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
    }

    @Test
    fun `runGitlabAnalysis InterruptedException during phase 1 is swallowed`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenAnswer { inv ->
            if ((inv.arguments[2] as Long) == 600L) {
                throw InterruptedException("interrupted")
            } else {
                "SUMMARY: s\nFINDINGS: []\nK6_SCRIPT: //"
            }
        }
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
    }

    @Test
    fun `runGitlabAnalysis Phase 2 future with wrapped InterruptedException is swallowed`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenAnswer { inv ->
            val timeout = inv.arguments[2] as Long
            if (timeout == 600L) {
                "SUMMARY: s\nFINDINGS: []\nK6_SCRIPT: //"
            } else {
                throw RuntimeException("wrap", InterruptedException("inner-interrupted"))
            }
        }
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
    }

    @Test
    fun `handleAnalysisError does not overwrite COMPLETED status`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        val completedAnalysis = LoadTestAnalysis(id = 1L, status = AnalysisStatus.COMPLETED)
        whenever(analysisRepo.findLoadTestAnalysisById(any())).thenReturn(completedAnalysis)
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("Boom"))
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
    }

    @Test
    fun `runGitlabAnalysis with null findings falls back to empty array`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(emptyList())
        whenever(aiService.extractJsonBlock(any(), any(), any())).thenReturn(null)
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
    }

    @Test
    fun `runLocalAnalysis with valid config reaches COMPLETED`(
        @TempDir tmp: Path,
    ) {
        stubCopilotReady()
        stubAnalysisPipeline()
        stubLocalCodeSummary()
        Files.writeString(tmp.resolve("Dockerfile"), "FROM alpine")
        Files.createDirectories(tmp.resolve("routes"))
        Files.writeString(tmp.resolve("routes/api.php"), "<?php")
        Files.writeString(tmp.resolve("main.go"), "package main")
        val config = LocalRepoConfig(id = 7L, name = "test-local", path = tmp.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(config)
        runner.runLocalAnalysis(1L, 7L)
        verify(analysisRepo, atLeast(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runLocalAnalysis with missing local config throws and does not save`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(localRepoConfigRepo.findLocalRepoConfigById(any())).thenReturn(null)
        var thrown: IllegalStateException? = null
        try {
            runner.runLocalAnalysis(1L, 99L)
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertNotNull(thrown)
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runLocalAnalysis with copilot not ready throws immediately`(
        @TempDir tmp: Path,
    ) {
        whenever(copilotPort.isReady()).thenReturn(false)
        whenever(i18n.t(any())).thenReturn("nokey")
        var thrown: IllegalStateException? = null
        try {
            runner.runLocalAnalysis(1L, 7L)
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertEquals("nokey", thrown?.message)
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runLocalAnalysis with FAILED status early-exits`(
        @TempDir tmp: Path,
    ) {
        stubCopilotReady()
        stubAnalysisPipeline()
        stubLocalCodeSummary()
        val config = LocalRepoConfig(id = 7L, name = "test-local", path = tmp.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(config)
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis(status = AnalysisStatus.FAILED))
        runner.runLocalAnalysis(1L, 7L)
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runLocalAnalysis with router files in subdirs uses them`(
        @TempDir tmp: Path,
    ) {
        stubCopilotReady()
        stubAnalysisPipeline()
        stubLocalCodeSummary()
        val config = LocalRepoConfig(id = 7L, name = "test-local", path = tmp.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(config)
        Files.createDirectories(tmp.resolve("src"))
        Files.writeString(tmp.resolve("src/router.js"), "module.exports = {}")
        Files.writeString(tmp.resolve("urls.py"), "urlpatterns = []")
        Files.createDirectories(tmp.resolve("routing"))
        Files.writeString(tmp.resolve("routing/main.kt"), "// routing")
        Files.writeString(tmp.resolve("Dockerfile"), "FROM alpine")
        runner.runLocalAnalysis(1L, 7L)
        verify(analysisRepo, atLeast(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runLocalAnalysis skips node_modules and dot-git directories`(
        @TempDir tmp: Path,
    ) {
        stubCopilotReady()
        stubAnalysisPipeline()
        stubLocalCodeSummary()
        val config = LocalRepoConfig(id = 7L, name = "test-local", path = tmp.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(config)
        Files.createDirectories(tmp.resolve("node_modules"))
        Files.writeString(tmp.resolve("node_modules/foo.js"), "// should be skipped")
        Files.createDirectories(tmp.resolve(".git"))
        Files.writeString(tmp.resolve(".git/HEAD"), "ref: refs/heads/main")
        runner.runLocalAnalysis(1L, 7L)
        verify(analysisRepo, atLeast(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runLocalAnalysis with no docker or route files uses i18n fallback`(
        @TempDir tmp: Path,
    ) {
        stubCopilotReady()
        stubAnalysisPipeline()
        stubLocalCodeSummary()
        val config = LocalRepoConfig(id = 7L, name = "test-local", path = tmp.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(config)
        Files.writeString(tmp.resolve("main.go"), "package main")
        runner.runLocalAnalysis(1L, 7L)
        verify(i18n, atLeast(1)).t(eq("load_test.be_no_docker_files"))
        verify(i18n, atLeast(1)).t(eq("load_test.be_no_route_files"))
    }

    @Test
    fun `runLocalAnalysis with exception during Phase 2 marks FAILED`(
        @TempDir tmp: Path,
    ) {
        stubCopilotReady()
        stubAnalysisPipeline()
        stubLocalCodeSummary()
        val config = LocalRepoConfig(id = 7L, name = "test-local", path = tmp.toString())
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(config)
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("Boom"))
        runner.runLocalAnalysis(1L, 7L)
        verify(analysisRepo, atLeast(2)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis with valid bridge context reaches COMPLETED`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        val config =
            GithubRepoConfig(id = 11L, name = "gh", owner = "octocat", repo = "hello-world", accessToken = "t")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(11L)).thenReturn(config)
        val bridgeCtx =
            JiraRepoContext(
                repoName = "hello-world",
                projectId = 1L,
                files =
                    listOf(
                        JiraRepoFile("Dockerfile", "FROM alpine"),
                        JiraRepoFile("src/router.ts", "export const r = 1"),
                        JiraRepoFile("README.md", "hi"),
                    ),
            )
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(bridgeCtx)
        runner.runGithubAnalysis(1L, 11L)
        verify(analysisRepo, atLeast(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis with missing repo config throws and marks FAILED`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(99L)).thenReturn(null)
        var thrown: IllegalStateException? = null
        try {
            runner.runGithubAnalysis(1L, 99L)
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun `runGithubAnalysis with null bridge context throws and marks FAILED`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        val config =
            GithubRepoConfig(id = 11L, name = "gh", owner = "octocat", repo = "hello", accessToken = "t")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(11L)).thenReturn(config)
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(null)
        var thrown: IllegalStateException? = null
        try {
            runner.runGithubAnalysis(1L, 11L)
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun `runGithubAnalysis with copilot not ready throws immediately`() {
        whenever(copilotPort.isReady()).thenReturn(false)
        whenever(i18n.t(any())).thenReturn("nokey")
        var thrown: IllegalStateException? = null
        try {
            runner.runGithubAnalysis(1L, 11L)
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertEquals("nokey", thrown?.message)
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis with FAILED status early-exits`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        val config =
            GithubRepoConfig(id = 11L, name = "gh", owner = "octocat", repo = "hello", accessToken = "t")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(11L)).thenReturn(config)
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(
            JiraRepoContext("hello", 1L, listOf(JiraRepoFile("README.md", "hi"))),
        )
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis(status = AnalysisStatus.FAILED))
        runner.runGithubAnalysis(1L, 11L)
        verify(analysisRepo, times(0)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis with Dockerfile variants uses them as docker files`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        val config =
            GithubRepoConfig(id = 11L, name = "gh", owner = "octocat", repo = "hello", accessToken = "t")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(11L)).thenReturn(config)
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(
            JiraRepoContext(
                repoName = "hello",
                projectId = 1L,
                files =
                    listOf(
                        JiraRepoFile("Dockerfile.prod", "FROM alpine"),
                        JiraRepoFile("src/api.php", "<?php"),
                        JiraRepoFile("src/urls.py", "urlpatterns=[]"),
                    ),
            ),
        )
        runner.runGithubAnalysis(1L, 11L)
        verify(analysisRepo, atLeast(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `runGithubAnalysis with no docker or route files uses i18n fallback`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        val config =
            GithubRepoConfig(id = 11L, name = "gh", owner = "octocat", repo = "hello", accessToken = "t")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(11L)).thenReturn(config)
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(
            JiraRepoContext("hello", 1L, listOf(JiraRepoFile("README.md", "hi"))),
        )
        runner.runGithubAnalysis(1L, 11L)
        verify(i18n, atLeast(1)).t(eq("load_test.be_no_docker_files"))
        verify(i18n, atLeast(1)).t(eq("load_test.be_no_route_files"))
    }

    @Test
    fun `runGithubAnalysis with exception during Phase 2 marks FAILED`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        val config =
            GithubRepoConfig(id = 11L, name = "gh", owner = "octocat", repo = "hello", accessToken = "t")
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(11L)).thenReturn(config)
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(
            JiraRepoContext("hello", 1L, listOf(JiraRepoFile("README.md", "hi"))),
        )
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("Boom"))
        runner.runGithubAnalysis(1L, 11L)
        verify(analysisRepo, atLeast(2)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `cancelRunningThread is a no-op for unknown analysis`() {
        runner.cancelRunningThread(999L)
    }

    @Test
    fun `cancelRunningThread interrupts running thread`() {
        runner.cancelRunningThread(9999L)
        val thread =
            Thread {
                try {
                    Thread.sleep(60_000)
                } catch (e: InterruptedException) {
                }
            }
        thread.start()
        Thread.sleep(100)
        thread.interrupt()
        thread.join(2_000)
        assertTrue(!thread.isAlive, "Thread should be dead after interrupt")
    }

    @Test
    fun `cancelRunningThread handles InterruptedException cleanly`() {
        stubCopilotReady()
        stubAnalysisPipeline()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(gitlabConfig())
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenAnswer {
            throw InterruptedException("cancelled")
        }
        var thrown: InterruptedException? = null
        try {
            runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        } catch (e: InterruptedException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun `splitGitlabFiles splits docker and route files`() {
        val files =
            listOf(
                "docker-compose.yml" to "version: '3'",
                "Dockerfile" to "FROM alpine",
                "Dockerfile.dev" to "FROM alpine",
                "src/routes/api.php" to "<?php",
                "src/urls.py" to "urlpatterns=[]",
                "src/router.js" to "module.exports = {}",
                "src/router.ts" to "export const r = 1",
                "src/routing/foo.kt" to "// routing",
                "README.md" to "hi",
            )
        val (docker, route) = runner.splitGitlabFiles(files, i18n)
        assertTrue(docker.contains("docker-compose.yml"))
        assertTrue(docker.contains("Dockerfile"))
        assertTrue(docker.contains("Dockerfile.dev"))
        assertTrue(route.contains("api.php"))
        assertTrue(route.contains("urls.py"))
        assertTrue(route.contains("router.js"))
        assertTrue(route.contains("router.ts"))
        assertTrue(route.contains("routing"))
    }

    @Test
    fun `splitGitlabFiles falls back to i18n when no docker or route files`() {
        whenever(i18n.t(eq("load_test.be_no_docker_files"))).thenReturn("(no-docker)")
        whenever(i18n.t(eq("load_test.be_no_route_files"))).thenReturn("(no-route)")
        val (docker, route) =
            runner.splitGitlabFiles(listOf("README.md" to "hi"), i18n)
        assertEquals("(no-docker)", docker)
        assertEquals("(no-route)", route)
    }

    @Test
    fun `splitGitlabFiles handles empty list`() {
        whenever(i18n.t(eq("load_test.be_no_docker_files"))).thenReturn("(no-docker)")
        whenever(i18n.t(eq("load_test.be_no_route_files"))).thenReturn("(no-route)")
        val (docker, route) = runner.splitGitlabFiles(emptyList(), i18n)
        assertEquals("(no-docker)", docker)
        assertEquals("(no-route)", route)
    }
}
