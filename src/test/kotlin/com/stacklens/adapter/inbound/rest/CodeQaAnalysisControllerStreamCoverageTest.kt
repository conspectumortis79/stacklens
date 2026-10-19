package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CodeQaAnalysisControllerStreamCoverageTest {
    private val analysisService = mock<CodeQaAnalysisUseCase>()
    private val controller = CodeQaAnalysisApiController(analysisService, mock<I18nUseCase>())

    private val id = 11L

    private fun givenAnalysis(
        status: String,
        resultMarkdown: String? = null,
        errorMessage: String? = null,
        integrationTestStatus: String? = null,
        integrationTestLog: String? = null,
    ) {
        whenever(analysisService.getAnalysis(id)).thenReturn(
            CodeQaAnalysis(
                id = id,
                mrUrl = "https://gitlab.example/group/repo/-/merge_requests/4",
                status = status,
                resultMarkdown = resultMarkdown,
                errorMessage = errorMessage,
                integrationTestStatus = integrationTestStatus,
                integrationTestLog = integrationTestLog,
            ),
        )
    }

    @Test
    fun `a completed analysis is streamed immediately without a polling task`() {
        givenAnalysis("COMPLETED", resultMarkdown = "# Result")

        assertEquals(0L, controller.stream(id).timeout)
    }

    @Test
    fun `a completed analysis without a markdown result still finishes`() {
        givenAnalysis("COMPLETED")

        assertEquals(0L, controller.stream(id).timeout)
    }

    @Test
    fun `a failed analysis reports its error message`() {
        givenAnalysis("FAILED", errorMessage = "copilot crashed")

        assertEquals(0L, controller.stream(id).timeout)
    }

    @Test
    fun `a failed analysis without an error message still terminates`() {
        givenAnalysis("FAILED")

        assertEquals(0L, controller.stream(id).timeout)
    }

    @Test
    fun `a cancelled analysis is closed right away`() {
        givenAnalysis("CANCELLED")

        assertEquals(0L, controller.stream(id).timeout)
    }

    @Test
    fun `an unknown analysis id completes with an error`() {
        whenever(analysisService.getAnalysis(id)).thenReturn(null)

        assertEquals(0L, controller.stream(id).timeout)
    }

    @Test
    fun `a finished integration test streams its log and status`() {
        givenAnalysis("COMPLETED", integrationTestStatus = "COMPLETED", integrationTestLog = "docker compose up\n1 passed")

        assertEquals(0L, controller.integrationTestStream(id).timeout)
    }

    @Test
    fun `a finished integration test without a log only reports the status`() {
        givenAnalysis("COMPLETED", integrationTestStatus = "COMPLETED", integrationTestLog = "   ")

        assertEquals(0L, controller.integrationTestStream(id).timeout)
    }

    @Test
    fun `a failed integration test is reported through the fast path`() {
        givenAnalysis("COMPLETED", integrationTestStatus = "FAILED")

        assertEquals(0L, controller.integrationTestStream(id).timeout)
    }

    @Test
    fun `a cancelled integration test is reported through the fast path`() {
        givenAnalysis("COMPLETED", integrationTestStatus = "CANCELLED")

        assertEquals(0L, controller.integrationTestStream(id).timeout)
    }

    @Test
    fun `an unknown integration test stream completes with an error`() {
        whenever(analysisService.getAnalysis(id)).thenReturn(null)

        assertEquals(0L, controller.integrationTestStream(id).timeout)
    }
}
