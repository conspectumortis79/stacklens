package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.pipeline.PipelineFilesPort
import com.stacklens.application.port.out.pipeline.PipelineRepoData
import com.stacklens.application.service.pipeline.PipelineAnalysisMrService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PipelineAnalysisServiceCancellationTest {
    private val analysisRepo = mock<PipelineAnalysisPort>()

    private val filesPort = mock<PipelineFilesPort>()

    private val copilot = mock<CopilotPort>()

    private val aiUsageContext = mock<AiUsageContextPort>()

    private val i18n: I18nPort = FakeI18n()

    private val aiService = mock<PipelineAnalysisAiService>()

    private val startupHealer = mock<StartupRecovery>()

    private val asyncExecutor: AsyncExecutorPort = InlineAsyncExecutor()

    private val mrService = mock<PipelineAnalysisMrService>()

    private val clock = FixedClockPort(1_750_000_000_000L)

    private fun service() =
        pipelineAnalysisService(
            analysisRepo = analysisRepo,
            filesPort = filesPort,
            copilotCliService = copilot,
            aiUsageContext = aiUsageContext,
            i18n = i18n,
            aiService = aiService,
            startupHealer = startupHealer,
            asyncExecutor = asyncExecutor,
            mrService = mrService,
            clock = clock,
        )

    private fun analysis(
        id: Long,
        status: String = AnalysisStatus.PENDING,
    ) = PipelineAnalysis(
        id = id,
        repoConfigId = 3L,
        status = status,
    )

    private fun stubRunPath(
        target: PipelineAnalysisService,
        current: PipelineAnalysis,
        duringExecution: () -> Unit = {},
    ) {
        whenever(analysisRepo.findPipelineAnalysisById(current.id)).thenReturn(current)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("modell")
        whenever(aiService.loadRepoData(any())).thenReturn(PipelineRepoData("app", "/repo", "baum", "inhalte"))
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenAnswer {
            duringExecution()
            "ki ausgabe"
        }
    }

    @Test
    fun `the merge request creation is delegated when the service is present`() {
        val result = PipelineMrResult(provider = "gitlab", url = "https://gitlab/mr/1", kind = "mr", branch = "feature")
        whenever(mrService.createMrOrPr(9L, 2L, 4L)).thenReturn(result)

        assertEquals(result, service().createMrOrPr(9L, 2L, 4L))
    }

    @Test
    fun `cancelling an unknown analysis only records the identifier`() {
        whenever(analysisRepo.findPipelineAnalysisById(77L)).thenReturn(null)

        service().cancelAnalysis(77L)

        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `a running analysis is cancelled through the repository lookup`() {
        val running = analysis(50L, AnalysisStatus.RUNNING)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(3L)).thenReturn(running)
        whenever(analysisRepo.findPipelineAnalysisById(50L)).thenReturn(running)

        assertEquals(50L, service().cancelLatestRunningForRepo(3L))
        assertEquals(AnalysisStatus.CANCELLED, running.status)
    }

    @Test
    fun `a pending analysis is cancelled through the local repository lookup`() {
        val pending = analysis(54L, AnalysisStatus.PENDING)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(6L)).thenReturn(pending)
        whenever(analysisRepo.findPipelineAnalysisById(54L)).thenReturn(pending)

        assertEquals(54L, service().cancelLatestRunningForRepo(6L))
        assertEquals(AnalysisStatus.CANCELLED, pending.status)
    }

    @Test
    fun `a pending analysis is cancelled through the repository lookup`() {
        val pending = analysis(51L, AnalysisStatus.PENDING)
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(4L)).thenReturn(pending)
        whenever(analysisRepo.findPipelineAnalysisById(51L)).thenReturn(pending)

        assertEquals(51L, service().cancelLatestRunningForGithub(4L))
        assertEquals(AnalysisStatus.CANCELLED, pending.status)
    }

    @Test
    fun `a vanished record during the run is not treated as cancelled`() {
        val current = analysis(52L)
        whenever(analysisRepo.findPipelineAnalysisById(52L)).thenReturn(current, current, null)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("modell")
        whenever(aiService.loadRepoData(any())).thenReturn(PipelineRepoData("app", "/repo", "baum", "inhalte"))
        whenever(aiService.buildAnalysisPrompt(any(), any(), any(), any(), any(), anyOrNull())).thenReturn("prompt")
        whenever(copilot.execute(any(), any(), anyOrNull(), any())).thenReturn("ki ausgabe")
        doAnswer {
            (it.arguments[0] as PipelineAnalysis).findingsJson = "[]"
            null
        }.whenever(aiService).applyAiOutput(any(), any(), any())

        service().runLocal(52L)

        assertEquals(AnalysisStatus.COMPLETED, current.status)
        verify(aiService).applyAiOutput(any(), any(), any())
    }

    @Test
    fun `a lookup failure during the error save falls back to the in memory record`() {
        val current = analysis(53L)
        whenever(analysisRepo.findPipelineAnalysisById(53L)).thenReturn(current, current).thenThrow(RuntimeException("datenbank weg"))
        whenever(copilot.isReady()).thenThrow(RuntimeException("copilot weg"))

        service().runLocal(53L)

        assertEquals(AnalysisStatus.FAILED, current.status)
        assertEquals("i18n:copilot weg", current.errorMessage)
    }

    @Test
    fun `a finished analysis is never cancelled again by the repo lookup`() {
        val done = analysis(5L, AnalysisStatus.COMPLETED)
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(3L)).thenReturn(done)

        assertNull(service().cancelLatestRunningForRepo(3L))
        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `a finished gitlab analysis is never cancelled again`() {
        whenever(analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(2L, 4L))
            .thenReturn(analysis(6L, AnalysisStatus.FAILED))

        assertNull(service().cancelLatestRunningForGitlab(2L, 4L))
    }

    @Test
    fun `a finished github analysis is never cancelled again`() {
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(8L))
            .thenReturn(analysis(7L, AnalysisStatus.CANCELLED))

        assertNull(service().cancelLatestRunningForGithub(8L))
    }

    @Test
    fun `a local run without a stored analysis stops immediately`() {
        whenever(analysisRepo.findPipelineAnalysisById(41L)).thenReturn(null)

        service().runLocal(41L)

        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `a gitlab run without a stored analysis stops immediately`() {
        whenever(analysisRepo.findPipelineAnalysisById(42L)).thenReturn(null)

        service().runGitlab(42L, 1L, 2L, "gruppe/projekt")

        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `a github run without a stored analysis stops immediately`() {
        whenever(analysisRepo.findPipelineAnalysisById(43L)).thenReturn(null)

        service().runGithub(43L, 9L)

        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `a local run without repository data completes with a hint`() {
        val current = analysis(44L)
        whenever(analysisRepo.findPipelineAnalysisById(44L)).thenReturn(current)
        whenever(copilot.isReady()).thenReturn(true)
        whenever(copilot.resolveModel()).thenReturn("modell")
        whenever(aiService.loadRepoData(any())).thenReturn(null)

        service().runLocal(44L)

        assertEquals(AnalysisStatus.COMPLETED, current.status)
        assertEquals("i18n:pipeline.no_files_found", current.summaryText)
    }

    @Test
    fun `a cancellation raised during the ai run skips the completed save`() {
        val target = service()
        val current = analysis(45L)
        stubRunPath(target, current, duringExecution = { target.cancelAnalysis(45L) })

        target.runLocal(45L)

        assertEquals(AnalysisStatus.CANCELLED, current.status)
        verify(aiService, never()).applyAiOutput(any(), any(), any())
    }

    @Test
    fun `a cancelled record in the repository skips the completed save`() {
        val current = analysis(46L)
        val target = service()
        stubRunPath(target, current, duringExecution = { current.status = AnalysisStatus.CANCELLED })

        target.runLocal(46L)

        assertEquals(AnalysisStatus.CANCELLED, current.status)
        verify(aiService, never()).applyAiOutput(any(), any(), any())
    }

    @Test
    fun `a failing repository lookup during the error save keeps the in memory record`() {
        val current = analysis(47L)
        whenever(analysisRepo.findPipelineAnalysisById(47L)).thenReturn(current)
        whenever(copilot.isReady()).thenThrow(RuntimeException("copilot nicht erreichbar"))

        service().runLocal(47L)

        assertEquals(AnalysisStatus.FAILED, current.status)
        assertEquals("i18n:copilot nicht erreichbar", current.errorMessage)
        assertEquals(clock.localDateTime(), current.completedAt)
    }

    @Test
    fun `an error save never overwrites a cancelled record`() {
        val current = analysis(48L)
        val cancelled = analysis(48L, AnalysisStatus.CANCELLED)
        whenever(analysisRepo.findPipelineAnalysisById(48L)).thenReturn(current, current, cancelled)
        whenever(copilot.isReady()).thenThrow(RuntimeException("kopierfehler"))

        service().runLocal(48L)

        assertEquals(AnalysisStatus.RUNNING, current.status)
        assertNull(current.errorMessage)
        verify(analysisRepo).savePipelineAnalysis(current)
        verify(analysisRepo, never()).savePipelineAnalysis(cancelled)
    }
}
