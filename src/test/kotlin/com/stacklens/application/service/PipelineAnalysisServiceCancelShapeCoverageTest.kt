package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineAnalysisServiceCancelShapeCoverageTest {
    private val analysisRepo = mock<PipelineAnalysisPort>()

    private fun service(): PipelineAnalysisService =
        pipelineAnalysisService(
            analysisRepo = analysisRepo,
            filesPort = mock(),
            copilotCliService = mock<CopilotPort>(),
            aiUsageContext = mock<AiUsageContextPort>(),
            i18n = com.stacklens.testsupport.FakeI18n(),
            aiService = mock<PipelineAnalysisAiService>(),
            startupHealer = mock<StartupRecovery>(),
            asyncExecutor = InlineAsyncExecutor(),
            mrService = mock(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun analysis(
        id: Long,
        status: String,
    ): PipelineAnalysis =
        PipelineAnalysis(
            id = id,
            repoConfigId = 3L,
            gitlabConfigId = 4L,
            gitlabProjectId = 5L,
            status = status,
        )

    @Test
    fun `a repository without any analysis has nothing to cancel`() {
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(any())).thenReturn(null)
        whenever(analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(any(), any())).thenReturn(null)

        assertEquals(null, service().cancelLatestRunningForRepo(3L))
        assertEquals(null, service().cancelLatestRunningForGitlab(4L, 5L))
    }

    @Test
    fun `a finished analysis is not cancelled again`() {
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(any()))
            .thenReturn(analysis(10L, AnalysisStatus.COMPLETED))
        whenever(analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(any(), any()))
            .thenReturn(analysis(11L, AnalysisStatus.FAILED))

        assertEquals(null, service().cancelLatestRunningForRepo(3L))
        assertEquals(null, service().cancelLatestRunningForGitlab(4L, 5L))
    }

    @Test
    fun `a running analysis is cancelled and its id reported`() {
        whenever(analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(any()))
            .thenReturn(analysis(12L, AnalysisStatus.RUNNING))
        whenever(analysisRepo.findPipelineAnalysisById(12L)).thenReturn(analysis(12L, AnalysisStatus.RUNNING))

        assertEquals(12L, service().cancelLatestRunningForRepo(3L))
    }

    @Test
    fun `a github run that already finished is left alone`() {
        whenever(analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(any()))
            .thenReturn(analysis(14L, AnalysisStatus.CANCELLED))

        assertEquals(null, service().cancelLatestRunningForGithub(6L))
    }

    @Test
    fun `a pending analysis waiting in the queue is cancelled too`() {
        whenever(analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(any(), any()))
            .thenReturn(analysis(13L, AnalysisStatus.PENDING))
        whenever(analysisRepo.findPipelineAnalysisById(13L)).thenReturn(analysis(13L, AnalysisStatus.PENDING))

        assertEquals(13L, service().cancelLatestRunningForGitlab(4L, 5L))
    }
}
