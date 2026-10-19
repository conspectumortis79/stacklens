package com.stacklens.application.service.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.adapter.out.pipeline.PipelineMrSimulationAdapter
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.port.out.mr.GitlabMrProjectDetail
import com.stacklens.application.service.FakeI18n
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.PipelineAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PipelineAnalysisMrServiceTest {
    private lateinit var analysisRepo: PipelineAnalysisPort
    private lateinit var gitlabMrPort: GitlabMrPort
    private lateinit var gitlabConfigPort: GitlabConfigPort
    private lateinit var githubRepoConfigPort: GithubRepoConfigPort
    private lateinit var githubBridge: GithubTicketBridgePort
    private lateinit var gitlabRepoPort: CodeAnalysisRepoApi
    private val jsonCodec = JacksonJsonCodecAdapter(ObjectMapper().apply { findAndRegisterModules() })
    private val i18n = FakeI18n()
    private lateinit var service: PipelineAnalysisMrService

    @BeforeEach
    fun setup() {
        analysisRepo = mock()
        gitlabMrPort = mock()
        gitlabConfigPort = mock()
        githubRepoConfigPort = mock()
        githubBridge = mock()
        gitlabRepoPort = mock()
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
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        whenever(analysisRepo.savePipelineAnalysis(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `createMrOrPr throws when analysis does not exist`() {
        whenever(analysisRepo.findPipelineAnalysisById(99L)).thenReturn(null)
        assertThrows(IllegalArgumentException::class.java) {
            service.createMrOrPr(99L)
        }
    }

    @Test
    fun `createMrOrPr routes GitLab analysis to GitlabMrPort and persists url`() {
        val analysis =
            PipelineAnalysis(
                id = 7L,
                status = "COMPLETED",
                gitlabConfigId = 1L,
                gitlabProjectId = 42L,
                gitlabProjectPath = "g/p",
                summaryText = "All good.",
                findingsJson = """[{"id":"f1","file":"docker-compose.yml","type":"COMPOSE_WARNING","severity":"HIGH","description":"oops","suggestion":"fix"}]""",
            )
        val config =
            GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl.test", accessToken = "tok")
        val projectDetail =
            GitlabMrProjectDetail(
                id = 42L,
                defaultBranch = "develop",
                pathWithNamespace = "g/p",
            )
        whenever(analysisRepo.findPipelineAnalysisById(7L)).thenReturn(analysis)
        whenever(gitlabRepoPort.findGitlabConfig(1L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(config, 42L)).thenReturn(projectDetail)
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any()))
            .thenReturn("http://gl.test/g/p/-/merge_requests/1")

        val result = service.createMrOrPr(7L)

        assertEquals("GITLAB", result.provider)
        assertEquals("merge_request", result.kind)
        assertEquals("http://gl.test/g/p/-/merge_requests/1", result.url)
        assertEquals("pipeline-analyse-7", result.branch)
        verify(gitlabMrPort).commitFiles(eq(config), eq(42L), eq("pipeline-analyse-7"), any(), any())
        verify(gitlabMrPort).createMergeRequest(eq(config), eq(42L), eq("pipeline-analyse-7"), eq("develop"), any(), any())
        assertEquals("http://gl.test/g/p/-/merge_requests/1", analysis.mrUrl)
        assertEquals("pipeline-analyse-7", analysis.mrBranch)
        assertEquals("GITLAB", analysis.mrProvider)
        verify(analysisRepo).savePipelineAnalysis(analysis)
    }

    @Test
    fun `createMrOrPr uses blank defaultBranch fallback to main`() {
        val analysis =
            PipelineAnalysis(
                id = 11L,
                status = "COMPLETED",
                gitlabConfigId = 2L,
                gitlabProjectId = 50L,
            )
        val config =
            GitlabConfig(id = 2L, name = "gl2", baseUrl = "http://gl.test", accessToken = "tok")
        whenever(analysisRepo.findPipelineAnalysisById(11L)).thenReturn(analysis)
        whenever(gitlabRepoPort.findGitlabConfig(2L)).thenReturn(config)
        whenever(gitlabMrPort.fetchProjectDetail(config, 50L))
            .thenReturn(GitlabMrProjectDetail(id = 50L, defaultBranch = ""))
        whenever(gitlabMrPort.createMergeRequest(any(), any(), any(), any(), any(), any()))
            .thenReturn("http://gl/mr/11")

        val result = service.createMrOrPr(11L)

        assertEquals("pipeline-analyse-11", result.branch)
        verify(gitlabMrPort).createMergeRequest(any(), any(), any(), eq("main"), any(), any())
    }

    @Test
    fun `createMrOrPr throws when override GitLab config missing`() {
        val analysis =
            PipelineAnalysis(
                id = 21L,
                status = "COMPLETED",
                gitlabConfigId = 99L,
                gitlabProjectId = 100L,
            )
        whenever(analysisRepo.findPipelineAnalysisById(21L)).thenReturn(analysis)
        whenever(gitlabRepoPort.findGitlabConfig(99L)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            service.createMrOrPr(21L)
        }
        verify(analysisRepo, never()).savePipelineAnalysis(any())
    }

    @Test
    fun `createMrOrPr throws when analysis lacks gitlabConfigId and gitlabProjectId`() {
        val analysis = PipelineAnalysis(id = 30L, status = "COMPLETED")
        whenever(analysisRepo.findPipelineAnalysisById(30L)).thenReturn(analysis)

        assertThrows(IllegalArgumentException::class.java) {
            service.createMrOrPr(30L, gitlabConfigId = 5L, gitlabProjectId = null)
        }
    }

    @Test
    fun `createMrOrPr routes GitHub analysis to GithubTicketBridgePort`() {
        val analysis =
            PipelineAnalysis(
                id = 5L,
                status = "COMPLETED",
                githubRepoConfigId = 7L,
                summaryText = "Lint warnings.",
            )
        val config =
            GithubRepoConfig(
                id = 7L,
                name = "demo",
                owner = "acme",
                repo = "app",
                branch = "main",
                accessToken = "ghp_x",
                apiBase = "https://api.github.com",
                active = true,
            )
        whenever(analysisRepo.findPipelineAnalysisById(5L)).thenReturn(analysis)
        whenever(githubRepoConfigPort.findGithubRepoConfigById(7L)).thenReturn(config)
        whenever(githubBridge.createPullRequest(any(), any(), any())).thenReturn("http://gh/pr/5")

        val result = service.createMrOrPr(5L)

        assertEquals("GITHUB", result.provider)
        assertEquals("pull_request", result.kind)
        assertEquals("http://gh/pr/5", result.url)
        assertNotNull(result.branch)
        val ticketCaptor = argumentCaptor<JiraTicketData>()
        val implCaptor = argumentCaptor<JiraCodeImplementation>()
        verify(githubBridge).createPullRequest(eq(config), ticketCaptor.capture(), implCaptor.capture())
        assertEquals("PIPELINE-5", ticketCaptor.firstValue.key)
        assertTrue(ticketCaptor.firstValue.summary.contains("Pipeline-Analyse #5"))
        assertEquals(1, implCaptor.firstValue.files.size)
        assertTrue(
            implCaptor.firstValue.files[0]
                .path
                .startsWith(".stacklens/pipeline-analysis-"),
        )
        assertEquals(FileChangeType.CREATE, implCaptor.firstValue.files[0].changeType)
        assertEquals("http://gh/pr/5", analysis.mrUrl)
        assertEquals("GITHUB", analysis.mrProvider)
    }

    @Test
    fun `createMrOrPr throws when GitHub config does not exist`() {
        val analysis =
            PipelineAnalysis(
                id = 6L,
                status = "COMPLETED",
                githubRepoConfigId = 99L,
            )
        whenever(analysisRepo.findPipelineAnalysisById(6L)).thenReturn(analysis)
        whenever(githubRepoConfigPort.findGithubRepoConfigById(99L)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            service.createMrOrPr(6L)
        }
    }

    @Test
    fun `createMrOrPr routes local analysis to simulated MR when GitLab configs exist`() {
        val analysis =
            PipelineAnalysis(id = 8L, status = "COMPLETED", repoConfigId = 1L)
        whenever(analysisRepo.findPipelineAnalysisById(8L)).thenReturn(analysis)
        whenever(gitlabConfigPort.existsActiveGitlabConfig()).thenReturn(true)

        val result = service.createMrOrPr(8L)

        assertEquals("SIMULATED", result.provider)
        assertEquals("/simulated-pipeline-mr/8", result.url)
        assertEquals("merge_request", result.kind)
        assertNull(result.branch)
        verify(gitlabMrPort, never()).createMergeRequest(any(), any(), any(), any(), any(), any())
        verify(githubBridge, never()).createPullRequest(any(), any(), any())
        assertEquals("/simulated-pipeline-mr/8", analysis.mrUrl)
        assertEquals("SIMULATED", analysis.mrProvider)
    }

    @Test
    fun `createMrOrPr routes local analysis to simulated PR when no GitLab configs exist`() {
        val analysis =
            PipelineAnalysis(id = 9L, status = "COMPLETED", repoConfigId = 1L)
        whenever(analysisRepo.findPipelineAnalysisById(9L)).thenReturn(analysis)
        whenever(gitlabConfigPort.existsActiveGitlabConfig()).thenReturn(false)

        val result = service.createMrOrPr(9L)

        assertEquals("SIMULATED", result.provider)
        assertEquals("/simulated-pipeline-pr/9", result.url)
        assertEquals("pull_request", result.kind)
    }

    @Test
    fun `createMrOrPr treats inactive GitLab configs as missing`() {
        val analysis =
            PipelineAnalysis(id = 91L, status = "COMPLETED", repoConfigId = 1L)
        whenever(analysisRepo.findPipelineAnalysisById(91L)).thenReturn(analysis)
        whenever(gitlabConfigPort.existsActiveGitlabConfig()).thenReturn(false)

        val result = service.createMrOrPr(91L)

        assertEquals("/simulated-pipeline-pr/91", result.url)
    }

    @Test
    fun `buildMrDescription includes HIGH MEDIUM and LOW severity badges`() {
        val findingsJson =
            """
            [
              {"id":"f1","file":"docker-compose.yml","type":"COMPOSE_ERROR","severity":"HIGH","description":"bad","suggestion":"s1"},
              {"id":"f2","file":"Dockerfile","type":"BEST_PRACTICE","severity":"MEDIUM","description":"meh","suggestion":"s2"},
              {"id":"f3","file":".gitlab-ci.yml","type":"OPTIMIZATION","severity":"LOW","description":"ok","suggestion":""}
            ]
            """.trimIndent()
        val analysis =
            PipelineAnalysis(
                id = 100L,
                status = "COMPLETED",
                summaryText = "kurz",
                findingsJson = findingsJson,
                optimizationsJson = """[{"title":"Add healthchecks","rationale":"Resilience"}]""",
            )

        val md = service.buildMrDescription(analysis)

        assertTrue(md.contains("🔴"))
        assertTrue(md.contains("🟡"))
        assertTrue(md.contains("🟢"))
        assertTrue(md.contains("kurz"))
        assertTrue(md.contains("Add healthchecks"))
        assertTrue(md.contains("Resilience"))
        assertTrue(md.trimEnd().endsWith("*Generated by StackLens Pipeline-Analyse*"))
    }

    @Test
    fun `buildMrDescription tolerates malformed JSON in findingsJson`() {
        val analysis =
            PipelineAnalysis(
                id = 101L,
                status = "COMPLETED",
                summaryText = "ok",
                findingsJson = "{not valid}",
            )
        val md = service.buildMrDescription(analysis)
        assertTrue(md.contains("ok"))
        assertTrue(!md.contains("### Befunde"))
    }

    @Test
    fun `buildMrDescription tolerates blank summary and findings`() {
        val analysis =
            PipelineAnalysis(
                id = 102L,
                status = "COMPLETED",
                summaryText = "   ",
                findingsJson = "",
                optimizationsJson = null,
            )
        val md = service.buildMrDescription(analysis)
        assertTrue(!md.contains("### Zusammenfassung"))
        assertTrue(!md.contains("### Befunde"))
        assertTrue(md.contains("*Generated by StackLens Pipeline-Analyse*"))
    }

    @Test
    fun `buildMrDescription includes line number when finding has line set`() {
        val findingsJson =
            """
            [{"id":"f","file":"docker-compose.yml","line":42,"type":"COMPOSE_WARNING","severity":"HIGH","description":"x","suggestion":""}]
            """.trimIndent()
        val analysis =
            PipelineAnalysis(id = 103L, status = "COMPLETED", findingsJson = findingsJson)
        val md = service.buildMrDescription(analysis)
        assertTrue(md.contains("Zeile 42"))
    }
}
