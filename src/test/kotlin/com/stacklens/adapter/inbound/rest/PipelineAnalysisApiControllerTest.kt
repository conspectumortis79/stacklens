package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.pipeline.PipelineAnalysisUseCase
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PipelineAnalysisApiControllerTest {
    private val useCase = mock<PipelineAnalysisUseCase>()
    private val i18n =
        com.stacklens.application.service
            .FakeI18n()
    private val controller = PipelineAnalysisApiController(useCase, i18n)

    private fun analysis(
        id: Long = 1L,
        status: String = "COMPLETED",
        mrUrl: String? = null,
        mrProvider: String? = null,
    ) = PipelineAnalysis(
        id = id,
        status = status,
        mrUrl = mrUrl,
        mrProvider = mrProvider,
    )

    @Test
    fun `all returns mapped analyses`() {
        val a = PipelineAnalysis(mrUrl = "x")
        whenever(useCase.getAllAnalyses()).thenReturn(listOf(a))
        val r = controller.all()
        assertEquals(1, r.size)
    }

    @Test
    fun `status returns mapped DTO when found`() {
        val a = analysis(mrUrl = "https://gl/mr/1", mrProvider = "GITLAB")
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        val r = controller.status(1L)
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `status returns 404 when not found`() {
        whenever(useCase.getAnalysis(99L)).thenReturn(null)
        assertEquals(404, controller.status(99L).statusCode.value())
    }

    @Test
    fun `delete returns 404 when not found`() {
        whenever(useCase.getAnalysis(99L)).thenReturn(null)
        assertEquals(404, controller.delete(99L).statusCode.value())
    }

    @Test
    fun `delete returns 204 and forwards deleteAnalysis to the use case when the analysis exists`() {
        val a = analysis(1L)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        val resp = controller.delete(1L)
        assertEquals(204, resp.statusCode.value())
        verify(useCase).deleteAnalysis(1L)
    }

    @Test
    fun `startLocal returns 200 with the analysisId and status`() {
        val a = analysis(id = 42L, status = "PENDING")
        whenever(useCase.startLocalAnalysis(eq(7L), anyOrNull())).thenReturn(a)
        val resp = controller.startLocal(PipelineAnalysisApiController.StartLocalDto(repoConfigId = 7L))
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(42L, body["analysisId"])
        assertEquals("PENDING", body["status"])
    }

    @Test
    fun `startGitlab returns 200 with the analysisId and status`() {
        val a = analysis(id = 100L, status = "PENDING")
        whenever(useCase.startGitlabAnalysis(eq(1L), eq(2L), eq("g/p"), anyOrNull())).thenReturn(a)
        val resp =
            controller.startGitlab(
                PipelineAnalysisApiController.StartGitlabDto(
                    gitlabConfigId = 1L,
                    projectId = 2L,
                    projectPath = "g/p",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(100L, body["analysisId"])
        assertEquals("PENDING", body["status"])
    }

    @Test
    fun `startGithub returns 200 with the analysisId and status`() {
        val a = analysis(id = 200L, status = "PENDING")
        whenever(useCase.startGithubAnalysis(eq(9L), anyOrNull())).thenReturn(a)
        val resp =
            controller.startGithub(
                PipelineAnalysisApiController.StartGithubDto(githubRepoConfigId = 9L),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(200L, body["analysisId"])
        assertEquals("PENDING", body["status"])
    }

    @Test
    fun `cancel returns 200 with status from existing analysis`() {
        whenever(useCase.getAnalysis(1L)).thenReturn(analysis(1L, status = "CANCELLED"))
        val resp = controller.cancel(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("CANCELLED", body["status"])
        verify(useCase).cancelAnalysis(1L)
    }

    @Test
    fun `createMrOrPr returns 404 when analysis not found`() {
        whenever(useCase.getAnalysis(99L)).thenReturn(null)
        val resp =
            controller.createMrOrPr(
                analysisId = 99L,
                dto = PipelineAnalysisApiController.CreateMrOrPrDto(),
            )
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `createMrOrPr forwards dto overrides and returns the url payload`() {
        val a = analysis(1L)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        whenever(useCase.createMrOrPr(1L, 5L, 100L))
            .thenReturn(PipelineMrResult(provider = "GITLAB", url = "https://gl/mr/1", kind = "merge_request", branch = "pipeline-analyse-1"))

        val resp =
            controller.createMrOrPr(
                analysisId = 1L,
                dto = PipelineAnalysisApiController.CreateMrOrPrDto(gitlabConfigId = 5L, gitlabProjectId = 100L),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("https://gl/mr/1", body["url"])
        assertEquals("GITLAB", body["provider"])
        assertEquals("merge_request", body["kind"])
        assertEquals("pipeline-analyse-1", body["branch"])
    }

    @Test
    fun `createMrOrPr passes null overrides for non-GitLab sources`() {
        val a = analysis(1L)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        whenever(useCase.createMrOrPr(1L, null, null))
            .thenReturn(PipelineMrResult(provider = "SIMULATED", url = "/simulated-pipeline-mr/1", kind = "merge_request", branch = null))

        val resp =
            controller.createMrOrPr(
                analysisId = 1L,
                dto = null,
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("SIMULATED", body["provider"])
        assertEquals("/simulated-pipeline-mr/1", body["url"])
        verify(useCase).createMrOrPr(1L, null, null)
    }

    @Test
    fun `createMrOrPr returns 400 when the service throws`() {
        whenever(useCase.getAnalysis(1L)).thenReturn(analysis(1L))
        whenever(useCase.createMrOrPr(eq(1L), anyOrNull(), anyOrNull()))
            .thenThrow(RuntimeException("no GitLab config"))
        val resp =
            controller.createMrOrPr(
                analysisId = 1L,
                dto = PipelineAnalysisApiController.CreateMrOrPrDto(),
            )
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("no GitLab config", body["error"])
    }

    @Test
    fun `createMrOrPr returns fallback error when exception message is null`() {
        whenever(useCase.getAnalysis(1L)).thenReturn(analysis(1L))
        whenever(useCase.createMrOrPr(eq(1L), anyOrNull(), anyOrNull()))
            .thenThrow(RuntimeException(null as String?))
        val resp =
            controller.createMrOrPr(
                analysisId = 1L,
                dto = PipelineAnalysisApiController.CreateMrOrPrDto(),
            )
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["error"])
    }

    @Test
    fun `CreateMrOrPrDto defaults both fields to null`() {
        val dto = PipelineAnalysisApiController.CreateMrOrPrDto()
        assertEquals(null, dto.gitlabConfigId)
        assertEquals(null, dto.gitlabProjectId)
    }

    @Test
    fun `StartLocalDto default constructor uses null userPrompt`() {
        val dto = PipelineAnalysisApiController.StartLocalDto(repoConfigId = 1L)
        assertEquals(null, dto.userPrompt)
    }

    @Test
    fun `StartGitlabDto default constructor uses null userPrompt`() {
        val dto =
            PipelineAnalysisApiController.StartGitlabDto(
                gitlabConfigId = 1L,
                projectId = 100L,
                projectPath = "team/repo",
            )
        assertEquals(null, dto.userPrompt)
    }

    @Test
    fun `StartGithubDto default constructor uses null userPrompt`() {
        val dto = PipelineAnalysisApiController.StartGithubDto(githubRepoConfigId = 1L)
        assertEquals(null, dto.userPrompt)
    }

    @Test
    fun `cancelLatestForRepo returns 200 with cancelled id`() {
        whenever(useCase.cancelLatestRunningForRepo(1L)).thenReturn(42L)
        val resp = controller.cancelLatestForRepo(1L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `cancelLatestForGitlab returns 200 with cancelled id`() {
        whenever(useCase.cancelLatestRunningForGitlab(1L, 100L)).thenReturn(42L)
        val resp = controller.cancelLatestForGitlab(1L, 100L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `cancelLatestForGithub returns 200 with cancelled id`() {
        whenever(useCase.cancelLatestRunningForGithub(1L)).thenReturn(42L)
        val resp = controller.cancelLatestForGithub(1L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `latestLocal returns 200 with mapped DTO`() {
        whenever(useCase.getLatestLocalAnalysis(1L)).thenReturn(analysis(1L, mrUrl = "https://local/1"))
        val resp = controller.latestLocal(1L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `latestLocal returns 200 with NONE status when not found`() {
        whenever(useCase.getLatestLocalAnalysis(99L)).thenReturn(null)
        val resp = controller.latestLocal(99L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `latestGitlab returns 200 with mapped DTO`() {
        whenever(useCase.getLatestGitlabAnalysis(1L, 100L)).thenReturn(analysis(1L, mrUrl = "https://gl/1"))
        val resp = controller.latestGitlab(1L, 100L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `latestGitlab returns 200 with NONE status when not found`() {
        whenever(useCase.getLatestGitlabAnalysis(99L, 999L)).thenReturn(null)
        val resp = controller.latestGitlab(99L, 999L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `latestGithub returns 200 with mapped DTO`() {
        whenever(useCase.getLatestGithubAnalysis(1L)).thenReturn(analysis(1L, mrUrl = "https://gh/1"))
        val resp = controller.latestGithub(1L)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `latestGithub returns 200 with NONE status when not found`() {
        whenever(useCase.getLatestGithubAnalysis(99L)).thenReturn(null)
        val resp = controller.latestGithub(99L)
        assertEquals(200, resp.statusCode.value())
    }
}
