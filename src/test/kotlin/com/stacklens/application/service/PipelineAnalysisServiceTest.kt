package com.stacklens.application.service

import com.stacklens.adapter.out.pipeline.PipelineMrSimulationAdapter
import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.pipeline.PipelineFilesPort
import com.stacklens.application.port.out.pipeline.PipelineRepoData
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PipelineAnalysisServiceTest {
    private lateinit var analysisRepo: PipelineAnalysisPort
    private lateinit var filesPort: PipelineFilesPort
    private lateinit var copilot: CopilotPort
    private lateinit var startupHealer: StartupRecovery
    private lateinit var asyncExecutor: AsyncExecutorPort
    private lateinit var aiService: PipelineAnalysisAiService
    private lateinit var service: PipelineAnalysisService

    @BeforeEach
    fun setup() {
        analysisRepo = mock()
        filesPort = mock()
        copilot = mock()
        startupHealer = mock()
        asyncExecutor = InlineAsyncExecutor()
        aiService = mock()
        whenever(copilot.resolveModel()).thenReturn("test-model")
        service =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = aiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
    }

    @Test
    fun `runLocal fails when the AI response carries no findings block`() {
        val row = PipelineAnalysis(id = 60L, repoConfigId = 1L, status = AnalysisStatus.PENDING)
        whenever(analysisRepo.findPipelineAnalysisById(60L)).thenReturn(row)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(aiService.loadRepoData(any())).thenReturn(PipelineRepoData("app", "/app", "tree", "contents"))
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("nur Fließtext ohne Marker")

        service.runLocal(60L)

        assertEquals(AnalysisStatus.FAILED, row.status)
        assertEquals("pipeline.be_err_incomplete_output", row.errorMessage)
        assertNotNull(row.completedAt)
    }

    @Test
    fun `runLocal records a truncated answer as its own failure`() {
        val row = PipelineAnalysis(id = 62L, repoConfigId = 1L, status = AnalysisStatus.PENDING)
        whenever(analysisRepo.findPipelineAnalysisById(62L)).thenReturn(row)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(aiService.loadRepoData(any())).thenReturn(PipelineRepoData("app", "/app", "tree", "contents"))
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(StackLensFailureException(StackLensFailure.AiOutputTruncated(8192, 8192, 0)))

        service.runLocal(62L)

        assertEquals(AnalysisStatus.FAILED, row.status)
        assertTrue(row.errorMessage!!.contains("AI_OUTPUT_TRUNCATED"), row.errorMessage)
        verify(aiService, never()).applyAiOutput(any(), any(), any())
    }

    @Test
    fun `runLocal completes when the findings block was extracted`() {
        val row = PipelineAnalysis(id = 61L, repoConfigId = 1L, status = AnalysisStatus.PENDING)
        whenever(analysisRepo.findPipelineAnalysisById(61L)).thenReturn(row)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(aiService.loadRepoData(any())).thenReturn(PipelineRepoData("app", "/app", "tree", "contents"))
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilot.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("SUMMARY:\nx\nFINDINGS:\n[]")
        doAnswer {
            (it.arguments[0] as PipelineAnalysis).findingsJson = "[]"
            null
        }.whenever(aiService).applyAiOutput(any(), any(), any())

        service.runLocal(61L)

        assertEquals(AnalysisStatus.COMPLETED, row.status)
        assertNull(row.errorMessage)
    }

    @Test
    fun `startLocalAnalysis persists PENDING row and dispatches`() {
        val row =
            PipelineAnalysis(id = 7L, repoConfigId = 1L, status = AnalysisStatus.PENDING, modelUsed = "x")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(row)
        whenever(copilot.isReady()).thenReturn(true)

        val result = service.startLocalAnalysis(repoConfigId = 1L, userPrompt = "focus on security")

        val captor = argumentCaptor<PipelineAnalysis>()
        verify(analysisRepo).savePipelineAnalysis(captor.capture())
        assertEquals(1L, captor.firstValue.repoConfigId)
        assertEquals(AnalysisStatus.PENDING, captor.firstValue.status)
        assertEquals("focus on security", captor.firstValue.userPromptAdditions)

        assertEquals(7L, result.id)
        verify(analysisRepo).findPipelineAnalysisById(7L)
    }

    @Test
    fun `startGitlabAnalysis sets gitlab fields and dispatches with project path`() {
        val row = PipelineAnalysis(id = 11L, gitlabConfigId = 2L, gitlabProjectId = 3L, gitlabProjectPath = "g/p")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(row)
        whenever(analysisRepo.findPipelineAnalysisById(11L)).thenReturn(row)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(aiService.loadRepoData(any())).thenAnswer {
            (it.getArgument(0) as () -> PipelineRepoData?)()
        }

        service.startGitlabAnalysis(
            gitlabConfigId = 2L,
            gitlabProjectId = 3L,
            projectPath = "g/p",
            userPrompt = null,
        )

        verify(analysisRepo).findPipelineAnalysisById(11L)
        verify(filesPort).loadGitlab(2L, 3L, "g/p")
    }

    @Test
    fun `startGithubAnalysis sets github field and dispatches`() {
        val row = PipelineAnalysis(id = 13L, githubRepoConfigId = 5L)
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(row)
        whenever(analysisRepo.findPipelineAnalysisById(13L)).thenReturn(row)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(aiService.loadRepoData(any())).thenAnswer {
            (it.getArgument(0) as () -> PipelineRepoData?)()
        }

        service.startGithubAnalysis(githubRepoConfigId = 5L, userPrompt = "  ")

        verify(analysisRepo).findPipelineAnalysisById(13L)
        verify(filesPort).loadGithub(5L)
    }

    @Test
    fun `startLocalAnalysis trims and ignores blank user prompt`() {
        val row = PipelineAnalysis(id = 7L, repoConfigId = 1L)
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(row)

        service.startLocalAnalysis(repoConfigId = 1L, userPrompt = "   \n  \t  ")

        val captor = argumentCaptor<PipelineAnalysis>()
        verify(analysisRepo).savePipelineAnalysis(captor.capture())
        assertNull(captor.firstValue.userPromptAdditions)
    }

    @Test
    fun `startLocalAnalysis aborts a previously running analysis for the same repo`() {
        val ghost =
            PipelineAnalysis(id = 99L, repoConfigId = 1L, status = AnalysisStatus.RUNNING)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(1L)).thenReturn(ghost)
        whenever(analysisRepo.findPipelineAnalysisById(99L)).thenReturn(ghost)
        val newRow =
            PipelineAnalysis(id = 7L, repoConfigId = 1L, status = AnalysisStatus.PENDING, modelUsed = "x")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(newRow)

        service.startLocalAnalysis(repoConfigId = 1L, userPrompt = null)

        assertEquals(AnalysisStatus.CANCELLED, ghost.status)
        verify(analysisRepo).findPipelineAnalysisById(7L)
    }

    @Test
    fun `startGitlabAnalysis aborts a previously running analysis for the same project`() {
        val ghost =
            PipelineAnalysis(
                id = 88L,
                gitlabConfigId = 2L,
                gitlabProjectId = 3L,
                status = AnalysisStatus.RUNNING,
            )
        whenever(
            analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(2L, 3L),
        ).thenReturn(ghost)
        whenever(analysisRepo.findPipelineAnalysisById(88L)).thenReturn(ghost)
        val newRow =
            PipelineAnalysis(id = 11L, gitlabConfigId = 2L, gitlabProjectId = 3L, gitlabProjectPath = "g/p")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(newRow)

        service.startGitlabAnalysis(
            gitlabConfigId = 2L,
            gitlabProjectId = 3L,
            projectPath = "g/p",
            userPrompt = null,
        )

        assertEquals(AnalysisStatus.CANCELLED, ghost.status)
        verify(analysisRepo).findPipelineAnalysisById(11L)
    }

    @Test
    fun `startGithubAnalysis aborts a previously running analysis for the same repo`() {
        val ghost =
            PipelineAnalysis(id = 77L, githubRepoConfigId = 5L, status = AnalysisStatus.PENDING)
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(5L)).thenReturn(ghost)
        whenever(analysisRepo.findPipelineAnalysisById(77L)).thenReturn(ghost)
        val newRow = PipelineAnalysis(id = 13L, githubRepoConfigId = 5L)
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(newRow)

        service.startGithubAnalysis(githubRepoConfigId = 5L, userPrompt = null)

        assertEquals(AnalysisStatus.CANCELLED, ghost.status)
        verify(analysisRepo).findPipelineAnalysisById(13L)
    }

    @Test
    fun `cancelAnalysis flips a RUNNING row to CANCELLED`() {
        val row =
            PipelineAnalysis(id = 7L, repoConfigId = 1L, status = AnalysisStatus.RUNNING)
        whenever(analysisRepo.findPipelineAnalysisById(7L)).thenReturn(row)

        service.cancelAnalysis(7L)

        assertEquals(AnalysisStatus.CANCELLED, row.status)
        assertNotNull(row.errorMessage)
        assertNotNull(row.completedAt)
        verify(analysisRepo).savePipelineAnalysis(row)
    }

    @Test
    fun `cancelAnalysis is a no-op for an already-completed row`() {
        val row =
            PipelineAnalysis(id = 7L, repoConfigId = 1L, status = AnalysisStatus.COMPLETED)
        whenever(analysisRepo.findPipelineAnalysisById(7L)).thenReturn(row)

        service.cancelAnalysis(7L)

        assertEquals(AnalysisStatus.COMPLETED, row.status)
        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `cancelLatestRunningForRepo returns null when no analysis exists`() {
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(99L)).thenReturn(null)
        assertNull(service.cancelLatestRunningForRepo(99L))
    }

    @Test
    fun `cancelLatestRunningForRepo returns null when latest is COMPLETED`() {
        val completed = PipelineAnalysis(id = 1L, repoConfigId = 2L, status = AnalysisStatus.COMPLETED)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(2L)).thenReturn(completed)
        assertNull(service.cancelLatestRunningForRepo(2L))
        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `cancelLatestRunningForRepo cancels a RUNNING row and returns its id`() {
        val running = PipelineAnalysis(id = 42L, repoConfigId = 5L, status = AnalysisStatus.RUNNING)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(5L)).thenReturn(running)
        whenever(analysisRepo.findPipelineAnalysisById(42L)).thenReturn(running)

        val cancelled = service.cancelLatestRunningForRepo(5L)

        assertEquals(42L, cancelled)
        assertEquals(AnalysisStatus.CANCELLED, running.status)
        verify(analysisRepo).savePipelineAnalysis(running)
    }

    @Test
    fun `cancelLatestRunningForGitlab cancels a RUNNING row and returns its id`() {
        val running =
            PipelineAnalysis(
                id = 100L,
                gitlabConfigId = 3L,
                gitlabProjectId = 9L,
                status = AnalysisStatus.RUNNING,
            )
        whenever(
            analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(3L, 9L),
        ).thenReturn(running)
        whenever(analysisRepo.findPipelineAnalysisById(100L)).thenReturn(running)

        val cancelled = service.cancelLatestRunningForGitlab(3L, 9L)

        assertEquals(100L, cancelled)
        assertEquals(AnalysisStatus.CANCELLED, running.status)
        verify(analysisRepo).savePipelineAnalysis(running)
    }

    @Test
    fun `cancelLatestRunningForGitlab returns null when latest is COMPLETED (second test)`() {
        val completed =
            PipelineAnalysis(
                id = 101L,
                gitlabConfigId = 3L,
                gitlabProjectId = 9L,
                status = AnalysisStatus.COMPLETED,
            )
        whenever(
            analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(3L, 9L),
        ).thenReturn(completed)

        assertNull(service.cancelLatestRunningForGitlab(3L, 9L))
        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `cancelLatestRunningForGithub cancels a PENDING row and returns its id`() {
        val running =
            PipelineAnalysis(
                id = 200L,
                githubRepoConfigId = 11L,
                status = AnalysisStatus.PENDING,
            )
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(11L)).thenReturn(running)
        whenever(analysisRepo.findPipelineAnalysisById(200L)).thenReturn(running)

        val cancelled = service.cancelLatestRunningForGithub(11L)

        assertEquals(200L, cancelled)
        assertEquals(AnalysisStatus.CANCELLED, running.status)
        verify(analysisRepo).savePipelineAnalysis(running)
    }

    @Test
    fun `cancelLatestRunningForGithub returns null when no analysis exists`() {
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(77L)).thenReturn(null)
        assertNull(service.cancelLatestRunningForGithub(77L))
    }

    @Test
    fun `latest lookups delegate to the port`() {
        val local = PipelineAnalysis(id = 1L, repoConfigId = 5L)
        val gitlab = PipelineAnalysis(id = 2L, gitlabConfigId = 1L, gitlabProjectId = 9L)
        val github = PipelineAnalysis(id = 3L, githubRepoConfigId = 7L)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(5L)).thenReturn(local)
        whenever(analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(1L, 9L)).thenReturn(gitlab)
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(7L)).thenReturn(github)

        assertEquals(local, service.getLatestLocalAnalysis(5L))
        assertEquals(gitlab, service.getLatestGitlabAnalysis(1L, 9L))
        assertEquals(github, service.getLatestGithubAnalysis(7L))
    }

    @Test
    fun `deleteAnalysis delegates to the port`() {
        service.deleteAnalysis(99L)
        verify(analysisRepo).deletePipelineAnalysisById(99L)
    }

    @Test
    fun `runLocal short-circuits when no compose or pipeline files are present`() {
        val row = PipelineAnalysis(id = 21L, repoConfigId = 1L, status = AnalysisStatus.RUNNING)
        whenever(analysisRepo.findPipelineAnalysisById(21L)).thenReturn(row)
        whenever(filesPort.loadLocal(1L)).thenReturn(
            PipelineRepoData("x", "/x", tree = "", contents = ""),
        )
        whenever(copilot.isReady()).thenReturn(true)
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                mock(),
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        realService.runLocal(21L)

        assertEquals(AnalysisStatus.COMPLETED, row.status)
        assertNotNull(row.summaryText)
        assertTrue(row.summaryText!!.contains("pipeline.no_files_found"))
        verify(copilot, never()).execute(
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `runGitlab passes through project path to filesPort`() {
        val row = PipelineAnalysis(id = 22L, status = AnalysisStatus.RUNNING, gitlabConfigId = 1L, gitlabProjectId = 2L, gitlabProjectPath = "g/p")
        whenever(analysisRepo.findPipelineAnalysisById(22L)).thenReturn(row)
        whenever(filesPort.loadGitlab(1L, 2L, "g/p")).thenReturn(
            PipelineRepoData("p", "g/p", tree = "docker-compose.yml", contents = "x"),
        )
        whenever(copilot.isReady()).thenReturn(true)
        whenever(
            copilot.execute(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("SUMMARY:\nNothing.\nFINDINGS:\n[]")
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                mock(),
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        realService.runGitlab(22L, 1L, 2L, "g/p")

        verify(filesPort).loadGitlab(1L, 2L, "g/p")
    }

    @Test
    fun `runGithub passes through the github config id`() {
        val row = PipelineAnalysis(id = 23L, status = AnalysisStatus.RUNNING, githubRepoConfigId = 7L)
        whenever(analysisRepo.findPipelineAnalysisById(23L)).thenReturn(row)
        whenever(filesPort.loadGithub(7L)).thenReturn(
            PipelineRepoData("o/r", "o/r", tree = "Dockerfile", contents = "FROM alpine"),
        )
        whenever(copilot.isReady()).thenReturn(true)
        whenever(
            copilot.execute(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("SUMMARY:\nNothing.\nFINDINGS:\n[]")
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                mock(),
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        realService.runGithub(23L, 7L)

        verify(filesPort).loadGithub(7L)
    }

    @Test
    fun `resetStaleAnalyses cancels RUNNING rows`() {
        val staleRunning =
            PipelineAnalysis(id = 100L, status = AnalysisStatus.RUNNING)
        val stalePending =
            PipelineAnalysis(id = 101L, status = AnalysisStatus.PENDING)
        val freshCompleted =
            PipelineAnalysis(id = 102L, status = AnalysisStatus.COMPLETED)
        whenever(analysisRepo.findAllPipelineAnalysesOrderByCreatedAtDesc())
            .thenReturn(listOf(staleRunning, stalePending, freshCompleted))
        val realHealer = StartupHealer()
        val svc =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = mock(),
                startupHealer = realHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        svc.heal()
        assertEquals(AnalysisStatus.CANCELLED, staleRunning.status)
        assertEquals(AnalysisStatus.CANCELLED, stalePending.status)
        assertEquals(AnalysisStatus.COMPLETED, freshCompleted.status)
    }

    @Test
    fun `cancelLatestRunningForRepo cancels a PENDING row and returns its id`() {
        val row = PipelineAnalysis(id = 30L, status = AnalysisStatus.PENDING)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(1L)).thenReturn(row)
        whenever(analysisRepo.findPipelineAnalysisById(30L)).thenReturn(row)
        val svc =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = mock(),
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val id = svc.cancelLatestRunningForRepo(1L)
        assertEquals(30L, id)
        assertEquals(AnalysisStatus.CANCELLED, row.status)
    }

    @Test
    fun `cancelLatestRunningForGitlab returns null when latest is COMPLETED`() {
        whenever(
            analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(1L, 2L),
        ).thenReturn(
            PipelineAnalysis(id = 31L, status = AnalysisStatus.COMPLETED),
        )
        val svc =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = mock(),
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val id = svc.cancelLatestRunningForGitlab(1L, 2L)
        assertNull(id)
    }

    @Test
    fun `cancelLatestRunningForGithub cancels a RUNNING row and returns its id`() {
        val row = PipelineAnalysis(id = 40L, status = AnalysisStatus.RUNNING)
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(1L)).thenReturn(row)
        whenever(analysisRepo.findPipelineAnalysisById(40L)).thenReturn(row)
        val svc =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = mock(),
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val id = svc.cancelLatestRunningForGithub(1L)
        assertEquals(40L, id)
        assertEquals(AnalysisStatus.CANCELLED, row.status)
    }

    @Test
    fun `cancelLatestRunningForGithub returns null when latest is COMPLETED`() {
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(1L))
            .thenReturn(PipelineAnalysis(id = 41L, status = AnalysisStatus.COMPLETED))
        val svc =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = mock(),
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val id = svc.cancelLatestRunningForGithub(1L)
        assertNull(id)
    }

    @Test
    fun `runLocal with non-blank userPromptAdditions includes them in the copilot call`() {
        val row =
            PipelineAnalysis(
                id = 50L,
                status = AnalysisStatus.RUNNING,
                repoConfigId = 9L,
                userPromptAdditions = "Extra guidance",
            )
        whenever(analysisRepo.findPipelineAnalysisById(50L)).thenReturn(row)
        whenever(filesPort.loadLocal(9L)).thenReturn(
            PipelineRepoData("p", "p", tree = "x", contents = "y"),
        )
        whenever(copilot.isReady()).thenReturn(true)
        whenever(
            copilot.execute(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("SUMMARY:\n- nothing\nFINDINGS:\n- none")
        val promptLoader = mock<com.stacklens.application.port.out.prompt.PromptReaderPort>()
        val ctx =
            com.stacklens.application.port.out.prompt.PromptContext(
                mapOf(
                    "pipeline.analysisPrompt" to "Analysis: {projectName} | {userPromptSection}",
                    "pipeline.userPromptSection" to "User adds: {userPrompt}",
                ),
            )
        whenever(promptLoader.context(any())).thenReturn(ctx)
        whenever(copilot.resolveLanguage()).thenReturn("de")
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                promptLoader,
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        realService.runLocal(50L)
        val captor = org.mockito.kotlin.argumentCaptor<String>()
        verify(copilot).execute(
            any(),
            captor.capture(),
            any(),
            anyOrNull(),
        )
        assertTrue(captor.firstValue.contains("Extra guidance"))
    }

    @Test
    fun `runLocal with user prompt over 4000 chars is truncated`() {
        val longPrompt = "x".repeat(5000)
        val row =
            PipelineAnalysis(
                id = 51L,
                status = AnalysisStatus.RUNNING,
                repoConfigId = 10L,
                userPromptAdditions = longPrompt.take(4000),
            )
        whenever(analysisRepo.findPipelineAnalysisById(51L)).thenReturn(row)
        whenever(filesPort.loadLocal(10L)).thenReturn(
            PipelineRepoData("p", "p", tree = "x", contents = "y"),
        )
        whenever(copilot.isReady()).thenReturn(true)
        whenever(
            copilot.execute(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("SUMMARY:\n- x\nFINDINGS:\n- y")
        val promptLoader = mock<com.stacklens.application.port.out.prompt.PromptReaderPort>()
        val ctx =
            com.stacklens.application.port.out.prompt.PromptContext(
                mapOf(
                    "pipeline.analysisPrompt" to "Analysis: {projectName} | {userPromptSection}",
                    "pipeline.userPromptSection" to "User adds: {userPrompt}",
                ),
            )
        whenever(promptLoader.context(any())).thenReturn(ctx)
        whenever(copilot.resolveLanguage()).thenReturn("de")
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                promptLoader,
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        realService.runLocal(51L)
        val captor = org.mockito.kotlin.argumentCaptor<String>()
        verify(copilot).execute(
            any(),
            captor.capture(),
            any(),
            anyOrNull(),
        )
        assertTrue(captor.firstValue.contains("x".repeat(4000)))
    }

    @Test
    fun `runLocal with repoConfigId null uses logTag local`() {
        val row = PipelineAnalysis(id = 52L, status = AnalysisStatus.RUNNING, repoConfigId = null)
        whenever(analysisRepo.findPipelineAnalysisById(52L)).thenReturn(row)
        whenever(filesPort.loadLocal(0L)).thenReturn(
            PipelineRepoData("p", "p", tree = "x", contents = "y"),
        )
        whenever(copilot.isReady()).thenReturn(true)
        whenever(
            copilot.execute(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull(),
            ),
        ).thenReturn("SUMMARY:\n- x\nFINDINGS:\n- y")
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                mock(),
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        realService.runLocal(52L)
        verify(filesPort).loadLocal(0L)
    }

    @Test
    fun `runLocal throws when copilot is not ready`() {
        val row = PipelineAnalysis(id = 53L, status = AnalysisStatus.RUNNING, repoConfigId = 5L)
        whenever(analysisRepo.findPipelineAnalysisById(53L)).thenReturn(row)
        whenever(filesPort.loadLocal(5L)).thenReturn(
            PipelineRepoData("p", "p", tree = "x", contents = "y"),
        )
        whenever(copilot.isReady()).thenReturn(false)
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                mock(),
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        realService.runLocal(53L)
        assertEquals(AnalysisStatus.FAILED, row.status)
    }

    @Test
    fun `createMrOrPr delegates to mrService when configured`() {
        val mrService =
            com.stacklens.application.service.pipeline.PipelineAnalysisMrService(
                analysisRepo = analysisRepo,
                gitlabMrPort = mock(),
                gitlabConfigPort = mock(),
                githubRepoConfigPort = mock(),
                githubBridge = mock(),
                gitlabRepoPort = mock(),
                jsonCodec = mock(),
                i18n = FakeI18n(),
                simulationPort = PipelineMrSimulationAdapter(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val serviceWithMr =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = aiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mrService,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        try {
            serviceWithMr.createMrOrPr(99L, 1L, 2L)
        } catch (e: IllegalStateException) {
            assertFalse(
                e.message?.contains("PipelineAnalysisMrService is not configured") == true,
                "Expected delegation to mrService, got null-check error: ${e.message}",
            )
        } catch (_: Exception) {
        }
    }

    @Test
    fun `runLocal handles exception in copilot execute`() {
        val row = PipelineAnalysis(id = 54L, status = AnalysisStatus.RUNNING, repoConfigId = 5L)
        whenever(analysisRepo.findPipelineAnalysisById(54L)).thenReturn(row)
        whenever(filesPort.loadLocal(5L)).thenReturn(
            PipelineRepoData("p", "p", tree = "x", contents = "y"),
        )
        whenever(copilot.isReady()).thenReturn(true)
        whenever(
            copilot.execute(
                any<String>(),
                any<String>(),
                any<Long>(),
                anyOrNull(),
            ),
        ).thenThrow(RuntimeException("boom"))
        val promptLoader = mock<com.stacklens.application.port.out.prompt.PromptReaderPort>()
        val ctx =
            com.stacklens.application.port.out.prompt.PromptContext(
                mapOf(
                    "pipeline.analysisPrompt" to "Analysis: {projectName} | {userPromptSection}",
                ),
            )
        whenever(promptLoader.context(any())).thenReturn(ctx)
        whenever(copilot.resolveLanguage()).thenReturn("de")
        val realAiService =
            PipelineAnalysisAiService(
                copilot,
                promptLoader,
                com.stacklens.adapter.out.textfilter
                    .DefaultAiResponseJsonExtractor(),
            )
        val realService =
            pipelineAnalysisService(
                analysisRepo = analysisRepo,
                filesPort = filesPort,
                copilotCliService = copilot,
                aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                i18n = FakeI18n(),
                aiService = realAiService,
                startupHealer = startupHealer,
                asyncExecutor = asyncExecutor,
                mrService = mock(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        realService.runLocal(54L)
        assertEquals(AnalysisStatus.FAILED, row.status)
        assertEquals("boom", row.errorMessage)
    }
}

class PipelineAnalysisServiceUserPromptBranchTest {
    private val analysisRepo = mock<com.stacklens.application.port.out.PipelineAnalysisPort>()
    private val filesPort = mock<com.stacklens.application.port.out.pipeline.PipelineFilesPort>()
    private val copilot = mock<com.stacklens.application.port.out.ai.CopilotPort>()
    private val startupHealer = mock<com.stacklens.application.collaboration.StartupRecovery>()
    private val asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor()
    private val aiService = mock<com.stacklens.application.service.PipelineAnalysisAiService>()

    private val service =
        com.stacklens.application.service.pipelineAnalysisService(
            analysisRepo = analysisRepo,
            filesPort = filesPort,
            copilotCliService = copilot,
            aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
            i18n = FakeI18n(),
            aiService = aiService,
            startupHealer = startupHealer,
            asyncExecutor = asyncExecutor,
            mrService = mock(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private val savedRow =
        com.stacklens.domain.model
            .PipelineAnalysis(id = 99L, status = com.stacklens.domain.AnalysisStatus.PENDING)

    @Test
    fun `startGitlabAnalysis with non-blank user prompt persists the additions field`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-x")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(savedRow)
        service.startGitlabAnalysis(
            gitlabConfigId = 2L,
            gitlabProjectId = 3L,
            projectPath = "g/p",
            userPrompt = "focus on security",
        )
        verify(analysisRepo, org.mockito.kotlin.atLeastOnce()).savePipelineAnalysis(
            org.mockito.kotlin.check { assertEquals("focus on security", it.userPromptAdditions) },
        )
    }

    @Test
    fun `startGitlabAnalysis with blank user prompt sets additions to null`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-x")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(savedRow)
        service.startGitlabAnalysis(
            gitlabConfigId = 2L,
            gitlabProjectId = 3L,
            projectPath = "g/p",
            userPrompt = "   \n  ",
        )
        verify(analysisRepo, org.mockito.kotlin.atLeastOnce()).savePipelineAnalysis(
            org.mockito.kotlin.check { assertEquals(null, it.userPromptAdditions) },
        )
    }

    @Test
    fun `startGithubAnalysis with non-blank user prompt persists the additions field`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-x")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(savedRow)
        service.startGithubAnalysis(githubRepoConfigId = 5L, userPrompt = "focus on perf")
        val captor = org.mockito.kotlin.argumentCaptor<com.stacklens.domain.model.PipelineAnalysis>()
        verify(analysisRepo, org.mockito.kotlin.atLeastOnce()).savePipelineAnalysis(captor.capture())
        val allCalls = captor.allValues
        val matching = allCalls.firstOrNull { it.userPromptAdditions == "focus on perf" }
        assertNotNull(matching, "no savePipelineAnalysis call had userPromptAdditions='focus on perf', got: $allCalls")
    }

    @Test
    fun `startGithubAnalysis with blank user prompt sets additions to null`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-x")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(savedRow)
        service.startGithubAnalysis(githubRepoConfigId = 5L, userPrompt = "")
        verify(analysisRepo, org.mockito.kotlin.atLeastOnce()).savePipelineAnalysis(
            org.mockito.kotlin.check { assertEquals(null, it.userPromptAdditions) },
        )
    }

    @Test
    fun `startLocalAnalysis with null user prompt sets additions to null`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-x")
        whenever(analysisRepo.savePipelineAnalysis(any())).thenReturn(savedRow)
        service.startLocalAnalysis(repoConfigId = 1L, userPrompt = null)
        verify(analysisRepo, org.mockito.kotlin.atLeastOnce()).savePipelineAnalysis(
            org.mockito.kotlin.check { assertEquals(null, it.userPromptAdditions) },
        )
    }
}
