package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.pipeline.PipelineFilesPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.testsupport.InlineAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineAnalysisServiceCoverageTest {
    private val analysisRepo = mock<PipelineAnalysisPort>()
    private val filesPort = mock<PipelineFilesPort>()
    private val copilot = mock<CopilotPort>()
    private val startupHealer = mock<StartupRecovery>()
    private val asyncExecutor = InlineAsyncExecutor()
    private val aiService = mock<PipelineAnalysisAiService>()

    private fun newService(analysisRepo: PipelineAnalysisPort = this.analysisRepo): PipelineAnalysisService =
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

    @Test
    fun `getAnalysis delegates to the repository and returns its result`() {
        val row =
            PipelineAnalysis(
                id = 1L,
                createdAt = java.time.LocalDateTime.now(),
                status = AnalysisStatus.PENDING,
            )
        whenever(analysisRepo.findPipelineAnalysisById(1L)).thenReturn(row)
        assertEquals(row, newService().getAnalysis(1L))
    }

    @Test
    fun `getAnalysis returns null when the repository returns null`() {
        whenever(analysisRepo.findPipelineAnalysisById(99L)).thenReturn(null)
        assertNull(newService().getAnalysis(99L))
    }

    @Test
    fun `getAllAnalyses delegates to the repository and returns its result`() {
        val rows =
            listOf(
                PipelineAnalysis(
                    id = 1L,
                    createdAt = java.time.LocalDateTime.now(),
                    status = AnalysisStatus.COMPLETED,
                ),
                PipelineAnalysis(
                    id = 2L,
                    createdAt = java.time.LocalDateTime.now(),
                    status = AnalysisStatus.RUNNING,
                ),
            )
        whenever(analysisRepo.findRecentPipelineAnalyses(200)).thenReturn(rows)
        assertEquals(rows, newService().getAllAnalyses())
    }

    @Test
    fun `getAllAnalyses returns an empty list when the repository has no rows`() {
        whenever(analysisRepo.findRecentPipelineAnalyses(200)).thenReturn(emptyList())
        assertTrue(newService().getAllAnalyses().isEmpty())
    }
}
