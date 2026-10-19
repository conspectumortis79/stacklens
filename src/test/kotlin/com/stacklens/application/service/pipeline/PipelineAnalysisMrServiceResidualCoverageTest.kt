package com.stacklens.application.service.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.adapter.out.pipeline.PipelineMrSimulationAdapter
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineAnalysisMrServiceResidualCoverageTest {
    private val analysisRepo = mock<PipelineAnalysisPort>()
    private val gitlabMrPort = mock<GitlabMrPort>()
    private val gitlabConfigPort = mock<GitlabConfigPort>()
    private val githubRepoConfigPort = mock<GithubRepoConfigPort>()
    private val githubBridge = mock<GithubTicketBridgePort>()
    private val gitlabRepoPort = mock<CodeAnalysisRepoApi>()
    private val jsonCodec = JacksonJsonCodecAdapter(ObjectMapper().apply { findAndRegisterModules() })
    private val i18n = FakeI18n()

    private lateinit var service: PipelineAnalysisMrService

    @BeforeEach
    fun wireService() {
        service =
            PipelineAnalysisMrService(
                analysisRepo,
                gitlabMrPort,
                gitlabConfigPort,
                githubRepoConfigPort,
                githubBridge,
                gitlabRepoPort,
                jsonCodec,
                i18n,
                PipelineMrSimulationAdapter(),
                FixedClockPort(1_750_000_000_000L),
            )
        whenever(analysisRepo.savePipelineAnalysis(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `a gitlab config without a project id asks the user to select one`() {
        val analysis =
            PipelineAnalysis(
                id = 70L,
                status = "COMPLETED",
                gitlabConfigId = 1L,
                gitlabProjectId = null,
            )
        whenever(analysisRepo.findPipelineAnalysisById(70L)).thenReturn(analysis)

        assertThrows(IllegalArgumentException::class.java) { service.createMrOrPr(70L) }
    }

    @Test
    fun `the simulated mr points at the mr page when an active gitlab config exists`() {
        val analysis =
            PipelineAnalysis(
                id = 71L,
                status = "COMPLETED",
                gitlabConfigId = null,
                githubRepoConfigId = null,
            )
        whenever(analysisRepo.findPipelineAnalysisById(71L)).thenReturn(analysis)
        whenever(gitlabConfigPort.existsActiveGitlabConfig()).thenReturn(true)

        val result = service.createMrOrPr(71L)

        assertTrue(result.url.startsWith("/simulated-pipeline-mr/"))
    }

    @Test
    fun `the simulated mr points at the pr page when no gitlab config exists`() {
        val analysis =
            PipelineAnalysis(
                id = 72L,
                status = "COMPLETED",
                gitlabConfigId = null,
                githubRepoConfigId = null,
            )
        whenever(analysisRepo.findPipelineAnalysisById(72L)).thenReturn(analysis)
        whenever(gitlabConfigPort.existsActiveGitlabConfig()).thenReturn(false)

        val result = service.createMrOrPr(72L)

        assertTrue(result.url.startsWith("/simulated-pipeline-pr/"))
    }
}
