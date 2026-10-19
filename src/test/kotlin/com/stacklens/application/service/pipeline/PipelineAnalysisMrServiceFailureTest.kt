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
import com.stacklens.application.port.out.mr.GitlabMrProjectDetail
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PipelineAnalysisMrServiceFailureTest {
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

    private val gitlabConfig =
        GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")

    @Test
    fun `explicit overrides win over the stored gitlab references`() {
        val analysis =
            PipelineAnalysis(
                id = 31L,
                status = "COMPLETED",
                gitlabConfigId = null,
                gitlabProjectId = null,
                summaryText = "Kurzfassung",
            )
        whenever(analysisRepo.findPipelineAnalysisById(31L)).thenReturn(analysis)
        whenever(gitlabRepoPort.findGitlabConfig(5L)).thenReturn(gitlabConfig)
        whenever(gitlabMrPort.fetchProjectDetail(gitlabConfig, 6L))
            .thenReturn(GitlabMrProjectDetail(id = 6L, defaultBranch = "main", pathWithNamespace = "g/p"))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any())).thenReturn("http://gl/mr/31")

        val result = service.createMrOrPr(31L, 5L, 6L)

        assertEquals("http://gl/mr/31", result.url)
        verify(gitlabMrPort).commitFiles(eq(gitlabConfig), eq(6L), any(), any(), any())
    }

    @Test
    fun `a rejected marker commit aborts the merge request`() {
        val analysis =
            PipelineAnalysis(
                id = 32L,
                status = "COMPLETED",
                gitlabConfigId = 1L,
                gitlabProjectId = 7L,
            )
        whenever(analysisRepo.findPipelineAnalysisById(32L)).thenReturn(analysis)
        whenever(gitlabRepoPort.findGitlabConfig(1L)).thenReturn(gitlabConfig)
        whenever(gitlabMrPort.fetchProjectDetail(gitlabConfig, 7L))
            .thenReturn(GitlabMrProjectDetail(id = 7L, defaultBranch = "main", pathWithNamespace = "g/p"))
        Mockito
            .doThrow(RuntimeException("protected branch"))
            .`when`(gitlabMrPort)
            .commitFiles(any(), any(), any(), any(), any())

        assertThrows(RuntimeException::class.java) { service.createMrOrPr(32L) }
    }

    @Test
    fun `a missing github configuration aborts the pull request`() {
        val analysis =
            PipelineAnalysis(
                id = 33L,
                status = "COMPLETED",
                githubRepoConfigId = 8L,
            )
        whenever(analysisRepo.findPipelineAnalysisById(33L)).thenReturn(analysis)
        whenever(githubRepoConfigPort.findGithubRepoConfigById(8L)).thenReturn(null)

        val failure = assertThrows(IllegalArgumentException::class.java) { service.createMrOrPr(33L) }

        assertEquals("i18n:pipeline.be_github_config_not_found|8", failure.message)
    }

    @Test
    fun `optimization rows without title or rationale only print what they have`() {
        val analysis =
            PipelineAnalysis(
                id = 34L,
                status = "COMPLETED",
                summaryText = "Zusammenfassung",
                findingsJson = """[{"severity":"LOW","type":"","file":"docker-compose.yml","description":"zu wenig","suggestion":""}]""",
                optimizationsJson = """[{"title":"Speicher begrenzen","rationale":null},{"title":"  "},{"title":"","rationale":"keine"}]""",
            )
        whenever(analysisRepo.findPipelineAnalysisById(34L)).thenReturn(analysis)

        val result = service.createMrOrPr(34L)

        assertEquals("SIMULATED", result.provider)
        assertEquals("/simulated-pipeline-pr/34", result.url)
    }

    @Test
    fun `a github pull request is opened through the bridge`() {
        val analysis =
            PipelineAnalysis(
                id = 35L,
                status = "COMPLETED",
                githubRepoConfigId = 9L,
                summaryText = "Lauf",
            )
        val config = GithubRepoConfig(id = 9L, name = "gh", owner = "octo", repo = "cat", accessToken = "tok")
        whenever(analysisRepo.findPipelineAnalysisById(35L)).thenReturn(analysis)
        whenever(githubRepoConfigPort.findGithubRepoConfigById(9L)).thenReturn(config)
        whenever(githubBridge.createPullRequest(any(), any(), any())).thenReturn("https://github/pr/35")

        val result = service.createMrOrPr(35L)

        assertEquals("https://github/pr/35", result.url)
    }

    @Test
    fun `a stored gitlab reference without a project id is rejected`() {
        val analysis =
            PipelineAnalysis(
                id = 41L,
                status = "COMPLETED",
                gitlabConfigId = 1L,
                gitlabProjectId = null,
            )
        whenever(analysisRepo.findPipelineAnalysisById(41L)).thenReturn(analysis)

        assertThrows(IllegalArgumentException::class.java) { service.createMrOrPr(41L) }
    }

    @Test
    fun `an active gitlab config simulates a merge request`() {
        val analysis =
            PipelineAnalysis(
                id = 42L,
                status = "COMPLETED",
                gitlabConfigId = null,
                githubRepoConfigId = null,
            )
        whenever(analysisRepo.findPipelineAnalysisById(42L)).thenReturn(analysis)
        whenever(gitlabConfigPort.existsActiveGitlabConfig()).thenReturn(true)

        val result = service.createMrOrPr(42L)

        assertEquals("merge_request", result.kind)
        assertEquals("/simulated-pipeline-mr/42", result.url)
    }

    @Test
    fun `a github analysis that loses its config id is rejected`() {
        val analysis = mock<PipelineAnalysis>()
        whenever(analysis.gitlabConfigId).thenReturn(null)
        whenever(analysis.githubRepoConfigId).thenReturn(44L, null)
        whenever(analysisRepo.findPipelineAnalysisById(43L)).thenReturn(analysis)

        assertThrows(IllegalArgumentException::class.java) { service.createMrOrPr(43L) }
    }

    @Test
    fun `suggestions and findings without text are left out of the description`() {
        val analysis =
            PipelineAnalysis(
                id = 45L,
                status = "COMPLETED",
                summaryText = " ",
                findingsJson = """[{"severity":"HIGH","type":"","file":"a.kt","line":null,"description":"d","suggestion":""}]""",
                optimizationsJson = """[{"title":"keep","rationale":7},{"title":null},{"other":"x"}]""",
            )

        val description = service.buildMrDescription(analysis)

        assertTrue(description.contains("- **keep**"))
        assertTrue(description.contains("  - 7"))
        assertEquals(1, Regex("(?m)^- \\*\\*").findAll(description).count())
        assertEquals(1, Regex("\\?\\*").findAll(description).count())
    }
}
