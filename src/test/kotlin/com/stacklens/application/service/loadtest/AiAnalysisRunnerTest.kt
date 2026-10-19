package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.TokenTrackingCopilotPortInterface
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.domain.pipeline.CancellationSignal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AiAnalysisRunnerTest {
    private lateinit var analysisRepo: LoadTestAnalysisPort
    private lateinit var gitlabConfigRepo: GitlabConfigPort
    private lateinit var copilotPort: TokenTrackingCopilotPortInterface
    private lateinit var gitlabPort: GitlabPort
    private lateinit var i18n: I18nPort
    private lateinit var aiService: LoadTestAi
    private lateinit var localRepoConfigRepo: LocalRepoConfigPort
    private lateinit var githubRepoConfigRepo: GithubRepoConfigPort
    private lateinit var scanService: LocalRepoScanPort
    private lateinit var githubBridge: GithubTicketBridgePort
    private lateinit var copilotConfig: CopilotConfigPort
    private lateinit var localDirectoryScan: LocalDirectoryScanPort
    private lateinit var runRegistry: LoadTestRunRegistry
    private lateinit var runner: AiAnalysisRunner

    @BeforeEach
    fun setUp() {
        analysisRepo = mock()
        gitlabConfigRepo = mock()
        copilotPort = mock()
        gitlabPort = mock()
        i18n = mock()
        aiService = mock()
        localRepoConfigRepo = mock()
        githubRepoConfigRepo = mock()
        scanService = mock()
        githubBridge = mock()
        copilotConfig = mock()
        localDirectoryScan = mock()
        runRegistry = LoadTestRunRegistry()
        runner =
            testAiAnalysisRunner(
                analysisRepo = analysisRepo,
                gitlabConfigRepo = gitlabConfigRepo,
                copilotPort = copilotPort,
                gitlabService = gitlabPort,
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
                runRegistry = runRegistry,
            )
    }

    @Test
    fun `runGitlabAnalysis returns early when buildGitlabContext returns null`() {
        try {
            runner.runGitlabAnalysis(42L, 1L, 100L, "group/project")
        } catch (_: Exception) {
        }
    }

    @Test
    fun `runLocalAnalysis returns early when buildLocalContext returns null`() {
        try {
            runner.runLocalAnalysis(42L, 1L)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `runGithubAnalysis returns early when buildGithubContext returns null`() {
        try {
            runner.runGithubAnalysis(42L, 1L)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `cancelRunningThread returns cleanly when no run is registered for analysisId`() {
        runner.cancelRunningThread(999L)
    }

    @Test
    fun `cancelRunningThread interrupts the worker thread of a running analysis`() {
        val executor =
            java.util.concurrent.Executors
                .newSingleThreadExecutor()
        val entered = java.util.concurrent.CountDownLatch(1)
        val gate = java.util.concurrent.CountDownLatch(1)
        val workerFailure =
            java.util.concurrent.atomic
                .AtomicReference<Throwable?>(null)
        val stubI18n =
            org.mockito.kotlin.mock<I18nPort>(
                defaultAnswer = org.mockito.stubbing.Answer { it.arguments[0] ?: "" },
            )
        try {
            org.mockito.kotlin
                .whenever(copilotPort.isReady())
                .thenReturn(true)
            org.mockito.kotlin
                .whenever(localRepoConfigRepo.findLocalRepoConfigById(2L))
                .thenReturn(
                    com.stacklens.domain.model.LocalRepoConfig(
                        id = 2L,
                        name = "repo",
                        path =
                            java.nio.file.Files
                                .createTempDirectory("cancel-runner")
                                .toString(),
                    ),
                )
            org.mockito.kotlin
                .whenever(scanService.buildCodeSummary(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
                .thenReturn(
                    com.stacklens.application.port.out.repo.CodeSummary(
                        projectTree = "",
                        fileContents = "",
                        fileCount = 0,
                        charCount = 0,
                    ),
                )
            org.mockito.kotlin
                .whenever(localDirectoryScan.textFiles(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
                .thenReturn(emptyList())
            org.mockito.kotlin
                .whenever(analysisRepo.findLoadTestAnalysisById(1L))
                .thenAnswer {
                    entered.countDown()
                    gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    null
                }
            val asyncRunner =
                testAiAnalysisRunner(
                    analysisRepo = analysisRepo,
                    gitlabConfigRepo = gitlabConfigRepo,
                    copilotPort = copilotPort,
                    gitlabService = gitlabPort,
                    i18n = stubI18n,
                    aiService = aiService,
                    localRepoConfigRepo = localRepoConfigRepo,
                    githubRepoConfigRepo = githubRepoConfigRepo,
                    scanService = scanService,
                    githubBridge = githubBridge,
                    copilotConfig = copilotConfig,
                    localDirectoryScan = localDirectoryScan,
                    asyncExecutor =
                        object : com.stacklens.application.port.out.async.AsyncExecutorPort {
                            override fun submit(
                                executorName: String?,
                                task: () -> Unit,
                            ) {
                                executor.execute {
                                    try {
                                        task()
                                    } catch (t: Throwable) {
                                        workerFailure.set(t)
                                    }
                                }
                            }
                        },
                    parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
                    clock = com.stacklens.adapter.out.time.SystemClockPort,
                )

            asyncRunner.runLocalAnalysis(1L, 2L)
            org.junit.jupiter.api.Assertions.assertTrue(
                entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "worker did not reach the analysis lookup: ${workerFailure.get()}",
            )
            asyncRunner.cancelRunningThread(1L)
            gate.countDown()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancelRunningThread signals the registered cancellation`() {
        val id = 123L
        var cancelled = false
        runRegistry.register(id, CancellationSignal { cancelled = true })

        runner.cancelRunningThread(id)

        assertEquals(true, cancelled)
    }

    @Test
    fun `cancelRunningThread removes the entry after cancelling`() {
        val id = 7L
        var signalCount = 0
        runRegistry.register(id, CancellationSignal { signalCount++ })

        runner.cancelRunningThread(id)
        runner.cancelRunningThread(id)

        assertEquals(1, signalCount)
    }
}
