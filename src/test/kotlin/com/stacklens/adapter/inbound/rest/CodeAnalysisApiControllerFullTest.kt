package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CodeAnalysisApiControllerFullTest {
    private lateinit var useCase: CodeAnalysisUseCase
    private lateinit var workspaceFiles: WorkspaceFileUseCase
    private lateinit var i18n: I18nUseCase
    private lateinit var controller: CodeAnalysisApiController

    @BeforeEach
    fun setUp() {
        useCase = mock()
        workspaceFiles = mock()
        i18n = mock()
        whenever(i18n.t(any(), anyVararg())).thenAnswer { it.arguments[0] as String }
        controller = CodeAnalysisApiController(useCase, workspaceFiles, i18n)
    }

    private fun analysis(
        id: Long = 1L,
        status: String = "COMPLETED",
        gitlabConfigId: Long? = null,
        gitlabProjectId: Long? = null,
        monorepoAppName: String? = null,
        gitlabProjectPath: String? = null,
    ) = CodeAnalysis(
        id = id,
        mrUrl = "https://gitlab.example.com/g/p/-/merge_requests/$id",
        status = status,
        gitlabConfigId = gitlabConfigId,
        gitlabProjectId = gitlabProjectId,
        monorepoAppName = monorepoAppName,
        gitlabProjectPath = gitlabProjectPath,
    )

    @Test
    fun `startLocal returns analysis id and status`() {
        val a = analysis(42L, status = "RUNNING")
        whenever(useCase.startLocalAnalysis(7L, includeMermaid = true, includeDrawio = true)).thenReturn(a)

        val resp =
            controller.startLocal(
                CodeAnalysisApiController.StartLocalDto(repoConfigId = 7L),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(42L, body["analysisId"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `startGitlab returns single analysis id and non-monorepo when only one project`() {
        val a = analysis(10L, gitlabProjectPath = "g/p")
        whenever(
            useCase.startGitlabAnalysis(1L, 100L, "g/p", includeMermaid = true, includeDrawio = true),
        ).thenReturn(listOf(a))

        val resp =
            controller.startGitlab(
                CodeAnalysisApiController.StartGitlabDto(
                    gitlabConfigId = 1L,
                    projectId = 100L,
                    projectPath = "g/p",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(10L, body["analysisId"])
        assertEquals(false, body["isMonorepo"])
    }

    @Test
    fun `startGitlab returns monorepo flag true when multiple analyses`() {
        val a = analysis(10L, monorepoAppName = "app1", gitlabProjectPath = "g/p")
        val b = analysis(11L, monorepoAppName = "app2", gitlabProjectPath = "g/p")
        whenever(
            useCase.startGitlabAnalysis(1L, 100L, "g/p", includeMermaid = true, includeDrawio = true),
        ).thenReturn(listOf(a, b))

        val resp =
            controller.startGitlab(
                CodeAnalysisApiController.StartGitlabDto(
                    gitlabConfigId = 1L,
                    projectId = 100L,
                    projectPath = "g/p",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(true, body["isMonorepo"])
        @Suppress("UNCHECKED_CAST")
        val ids = body["analysisIds"] as List<Map<String, Any?>>
        assertEquals(2, ids.size)
        assertEquals("app1", ids[0]["appName"])
        assertEquals("app2", ids[1]["appName"])
    }

    @Test
    fun `startGitlab falls back to projectPath substring for appName when monorepoAppName is null`() {
        val a = analysis(10L, monorepoAppName = null, gitlabProjectPath = "group/subgroup/my-app")
        whenever(
            useCase.startGitlabAnalysis(1L, 100L, "group/subgroup/my-app", includeMermaid = true, includeDrawio = true),
        ).thenReturn(listOf(a))

        val resp =
            controller.startGitlab(
                CodeAnalysisApiController.StartGitlabDto(
                    gitlabConfigId = 1L,
                    projectId = 100L,
                    projectPath = "group/subgroup/my-app",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val ids = body["analysisIds"] as List<Map<String, Any?>>
        assertEquals("my-app", ids[0]["appName"])
    }

    @Test
    fun `startGitlab falls back to empty appName when both monorepoAppName and gitlabProjectPath are null`() {
        val a = analysis(10L, monorepoAppName = null, gitlabProjectPath = null)
        whenever(
            useCase.startGitlabAnalysis(1L, 100L, "g/p", includeMermaid = true, includeDrawio = true),
        ).thenReturn(listOf(a))

        val resp =
            controller.startGitlab(
                CodeAnalysisApiController.StartGitlabDto(
                    gitlabConfigId = 1L,
                    projectId = 100L,
                    projectPath = "g/p",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val ids = body["analysisIds"] as List<Map<String, Any?>>
        assertEquals("", ids[0]["appName"])
    }

    @Test
    fun `startGithub returns analysis id and status`() {
        val a = analysis(99L, status = "RUNNING")
        whenever(useCase.startGithubAnalysis(3L, includeMermaid = true, includeDrawio = true)).thenReturn(a)

        val resp =
            controller.startGithub(CodeAnalysisApiController.StartGithubDto(githubRepoConfigId = 3L))
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(99L, body["analysisId"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `delete returns 204 when analysis exists`() {
        whenever(useCase.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.delete(1L)
        assertEquals(204, resp.statusCode.value())
        verify(useCase).deleteAnalysis(1L)
    }

    @Test
    fun `cancel returns 200 with status from existing analysis`() {
        whenever(useCase.getAnalysis(1L)).thenReturn(analysis(1L, status = "FAILED"))
        val resp = controller.cancel(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("FAILED", body["status"])
        verify(useCase).cancelAnalysis(1L)
    }

    @Test
    fun `cancelLatestForRepo returns the cancelled id`() {
        whenever(useCase.cancelLatestRunningForRepo(5L)).thenReturn(42L)
        val resp = controller.cancelLatestForRepo(5L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(42L, body["cancelledId"])
    }

    @Test
    fun `cancelLatestForRepo returns null cancelledId when nothing was running`() {
        whenever(useCase.cancelLatestRunningForRepo(5L)).thenReturn(null)
        val resp = controller.cancelLatestForRepo(5L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(null, body["cancelledId"])
    }

    @Test
    fun `latestLocal returns NONE status when no analysis`() {
        whenever(useCase.getLatestLocalAnalysis(1L)).thenReturn(null)
        val resp = controller.latestLocal(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("NONE", body["status"])
    }

    @Test
    fun `latestLocal returns mapped analysis when present`() {
        val a = analysis(99L)
        whenever(useCase.getLatestLocalAnalysis(1L)).thenReturn(a)
        val resp = controller.latestLocal(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(99L, body["id"])
    }

    @Test
    fun `latestGitlab returns NONE when no analysis`() {
        whenever(useCase.getLatestGitlabAnalysis(1L, 100L)).thenReturn(null)
        val resp = controller.latestGitlab(1L, 100L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("NONE", body["status"])
    }

    @Test
    fun `latestGitlab returns mapped analysis when present`() {
        val a = analysis(50L)
        whenever(useCase.getLatestGitlabAnalysis(1L, 100L)).thenReturn(a)
        val resp = controller.latestGitlab(1L, 100L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(50L, body["id"])
    }

    @Test
    fun `latestGithub returns NONE when no analysis`() {
        whenever(useCase.getLatestGithubAnalysis(1L)).thenReturn(null)
        val resp = controller.latestGithub(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("NONE", body["status"])
    }

    @Test
    fun `latestGithub returns mapped analysis when present`() {
        val a = analysis(60L)
        whenever(useCase.getLatestGithubAnalysis(1L)).thenReturn(a)
        val resp = controller.latestGithub(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(60L, body["id"])
    }

    @Test
    fun `createMr returns 404 when analysis not found`() {
        whenever(useCase.getAnalysis(99L)).thenReturn(null)
        val resp =
            controller.createMr(
                analysisId = 99L,
                dto = CodeAnalysisApiController.CreateMrDto(),
            )
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `createMr returns 400 when gitlabConfigId is missing on analysis`() {
        val a = analysis(1L, gitlabConfigId = null, gitlabProjectId = null)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        val resp =
            controller.createMr(
                analysisId = 1L,
                dto = CodeAnalysisApiController.CreateMrDto(),
            )
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `createMr returns 400 when projectId is missing on analysis`() {
        val a = analysis(1L, gitlabConfigId = 5L, gitlabProjectId = null)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        val resp =
            controller.createMr(
                analysisId = 1L,
                dto = CodeAnalysisApiController.CreateMrDto(),
            )
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `createMr uses dto values when provided and returns mrUrl on success`() {
        val a = analysis(1L, gitlabConfigId = 99L, gitlabProjectId = 999L)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        whenever(useCase.createMr(1L, 5L, 100L)).thenReturn("https://gitlab.example.com/g/p/-/merge_requests/42")

        val resp =
            controller.createMr(
                analysisId = 1L,
                dto = CodeAnalysisApiController.CreateMrDto(gitlabConfigId = 5L, projectId = 100L),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("https://gitlab.example.com/g/p/-/merge_requests/42", body["mrUrl"])
    }

    @Test
    fun `createMr falls back to analysis gitlabConfigId and projectId when dto omits them`() {
        val a = analysis(1L, gitlabConfigId = 5L, gitlabProjectId = 100L)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        whenever(useCase.createMr(1L, 5L, 100L)).thenReturn("https://gitlab/mr/1")

        val resp =
            controller.createMr(
                analysisId = 1L,
                dto = CodeAnalysisApiController.CreateMrDto(),
            )
        assertEquals(200, resp.statusCode.value())
        verify(useCase).createMr(1L, 5L, 100L)
    }

    @Test
    fun `createMr returns 400 with error message when service throws`() {
        val a = analysis(1L, gitlabConfigId = 5L, gitlabProjectId = 100L)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        whenever(useCase.createMr(any(), any(), any())).thenThrow(RuntimeException("API down"))

        val resp =
            controller.createMr(
                analysisId = 1L,
                dto = CodeAnalysisApiController.CreateMrDto(),
            )
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("API down", body["error"])
    }

    @Test
    fun `createMr returns 400 with fallback error when exception message is null`() {
        val a = analysis(1L, gitlabConfigId = 5L, gitlabProjectId = 100L)
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        whenever(useCase.createMr(any(), any(), any())).thenThrow(RuntimeException(null as String?))

        val resp =
            controller.createMr(
                analysisId = 1L,
                dto = CodeAnalysisApiController.CreateMrDto(),
            )
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNotNull(body["error"])
    }

    @Test
    fun `saveDiagram returns 400 with the translation key when the directory is missing`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val resp =
            controller.saveDiagram(
                analysisId = 1L,
                req =
                    CodeAnalysisApiController.SaveDiagramRequest(
                        path = "/no/such/dir",
                        filename = "diagram.puml",
                        content = "content",
                    ),
            )
        assertEquals(400, resp.statusCode.value())
        assertEquals("code_analysis.be_dir_not_found_save", resp.body?.get("error"))
    }

    @Test
    fun `saveDiagram returns 500 with the translation key when the write fails`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
        val resp =
            controller.saveDiagram(
                analysisId = 1L,
                req =
                    CodeAnalysisApiController.SaveDiagramRequest(
                        path = "/tmp",
                        filename = "diagram.puml",
                        content = "content",
                    ),
            )
        assertEquals(500, resp.statusCode.value())
        assertEquals("code_analysis.be_save_failed", resp.body?.get("error"))
    }

    @Test
    fun `saveDiagram returns the saved path from the use case`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved("/tmp/sub/diagram.puml"))
        val resp =
            controller.saveDiagram(
                analysisId = 1L,
                req =
                    CodeAnalysisApiController.SaveDiagramRequest(
                        path = "/tmp/sub",
                        filename = "diagram.puml",
                        content = "x",
                    ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals("/tmp/sub/diagram.puml", body["savedPath"])
    }

    @Test
    fun `saveDiagram forwards the request fields to the use case`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved("/tmp/sub/passwd"))
        controller.saveDiagram(
            analysisId = 1L,
            req =
                CodeAnalysisApiController.SaveDiagramRequest(
                    path = "/tmp/sub",
                    filename = "../../etc/passwd",
                    content = "diagram content",
                ),
        )
        verify(workspaceFiles).saveText(
            TextFileSaveRequest(
                directory = "/tmp/sub",
                filename = "../../etc/passwd",
                content = "diagram content",
            ),
        )
    }

    @Test
    fun `saveDiagram renders a failed write through the injected i18n port`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
        whenever(i18n.t("code_analysis.be_save_failed", "")).thenReturn("Fehler beim Speichern: ")
        val resp =
            controller.saveDiagram(
                analysisId = 1L,
                req =
                    CodeAnalysisApiController.SaveDiagramRequest(
                        path = "/tmp",
                        filename = "diagram.puml",
                        content = "x",
                    ),
            )
        assertEquals(500, resp.statusCode.value())
        assertEquals("Fehler beim Speichern: ", resp.body?.get("error"))
    }

    @Test
    fun `StartLocalDto stores repoConfigId`() {
        val d = CodeAnalysisApiController.StartLocalDto(repoConfigId = 7L)
        assertEquals(7L, d.repoConfigId)
    }

    @Test
    fun `StartGitlabDto stores all fields`() {
        val d =
            CodeAnalysisApiController.StartGitlabDto(
                gitlabConfigId = 1L,
                projectId = 2L,
                projectPath = "g/p",
            )
        assertEquals(1L, d.gitlabConfigId)
        assertEquals(2L, d.projectId)
        assertEquals("g/p", d.projectPath)
    }

    @Test
    fun `StartGithubDto stores githubRepoConfigId`() {
        val d = CodeAnalysisApiController.StartGithubDto(githubRepoConfigId = 5L)
        assertEquals(5L, d.githubRepoConfigId)
    }

    @Test
    fun `CreateMrDto defaults gitlabConfigId and projectId to null`() {
        val d = CodeAnalysisApiController.CreateMrDto()
        assertEquals(null, d.gitlabConfigId)
        assertEquals(null, d.projectId)
    }

    @Test
    fun `SaveDiagramRequest stores all fields`() {
        val r =
            CodeAnalysisApiController.SaveDiagramRequest(
                path = "/tmp",
                filename = "diagram.puml",
                content = "x",
            )
        assertEquals("/tmp", r.path)
        assertEquals("diagram.puml", r.filename)
        assertEquals("x", r.content)
    }
}
