package com.stacklens.adapter.inbound.rest

import com.stacklens.application.service.CodeQaAnalysisService
import com.stacklens.application.service.I18nService
import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CodeQaAnalysisApiControllerTest {
    private val service: CodeQaAnalysisService = mock()
    private val i18n: I18nService = mock()
    private val controller = CodeQaAnalysisApiController(service, i18n)

    private fun analysis(
        id: Long = 1L,
        status: String = "COMPLETED",
        completedAt: java.time.LocalDateTime? = null,
    ) = CodeQaAnalysis(
        id = id,
        mrUrl = "https://gitlab.example.com/group/repo/-/merge_requests/1",
        status = status,
        completedAt = completedAt,
    )

    @Test
    fun `start returns 400 when mrUrl is blank`() {
        whenever(i18n.t("mr_analysis.be_err_url_empty")).thenReturn("URL required")
        val resp = controller.start(mapOf("mrUrl" to ""))
        assertEquals(400, resp.statusCode.value())
        assertEquals("URL required", resp.body?.get("error"))
    }

    @Test
    fun `start returns 400 when mrUrl is missing`() {
        whenever(i18n.t("mr_analysis.be_err_url_empty")).thenReturn("URL required")
        val resp = controller.start(emptyMap())
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `start returns 200 with analysis id and status`() {
        val a = analysis(5L)
        whenever(
            service.startAnalysis(
                "https://gitlab.example.com/group/repo/-/merge_requests/1",
                null,
                null,
                null,
                null,
                null,
            ),
        ).thenReturn(a)
        val resp = controller.start(mapOf("mrUrl" to "https://gitlab.example.com/group/repo/-/merge_requests/1"))
        assertEquals(200, resp.statusCode.value())
        assertEquals(5L, resp.body?.get("id"))
        assertEquals("COMPLETED", resp.body?.get("status"))
    }

    @Test
    fun `start forwards optional fields to service`() {
        val a = analysis(2L)
        whenever(
            service.startAnalysis(
                "https://gitlab.example.com/group/repo/-/merge_requests/1",
                "PROJ-99",
                10L,
                7L,
                "gitlab",
                "check security",
            ),
        ).thenReturn(a)
        val resp =
            controller.start(
                mapOf(
                    "mrUrl" to "https://gitlab.example.com/group/repo/-/merge_requests/1",
                    "jiraTicketKey" to "PROJ-99",
                    "jiraConfigId" to "10",
                    "gitConfigId" to "7",
                    "gitPlatform" to "GITLAB",
                    "additionalInstructions" to "check security",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        verify(service).startAnalysis(
            "https://gitlab.example.com/group/repo/-/merge_requests/1",
            "PROJ-99",
            10L,
            7L,
            "gitlab",
            "check security",
        )
    }

    @Test
    fun `start ignores blank optional fields`() {
        val a = analysis(3L)
        whenever(
            service.startAnalysis("https://gitlab.example.com/group/repo/-/merge_requests/1", null, null, null, null, null),
        ).thenReturn(a)
        val resp =
            controller.start(
                mapOf(
                    "mrUrl" to "https://gitlab.example.com/group/repo/-/merge_requests/1",
                    "jiraTicketKey" to "  ",
                    "jiraConfigId" to "not-a-number",
                    "gitPlatform" to "  ",
                    "additionalInstructions" to "  ",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        verify(service).startAnalysis("https://gitlab.example.com/group/repo/-/merge_requests/1", null, null, null, null, null)
    }

    @Test
    fun `listAll returns empty list when no analyses`() {
        whenever(service.getAllAnalyses()).thenReturn(emptyList())
        val resp = controller.listAll()
        assertEquals(200, resp.statusCode.value())
        assertEquals(0, resp.body?.size)
    }

    @Test
    fun `listAll returns list of analyses`() {
        whenever(service.getAllAnalyses()).thenReturn(listOf(analysis(1L), analysis(2L)))
        val resp = controller.listAll()
        assertEquals(200, resp.statusCode.value())
        assertEquals(2, resp.body?.size)
    }

    @Test
    fun `getResult returns 404 when not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.getResult(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `getResult returns full map when found`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.getResult(1L)
        assertEquals(200, resp.statusCode.value())
        assertEquals(1L, resp.body?.get("id"))
        assertEquals("https://gitlab.example.com/group/repo/-/merge_requests/1", resp.body?.get("mrUrl"))
    }

    @Test
    fun `getStatus returns 404 when not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.getStatus(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `getStatus returns status map when found`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.getStatus(1L)
        assertEquals(200, resp.statusCode.value())
        assertEquals("COMPLETED", resp.body?.get("status"))
    }

    @Test
    fun `deleteOne returns 404 when not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.deleteOne(99L)
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `deleteOne returns 200 when found`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.deleteOne(1L)
        assertEquals(200, resp.statusCode.value())
        assertEquals(true, resp.body?.get("deleted"))
        verify(service).deleteAnalysis(1L)
    }

    @Test
    fun `deleteHistory returns 200 with deleted true`() {
        val resp = controller.deleteHistory()
        assertEquals(200, resp.statusCode.value())
        assertEquals(true, resp.body?.get("deleted"))
        verify(service).deleteAllAnalyses()
    }

    @Test
    fun `cancel returns status from analysis after cancellation`() {
        val a = analysis(1L, "FAILED")
        whenever(service.getAnalysis(1L)).thenReturn(a)
        val resp = controller.cancel(1L)
        assertEquals(200, resp.statusCode.value())
        assertEquals("FAILED", resp.body?.get("status"))
        verify(service).cancelAnalysis(1L)
    }

    @Test
    fun `cancel returns FAILED status when analysis gone after cancel`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.cancel(99L)
        assertEquals(200, resp.statusCode.value())
        assertEquals("FAILED", resp.body?.get("status"))
    }

    @Test
    fun `getStatus returns completedAt when set`() {
        val completed = analysis(1L, completedAt = java.time.LocalDateTime.of(2024, 1, 15, 10, 30))
        whenever(service.getAnalysis(1L)).thenReturn(completed)
        val resp = controller.getStatus(1L)
        assertEquals(200, resp.statusCode.value())
        assertNotNull(resp.body?.get("completedAt"))
    }

    @Test
    fun `getResult returns completedAt when set`() {
        val completed = analysis(1L, completedAt = java.time.LocalDateTime.of(2024, 1, 15, 10, 30))
        whenever(service.getAnalysis(1L)).thenReturn(completed)
        val resp = controller.getResult(1L)
        assertEquals(200, resp.statusCode.value())
        assertNotNull(resp.body?.get("completedAt"))
    }

    @Test
    fun `listAll returns completedAt when set`() {
        val completed = analysis(1L, completedAt = java.time.LocalDateTime.of(2024, 1, 15, 10, 30))
        whenever(service.getAllAnalyses()).thenReturn(listOf(completed))
        val resp = controller.listAll()
        assertEquals(200, resp.statusCode.value())
        assertNotNull(resp.body?.first()?.get("completedAt"))
    }

    @Test
    fun `postSuggestion returns 404 when analysis not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.postSuggestion(99L, mapOf("filePath" to "src/A.kt", "lineNumber" to 5))
        assertEquals(404, resp.statusCode.value())
    }

    @Test
    fun `postSuggestion returns 400 when analysis not completed`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L, status = "RUNNING"))
        val resp = controller.postSuggestion(1L, mapOf("filePath" to "src/A.kt", "lineNumber" to 5))
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `postSuggestion returns 400 when filePath missing`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.postSuggestion(1L, mapOf("lineNumber" to 5))
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `postSuggestion returns 400 when lineNumber zero`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.postSuggestion(1L, mapOf("filePath" to "src/A.kt", "lineNumber" to 0))
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `postSuggestion returns url on success`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        whenever(service.postSuggestion(1L, "src/A.kt", 5, "val x = 1"))
            .thenReturn("https://gitlab.example.com/g/r/-/merge_requests/1#note_42")
        val resp = controller.postSuggestion(1L, mapOf("filePath" to "src/A.kt", "lineNumber" to 5, "suggestedCode" to "val x = 1"))
        assertEquals(200, resp.statusCode.value())
        assertEquals("https://gitlab.example.com/g/r/-/merge_requests/1#note_42", resp.body?.get("url"))
    }

    @Test
    fun `postSuggestion returns 400 when service throws`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        whenever(
            service.postSuggestion(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()),
        ).thenThrow(RuntimeException("no SHA"))
        val resp = controller.postSuggestion(1L, mapOf("filePath" to "src/A.kt", "lineNumber" to 3))
        assertEquals(400, resp.statusCode.value())
        assertEquals("no SHA", resp.body?.get("error"))
    }

    @Test
    fun `postSuggestion returns 400 when lineNumber is absent`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        val resp = controller.postSuggestion(1L, mapOf("filePath" to "src/A.kt"))
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `postSuggestion returns fallback message when exception message is null`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        whenever(
            service.postSuggestion(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()),
        ).thenThrow(RuntimeException(null as String?))
        val resp = controller.postSuggestion(1L, mapOf("filePath" to "src/A.kt", "lineNumber" to 3))
        assertEquals(400, resp.statusCode.value())
        assertEquals("Failed to post suggestion", resp.body?.get("error"))
    }

    @Test
    fun `postSuggestion returns connectivity hint when cause is connection refused`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(1L))
        whenever(
            service.postSuggestion(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()),
        ).thenThrow(RuntimeException("wrapper", java.net.ConnectException("Connection refused")))
        val resp = controller.postSuggestion(1L, mapOf("filePath" to "src/A.kt", "lineNumber" to 3))
        assertEquals(400, resp.statusCode.value())
        val msg = resp.body?.get("error") as String
        assert(msg.contains("Cannot connect"))
    }

    @Test
    fun `start forwards integrationTestEnabled true to service`() {
        val a = analysis(40L)
        whenever(
            service.startAnalysis(
                "https://gitlab.example.com/g/r/-/merge_requests/1",
                null,
                null,
                null,
                null,
                null,
                true,
            ),
        ).thenReturn(a)
        val resp =
            controller.start(
                mapOf(
                    "mrUrl" to "https://gitlab.example.com/g/r/-/merge_requests/1",
                    "integrationTestEnabled" to "TRUE",
                ),
            )
        assertEquals(200, resp.statusCode.value())
        verify(service).startAnalysis(
            "https://gitlab.example.com/g/r/-/merge_requests/1",
            null,
            null,
            null,
            null,
            null,
            true,
        )
    }

    @Test
    fun `start treats non-true integrationTestEnabled as false`() {
        val a = analysis(41L)
        whenever(
            service.startAnalysis(
                "https://gitlab.example.com/g/r/-/merge_requests/1",
                null,
                null,
                null,
                null,
                null,
                false,
            ),
        ).thenReturn(a)
        val resp =
            controller.start(
                mapOf(
                    "mrUrl" to "https://gitlab.example.com/g/r/-/merge_requests/1",
                    "integrationTestEnabled" to "no",
                ),
            )
        assertEquals(200, resp.statusCode.value())
    }
}

