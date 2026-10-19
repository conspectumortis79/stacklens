package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.loadtest.K6StatusResult
import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.LoadTestAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LoadTestApiControllerFullTest {
    private lateinit var service: LoadTestAnalysisUseCase
    private lateinit var workspaceFiles: WorkspaceFileUseCase
    private lateinit var i18n: I18nUseCase
    private lateinit var controller: LoadTestApiController

    @BeforeEach
    fun setUp() {
        service = mock()
        workspaceFiles = mock()
        i18n =
            mock(defaultAnswer = org.mockito.stubbing.Answer { it.arguments.firstOrNull() ?: "" })
        controller = LoadTestApiController(service, workspaceFiles, i18n)
    }

    private fun analysis(
        id: Long = 1L,
        status: String = "COMPLETED",
    ) = LoadTestAnalysis(
        id = id,
        gitlabProjectPath = "/tmp/repo",
        status = status,
    )

    private fun k6Result(
        running: Boolean = false,
        exitCode: Int? = 0,
        output: String = "output",
        error: String = "",
        summaryJson: String? = "{}",
    ) = K6StatusResult(
        running = running,
        exitCode = exitCode,
        output = output,
        error = error,
        summaryJson = summaryJson,
    )

    @Test
    fun `startGitlab returns analysisId and status`() {
        val a = analysis(42L, status = "RUNNING")
        whenever(service.startGitlabAnalysis(1L, 100L, "g/p")).thenReturn(a)

        val resp =
            controller.startGitlab(
                StartLoadTestDto(gitlabConfigId = 1L, projectId = 100L, projectPath = "g/p"),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(42L, body["analysisId"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `startLocal returns analysisId and status`() {
        val a = analysis(7L, status = "RUNNING")
        whenever(service.startLocalAnalysis(5L)).thenReturn(a)

        val resp =
            controller.startLocal(StartLocalLoadTestDto(repoConfigId = 5L))
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(7L, body["analysisId"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `startGithub returns analysisId and status`() {
        val a = analysis(11L, status = "RUNNING")
        whenever(service.startGithubAnalysis(3L)).thenReturn(a)

        val resp =
            controller.startGithub(StartGithubLoadTestDto(githubRepoConfigId = 3L))
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(11L, body["analysisId"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `status returns mapped DTO when analysis exists`() {
        val a = analysis(1L)
        whenever(service.getAnalysis(1L)).thenReturn(a)
        val resp = controller.status(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(1L, body["id"])
    }

    @Test
    fun `status returns 404 when analysis not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.status(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `all returns mapped list of analyses`() {
        whenever(service.getAllAnalyses()).thenReturn(listOf(analysis(1L), analysis(2L)))
        val list = controller.all()
        assertEquals(2, list.size)
    }

    @Test
    fun `all returns empty list when no analyses`() {
        whenever(service.getAllAnalyses()).thenReturn(emptyList())
        val list = controller.all()
        assertEquals(0, list.size)
    }

    @Test
    fun `delete returns 204 when analysis exists`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.delete(1L)
        assertEquals(204, resp.statusCode.value())
        verify(service).deleteAnalysis(1L)
    }

    @Test
    fun `delete returns 404 when analysis not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.delete(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `cancel returns 200 with status from analysis when it exists`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L, status = "FAILED"))
        val resp = controller.cancel(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("FAILED", body["status"])
        verify(service).cancelAnalysis(1L)
    }

    @Test
    fun `cancel returns 200 with FAILED when analysis is gone after cancel`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.cancel(99L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("FAILED", body["status"])
    }

    @Test
    fun `latestGitlab returns mapped analysis when present`() {
        val a = analysis(50L)
        whenever(service.getLatestGitlabAnalysis(1L, 100L)).thenReturn(a)
        val resp = controller.latestGitlab(1L, 100L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(50L, body["id"])
    }

    @Test
    fun `latestGitlab returns NONE when no analysis`() {
        whenever(service.getLatestGitlabAnalysis(1L, 100L)).thenReturn(null)
        val resp = controller.latestGitlab(1L, 100L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("NONE", body["status"])
    }

    @Test
    fun `latestLocal returns mapped analysis when present`() {
        val a = analysis(60L)
        whenever(service.getLatestLocalAnalysis(1L)).thenReturn(a)
        val resp = controller.latestLocal(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(60L, body["id"])
    }

    @Test
    fun `latestLocal returns NONE when no analysis`() {
        whenever(service.getLatestLocalAnalysis(1L)).thenReturn(null)
        val resp = controller.latestLocal(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("NONE", body["status"])
    }

    @Test
    fun `latestGithub returns mapped analysis when present`() {
        val a = analysis(70L)
        whenever(service.getLatestGithubAnalysis(1L)).thenReturn(a)
        val resp = controller.latestGithub(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(70L, body["id"])
    }

    @Test
    fun `latestGithub returns NONE when no analysis`() {
        whenever(service.getLatestGithubAnalysis(1L)).thenReturn(null)
        val resp = controller.latestGithub(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("NONE", body["status"])
    }

    @Test
    fun `runK6 forwards all dto fields to service`() {
        whenever(
            service.startK6Script(1L, "tok-123", "staging", "import http from 'k6/http'"),
        ).thenReturn(k6Result(running = true, exitCode = 0, output = "started"))

        val resp =
            controller.runK6(
                analysisId = 1L,
                body =
                    RunTestDto(
                        bearerToken = "tok-123",
                        environmentName = "staging",
                        customScript = "import http from 'k6/http'",
                    ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(true, body["running"])
        assertEquals(0, body["exitCode"])
        assertEquals("started", body["output"])
    }

    @Test
    fun `runK6 handles null body with all-null fields`() {
        whenever(service.startK6Script(1L, null, null, null))
            .thenReturn(k6Result(running = false, exitCode = 1, error = "failed"))

        val resp = controller.runK6(analysisId = 1L, body = null)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(false, body["running"])
        assertEquals(1, body["exitCode"])
        assertEquals("failed", body["error"])
    }

    @Test
    fun `runK6 handles null exitCode while still running`() {
        whenever(service.startK6Script(1L, null, null, null))
            .thenReturn(k6Result(running = true, exitCode = null, output = "running"))

        val resp = controller.runK6(analysisId = 1L, body = null)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(true, body["running"])
        assertEquals(null, body["exitCode"])
    }

    @Test
    fun `k6Status returns the current k6 result`() {
        whenever(service.getK6Status(1L))
            .thenReturn(k6Result(running = false, exitCode = 0, summaryJson = """{"metrics":[]}"""))

        val resp = controller.k6Status(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(false, body["running"])
        assertEquals("""{"metrics":[]}""", body["summaryJson"])
    }

    @Test
    fun `stopK6 returns stopped true and delegates to service`() {
        val resp = controller.stopK6(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(true, body["stopped"])
        verify(service).stopK6Script(1L)
    }

    @Test
    fun `saveScript returns 400 with the translation key when the directory is missing`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val resp =
            controller.saveScript(
                analysisId = 1L,
                req =
                    LoadTestApiController.SaveScriptRequest(
                        path = "/no/such/dir",
                        filename = "test.js",
                        content = "x",
                    ),
            )
        assertEquals(400, resp.statusCode.value())
        assertEquals("load_test.be_dir_not_found", resp.body?.get("error"))
    }

    @Test
    fun `saveScript returns 500 with the translation key when the write fails`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
        val resp =
            controller.saveScript(
                analysisId = 1L,
                req =
                    LoadTestApiController.SaveScriptRequest(
                        path = "/tmp",
                        filename = "test.js",
                        content = "x",
                    ),
            )
        assertEquals(500, resp.statusCode.value())
        assertTrue(
            resp.body
                ?.get("error")
                .toString()
                .startsWith("load_test.be_save_failed"),
        )
    }

    @Test
    fun `saveScript returns the saved path from the use case`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved("/tmp/scripts/test.js"))
        val resp =
            controller.saveScript(
                analysisId = 1L,
                req =
                    LoadTestApiController.SaveScriptRequest(
                        path = "/tmp/scripts",
                        filename = "test.js",
                        content = "x",
                    ),
            )
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals("/tmp/scripts/test.js", body["savedPath"])
    }

    @Test
    fun `saveScript forwards the request fields to the use case`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved("/tmp/scripts/passwd"))
        controller.saveScript(
            analysisId = 1L,
            req =
                LoadTestApiController.SaveScriptRequest(
                    path = "/tmp/scripts",
                    filename = "../../../etc/passwd",
                    content = "console.log('hi');",
                ),
        )
        verify(workspaceFiles).saveText(
            TextFileSaveRequest(
                directory = "/tmp/scripts",
                filename = "../../../etc/passwd",
                content = "console.log('hi');",
            ),
        )
    }

    @Test
    fun `saveScript renders a failed write through the injected i18n port`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
        whenever(i18n.t("load_test.be_save_failed", "")).thenReturn("Fehler beim Speichern: ")
        val resp =
            controller.saveScript(
                analysisId = 1L,
                req =
                    LoadTestApiController.SaveScriptRequest(
                        path = "/tmp",
                        filename = "test.js",
                        content = "x",
                    ),
            )
        assertEquals(500, resp.statusCode.value())
        assertEquals("Fehler beim Speichern: ", resp.body?.get("error"))
    }

    @Test
    fun `StartLoadTestDto stores all fields`() {
        val d = StartLoadTestDto(gitlabConfigId = 1L, projectId = 2L, projectPath = "g/p")
        assertEquals(1L, d.gitlabConfigId)
        assertEquals(2L, d.projectId)
        assertEquals("g/p", d.projectPath)
    }

    @Test
    fun `StartLocalLoadTestDto stores repoConfigId`() {
        val d = StartLocalLoadTestDto(repoConfigId = 5L)
        assertEquals(5L, d.repoConfigId)
    }

    @Test
    fun `StartGithubLoadTestDto stores githubRepoConfigId`() {
        val d = StartGithubLoadTestDto(githubRepoConfigId = 3L)
        assertEquals(3L, d.githubRepoConfigId)
    }

    @Test
    fun `RunTestDto defaults all fields to null`() {
        val d = RunTestDto()
        assertEquals(null, d.bearerToken)
        assertEquals(null, d.environmentName)
        assertEquals(null, d.customScript)
    }

    @Test
    fun `RunTestDto stores provided values`() {
        val d = RunTestDto(bearerToken = "t", environmentName = "e", customScript = "c")
        assertEquals("t", d.bearerToken)
        assertEquals("e", d.environmentName)
        assertEquals("c", d.customScript)
    }

    @Test
    fun `SaveScriptRequest stores all fields`() {
        val r =
            LoadTestApiController.SaveScriptRequest(
                path = "/tmp",
                filename = "test.js",
                content = "console.log(1)",
            )
        assertEquals("/tmp", r.path)
        assertEquals("test.js", r.filename)
        assertEquals("console.log(1)", r.content)
    }
}
