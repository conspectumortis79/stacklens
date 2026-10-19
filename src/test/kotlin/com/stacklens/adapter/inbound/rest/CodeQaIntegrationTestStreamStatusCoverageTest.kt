package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.testsupport.HashCollisions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CodeQaIntegrationTestStreamStatusCoverageTest {
    private val service = mock<CodeQaAnalysisUseCase>()
    private val controller = CodeQaAnalysisApiController(service, mock<I18nUseCase>())

    private fun givenStatus(status: String?) {
        whenever(service.getAnalysis(1L)).thenReturn(
            CodeQaAnalysis(
                id = 1L,
                mrUrl = "https://gitlab.example.com/group/repo/-/merge_requests/1",
                status = "COMPLETED",
                integrationTestStatus = status,
            ),
        )
    }

    @Test
    fun `an analysis without an integration test status keeps the long living stream`() {
        givenStatus(null)

        assertEquals(30 * 60 * 1000L, controller.integrationTestStream(1L).timeout)
    }

    @ParameterizedTest
    @ValueSource(strings = ["PENDING", "RUNNING", "COMPLITE"])
    fun `a status that is not terminal keeps the long living stream`(status: String) {
        givenStatus(status)

        assertEquals(30 * 60 * 1000L, controller.integrationTestStream(1L).timeout)
    }

    @ParameterizedTest
    @ValueSource(strings = ["COMPLETED", "FAILED", "CANCELLED"])
    fun `a value that only collides with a terminal hash keeps the long living stream`(status: String) {
        givenStatus(HashCollisions.sharingHashCodeOf(status))

        assertEquals(30 * 60 * 1000L, controller.integrationTestStream(1L).timeout)
    }
}
