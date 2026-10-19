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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnalysisRunnerResidualBranchCoverageTest {
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

    @TempDir
    lateinit var tempDir: Path

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

    private fun analysis(
        id: Long,
        status: String = AnalysisStatus.PENDING,
    ) = LoadTestAnalysis(id = id, status = status)

    private fun stubHappyPipeline() {
        whenever(i18n.t(any())).thenReturn("label")
        whenever(i18n.translateError(any())).thenReturn("translated")
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("en")
        whenever(copilotConfig.loadTestAnalysisTimeoutSeconds).thenReturn(60L)
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any())).thenReturn("prompt")
        whenever(aiService.buildEnvDetectPrompt(any(), any(), any(), any())).thenReturn("env prompt")
        whenever(aiService.buildK6Prompt(any(), any(), any(), any(), any())).thenReturn("k6 prompt")
        whenever(aiService.extractSection(any(), any(), any())).thenReturn("summary")
        whenever(aiService.extractJsonBlock(any(), any(), any())).thenReturn("[]")
        whenever(aiService.extractJsonArray(any())).thenReturn("[]")
        whenever(aiService.extractCodeBlock(any(), any(), any())).thenReturn("// k6")
    }

    private fun stubCopilotReady() {
        stubHappyPipeline()
        whenever(copilotPort.isReady()).thenReturn(true)
    }

    private fun stubGitlabConfig() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(
            GitlabConfig(id = 1L, name = "test", baseUrl = "http://gl.example.com", accessToken = "tok"),
        )
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(listOf("README.md" to "hello"))
    }

    private fun stubErrorLookup(
        first: LoadTestAnalysis?,
        second: LoadTestAnalysis?,
    ) {
        whenever(analysisRepo.findLoadTestAnalysisById(any())).thenReturn(first, second)
    }

    private fun executeThrows(throwable: Throwable) {
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenAnswer { throw throwable }
    }

    @Test
    fun `pipeline reports error when copilot turns unavailable after context build`() {
        stubCopilotReady()
        stubGitlabConfig()
        stubErrorLookup(analysis(1L), null)
        whenever(copilotPort.isReady()).thenReturn(true, false)
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, times(2)).findLoadTestAnalysisById(1L)
        verify(copilotPort, never()).setModelOverride(any())
    }

    @Test
    fun `gitlab file cache evicts eldest entry beyond sixty-four projects`() {
        stubCopilotReady()
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(
            GitlabConfig(id = 1L, name = "test", baseUrl = "http://gl.example.com", accessToken = "tok"),
        )
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any())).thenReturn(listOf("README.md" to "hello"))
        whenever(analysisRepo.findLoadTestAnalysisById(any())).thenReturn(null)
        repeat(70) { index -> runner.runGitlabAnalysis(1L, 1L, index.toLong(), "group/project-$index") }
        runner.runGitlabAnalysis(1L, 1L, 0L, "group/project-0")
        verify(gitlabService, times(71)).fetchProjectFiles(any(), any(), any(), any())
    }

    @Test
    fun `error handler skips update on direct interrupt`() {
        stubCopilotReady()
        stubGitlabConfig()
        stubErrorLookup(analysis(1L), analysis(1L))
        executeThrows(InterruptedException("interrupted"))
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        assertTrue(Thread.interrupted())
        verify(i18n, never()).translateError(any())
        verify(analysisRepo, times(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `error handler skips update on interrupting cause`() {
        stubCopilotReady()
        stubGitlabConfig()
        stubErrorLookup(analysis(1L), analysis(1L))
        executeThrows(RuntimeException(InterruptedException("interrupted")))
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        assertTrue(Thread.interrupted())
        verify(i18n, never()).translateError(any())
    }

    @Test
    fun `error handler falls back to toString when message is null`() {
        stubCopilotReady()
        stubGitlabConfig()
        stubErrorLookup(analysis(1L), analysis(1L))
        executeThrows(RuntimeException())
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, times(2)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `error handler skips update when analysis disappeared`() {
        stubCopilotReady()
        stubGitlabConfig()
        stubErrorLookup(analysis(1L), null)
        executeThrows(RuntimeException("Copilot CLI Timeout (mock)"))
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, times(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `error handler skips update when analysis already completed`() {
        stubCopilotReady()
        stubGitlabConfig()
        stubErrorLookup(analysis(1L), analysis(1L, status = AnalysisStatus.COMPLETED))
        executeThrows(RuntimeException("boom"))
        runner.runGitlabAnalysis(1L, 1L, 42L, "group/project")
        verify(analysisRepo, times(1)).saveLoadTestAnalysis(any())
    }

    @Test
    fun `local analysis builds context from repository folder`() {
        stubCopilotReady()
        Files.writeString(tempDir.resolve("server.js"), "const a = 1")
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM alpine")
        whenever(scanService.buildCodeSummary(any(), any(), any())).thenReturn(CodeSummary("tree", "contents", 2, 20))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(
            LocalRepoConfig(id = 7L, name = "local", path = tempDir.toString()),
        )
        whenever(analysisRepo.findLoadTestAnalysisById(any())).thenReturn(null)
        runner.runLocalAnalysis(1L, 7L)
        verify(copilotPort, times(1)).isReady()
    }

    @Test
    fun `github analysis builds context from bridge files`() {
        stubCopilotReady()
        whenever(githubRepoConfigRepo.findGithubRepoConfigById(9L)).thenReturn(
            GithubRepoConfig(id = 9L, name = "widget", owner = "acme", repo = "widget"),
        )
        whenever(githubBridge.fetchRepoContext(any())).thenReturn(
            JiraRepoContext(
                repoName = "widget",
                projectId = 3L,
                files = listOf(JiraRepoFile("src/index.js", "console.log(1)")),
            ),
        )
        whenever(analysisRepo.findLoadTestAnalysisById(any())).thenReturn(null)
        runner.runGithubAnalysis(1L, 9L)
        verify(githubBridge, times(1)).fetchRepoContext(any())
        verify(githubRepoConfigRepo, times(1)).findGithubRepoConfigById(9L)
    }
}
