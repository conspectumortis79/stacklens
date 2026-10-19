package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.security.SecurityAnalysisUseCase
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.security.SecurityFixResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class SecurityAnalysisApiControllerTest {
    private val service: SecurityAnalysisUseCase = mock()
    private val controller = SecurityAnalysisApiController(service)

    private fun analysis(id: Long = 1L) = SecurityAnalysis(id = id, status = "COMPLETED")

    @Test
    fun `startLocal returns analysisId and status`() {
        val a = analysis(10L)
        whenever(service.startLocalAnalysis(5L, 3)).thenReturn(a)
        val resp = controller.startLocal(SecurityAnalysisApiController.StartLocalDto(repoConfigId = 5L))
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(10L, body["analysisId"])
        assertEquals("COMPLETED", body["status"])
    }

    @Test
    fun `startGitlab returns analysisId and status`() {
        val a = analysis(20L)
        whenever(service.startGitlabAnalysis(1L, 2L, "group/proj", 3)).thenReturn(a)
        val dto = SecurityAnalysisApiController.StartGitlabDto(gitlabConfigId = 1L, projectId = 2L, projectPath = "group/proj")
        val resp = controller.startGitlab(dto)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(20L, body["analysisId"])
    }

    @Test
    fun `startGithub returns analysisId and status`() {
        val a = analysis(30L)
        whenever(service.startGithubAnalysis(7L, 3)).thenReturn(a)
        val resp = controller.startGithub(SecurityAnalysisApiController.StartGithubDto(githubRepoConfigId = 7L))
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(30L, body["analysisId"])
    }

    @Test
    fun `status returns 404 when analysis not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.status(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `status returns analysis map when found`() {
        val a = analysis(1L)
        whenever(service.getAnalysis(1L)).thenReturn(a)
        val resp = controller.status(1L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(1L, body["id"])
        assertEquals("COMPLETED", body["status"])
    }

    @Test
    fun `all returns list of analyses`() {
        whenever(service.getAllAnalyses()).thenReturn(listOf(analysis(1L), analysis(2L)))
        val result = controller.all()
        assertEquals(2, result.size)
    }

    @Test
    fun `cancel returns status after cancellation`() {
        val cancelled = analysis(5L).also { it.status = "ABGEBROCHEN" }
        whenever(service.getAnalysis(5L)).thenReturn(cancelled)
        val resp = controller.cancel(5L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("ABGEBROCHEN", body["status"])
    }

    @Test
    fun `cancel returns FAILED status when analysis no longer exists after cancel`() {
        whenever(service.getAnalysis(6L)).thenReturn(null)
        val resp = controller.cancel(6L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("FAILED", body["status"])
    }

    @Test
    fun `delete returns 404 when analysis not found`() {
        whenever(service.getAnalysis(7L)).thenReturn(null)
        val resp = controller.delete(7L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
        verify(service, never()).deleteAnalysis(any())
    }

    @Test
    fun `delete returns 204 when analysis found and deleted`() {
        whenever(service.getAnalysis(8L)).thenReturn(analysis(8L))
        val resp = controller.delete(8L)
        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
        verify(service).deleteAnalysis(8L)
    }

    @Test
    fun `generateFix returns fix result on success`() {
        val fixResult =
            SecurityFixResult(
                prUrl = "https://gitlab.example.com/pr/1",
                branchName = "fix/vuln-1",
                explanation = "Fixed SQL injection",
                filesChanged = listOf("src/main.kt"),
                isLocalRepo = false,
                localChanges = null,
            )
        whenever(service.generateFix(1L, "F1")).thenReturn(fixResult)
        val resp = controller.generateFix(1L, "F1")
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("https://gitlab.example.com/pr/1", body["prUrl"])
        assertEquals("fix/vuln-1", body["branchName"])
    }

    @Test
    fun `generateFix returns 500 when service throws exception`() {
        whenever(service.generateFix(2L, "F2")).thenThrow(RuntimeException("git error"))
        val resp = controller.generateFix(2L, "F2")
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }

    @Test
    fun `generateFix maps local changes when isLocalRepo is true`() {
        val localChange =
            com.stacklens.domain.model.security.SecurityLocalFileChange(
                path = "src/Service.kt",
                code = "fun safe() {}",
                changeType = FileChangeType.UPDATE,
            )
        val fixResult =
            SecurityFixResult(
                prUrl = null,
                branchName = null,
                explanation = "Local fix",
                filesChanged = listOf("src/Service.kt"),
                isLocalRepo = true,
                localChanges = listOf(localChange),
            )
        whenever(service.generateFix(3L, "F3")).thenReturn(fixResult)
        val resp = controller.generateFix(3L, "F3")
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val changes = body["localChanges"] as List<Map<String, Any?>>
        assertEquals(1, changes.size)
        assertEquals("src/Service.kt", changes[0]["path"])
    }

    @Test
    fun `generateFix returns default error message when exception has no message`() {
        whenever(service.generateFix(4L, "F4")).thenThrow(RuntimeException())
        val resp = controller.generateFix(4L, "F4")
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("Unbekannter Fehler", body["error"])
    }
}
