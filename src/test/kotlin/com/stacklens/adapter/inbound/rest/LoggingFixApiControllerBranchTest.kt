package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.logging.CancelOutcome
import com.stacklens.application.port.inbound.logging.DeleteOutcome
import com.stacklens.application.port.inbound.logging.LoggingFixUseCase
import com.stacklens.application.port.inbound.logging.StageOutputs
import com.stacklens.application.port.inbound.logging.StageState
import com.stacklens.application.port.inbound.logging.WriteFileEntry
import com.stacklens.application.port.inbound.logging.WriteFilesPreview
import com.stacklens.application.port.inbound.logging.WriteFilesResult
import com.stacklens.domain.model.LoggingFixAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class LoggingFixApiControllerBranchTest {
    private val useCase: LoggingFixUseCase = mock()
    private val i18n: com.stacklens.application.port.inbound.platform.I18nUseCase = mock()
    private val controller = LoggingFixApiController(useCase, useCase, useCase, useCase, i18n)

    private fun analysis(): LoggingFixAnalysis =
        LoggingFixAnalysis(
            id = 5L,
            featureSlug = "be",
            status = "RUNNING",
            language = "de",
            logText = "text",
            selectedCategories = "error,warn",
            localRepoConfigId = 3L,
            errorMessage = null,
            createdAt = LocalDateTime.of(2025, 1, 1, 10, 0),
            completedAt = LocalDateTime.of(2025, 1, 1, 10, 5),
        )

    @Test
    fun `status returns analysis with stage outputs`() {
        whenever(useCase.getAnalysis(5L)).thenReturn(analysis())
        whenever(useCase.getStageOutputs(5L)).thenReturn(
            StageOutputs(
                id = 5L,
                status = "RUNNING",
                currentStage = "code",
                stages = mapOf("plan" to StageState("plan", "Plan", "DONE", "output")),
                errorMessage = null,
                repoPath = "/repo",
            ),
        )
        val resp = controller.status(5L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(5L, body["id"])
        assertEquals("RUNNING", body["status"])
        assertEquals("de", body["language"])
        assertEquals(listOf("error", "warn"), body["selectedCategories"])
        assertEquals(3L, body["localRepoConfigId"])
        assertEquals("code", body["currentStage"])
        assertEquals(false, body["completedAt"].toString().isBlank())
        assertTrue((body["stages"] as Map<*, *>).isNotEmpty())
    }

    @Test
    fun `status returns not found when analysis missing`() {
        whenever(useCase.getAnalysis(5L)).thenReturn(null)
        assertEquals(HttpStatus.NOT_FOUND, controller.status(5L).statusCode)
    }

    @Test
    fun `list maps analyses and coerces limit`() {
        val a = analysis()
        whenever(useCase.listRecent(100)).thenReturn(listOf(a))
        assertEquals(1, controller.list(10_000).size)
        whenever(useCase.listRecent(1)).thenReturn(listOf(a))
        assertEquals(1, controller.list(0).size)
        whenever(useCase.listRecent(50)).thenReturn(listOf(a))
        val result = controller.list(50)
        assertEquals(5L, result.single()["id"])
        assertEquals(listOf("error", "warn"), result.single()["selectedCategories"])
    }

    @Test
    fun `cancel maps all outcomes`() {
        whenever(useCase.cancel(1L)).thenReturn(CancelOutcome.Cancelled)
        assertEquals(HttpStatus.OK, controller.cancel(1L).statusCode)
        whenever(useCase.cancel(2L)).thenReturn(CancelOutcome.NotFound)
        assertEquals(HttpStatus.NOT_FOUND, controller.cancel(2L).statusCode)
        whenever(useCase.cancel(3L)).thenReturn(CancelOutcome.AlreadyFinished)
        assertEquals(HttpStatus.CONFLICT, controller.cancel(3L).statusCode)
    }

    @Test
    fun `delete maps all outcomes`() {
        whenever(useCase.delete(1L)).thenReturn(DeleteOutcome.Deleted)
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(1L).statusCode)
        whenever(useCase.delete(2L)).thenReturn(DeleteOutcome.NotFound)
        assertEquals(HttpStatus.NOT_FOUND, controller.delete(2L).statusCode)
        whenever(useCase.delete(3L)).thenReturn(DeleteOutcome.StillRunning)
        assertEquals(HttpStatus.CONFLICT, controller.delete(3L).statusCode)
    }

    @Test
    fun `deleteAllFinished returns deleted count`() {
        whenever(useCase.deleteAllFinished()).thenReturn(7)
        val resp = controller.deleteAllFinished()
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(7, resp.body!!["deleted"])
    }

    @Test
    fun `previewWriteFiles returns preview`() {
        whenever(useCase.getWriteFilesPreview(5L)).thenReturn(
            WriteFilesPreview(
                repoPath = "/repo",
                files = listOf(WriteFileEntry("/repo/a.java", "code", "desc")),
            ),
        )
        val resp = controller.previewWriteFiles(5L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("/repo", body["repoPath"])
        val files = body["files"] as List<*>
        assertEquals("code", (files.single() as Map<*, *>)["content"])
    }

    @Test
    fun `previewWriteFiles returns not found when preview missing`() {
        whenever(useCase.getWriteFilesPreview(5L)).thenReturn(null)
        assertEquals(HttpStatus.NOT_FOUND, controller.previewWriteFiles(5L).statusCode)
    }

    @Test
    fun `writeFiles returns success result`() {
        whenever(useCase.getAnalysis(5L)).thenReturn(analysis())
        whenever(useCase.writeFiles(5L)).thenReturn(WriteFilesResult(success = true, writtenFiles = listOf("a.java")))
        val resp = controller.writeFiles(5L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(true, resp.body!!["success"])
        assertEquals(listOf("a.java"), resp.body!!["writtenFiles"])
    }

    @Test
    fun `writeFiles returns 500 on failure`() {
        whenever(useCase.getAnalysis(5L)).thenReturn(analysis())
        whenever(useCase.writeFiles(5L)).thenReturn(WriteFilesResult(success = false, error = "boom"))
        val resp = controller.writeFiles(5L)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
        assertEquals("boom", resp.body!!["error"])
    }

    @Test
    fun `writeFiles returns 404 when analysis missing`() {
        whenever(useCase.getAnalysis(5L)).thenReturn(null)
        assertEquals(HttpStatus.NOT_FOUND, controller.writeFiles(5L).statusCode)
    }
}