class CodeQaAnalysisApiControllerStreamBranchTest {
    private val service: CodeQaAnalysisService = mock()
    private val i18n: I18nService = mock()
    private val controller = CodeQaAnalysisApiController(service, i18n)

    @Test
    fun `stream returns emitter with error when analysis not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.stream(99L)
        assertNotNull(resp)
    }

    @Test
    fun `stream returns emitter with content when analysis COMPLETED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            CodeQaAnalysis(
                id = 1L,
                mrUrl = "https://gitlab/x",
                status = "COMPLETED",
                resultMarkdown = "final result",
            ),
        )
        val resp = controller.stream(1L)
        assertNotNull(resp)
    }

    @Test
    fun `stream returns emitter with error when analysis FAILED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            CodeQaAnalysis(
                id = 1L,
                mrUrl = "https://gitlab/x",
                status = "FAILED",
                errorMessage = "boom",
            ),
        )
        val resp = controller.stream(1L)
        assertNotNull(resp)
    }

    @Test
    fun `stream returns emitter with error when analysis CANCELLED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            CodeQaAnalysis(
                id = 1L,
                mrUrl = "https://gitlab/x",
                status = "CANCELLED",
            ),
        )
        val resp = controller.stream(1L)
        assertNotNull(resp)
    }

    @Test
    fun `integrationTestStream returns emitter when integration test COMPLETED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            CodeQaAnalysis(
                id = 1L,
                mrUrl = "https://gitlab/x",
                status = "COMPLETED",
                integrationTestStatus = "COMPLETED",
            ),
        )
        val resp = controller.integrationTestStream(1L)
        assertNotNull(resp)
    }

    @Test
    fun `integrationTestStream returns emitter when analysis missing`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val resp = controller.integrationTestStream(99L)
        assertNotNull(resp)
    }
}
