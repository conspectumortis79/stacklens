package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.logging.LoggingFixUseCase
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class LoggingFixApiControllerNullablesCoverageTest {
    private val useCase: LoggingFixUseCase = mock()
    private val i18n = FakeI18n()
    private val controller = LoggingFixApiController(useCase, useCase, useCase, useCase, i18n)

    private fun analysis(categories: String): LoggingFixAnalysis =
        LoggingFixAnalysis(
            id = 11L,
            featureSlug = "slug",
            status = LoggingFixAnalysis.STATUS_DONE,
            language = "en",
            logText = "log",
            selectedCategories = categories,
            createdAt = LocalDateTime.of(2025, 3, 4, 5, 6),
            completedAt = null,
        )

    @Test
    fun `status tolerates a missing stage snapshot, a missing completion and blank categories`() {
        whenever(useCase.getAnalysis(11L)).thenReturn(analysis(",, "))
        whenever(useCase.getStageOutputs(11L)).thenReturn(null)

        val resp = controller.status(11L)

        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertNull(body["stages"])
        assertNull(body["currentStage"])
        assertNull(body["completedAt"])
        assertEquals(emptyList<String>(), body["selectedCategories"])
    }

    @Test
    fun `status filters the blank segments out of a partially empty category list`() {
        whenever(useCase.getAnalysis(11L)).thenReturn(analysis("error,,warn"))
        whenever(useCase.getStageOutputs(11L)).thenReturn(null)

        @Suppress("UNCHECKED_CAST")
        val body = controller.status(11L).body as Map<String, Any?>

        assertEquals(listOf("error", "warn"), body["selectedCategories"])
    }

    @Test
    fun `list drops blank category segments`() {
        whenever(useCase.listRecent(5)).thenReturn(listOf(analysis("a,,b")))

        val first = controller.list(5).first()

        assertEquals(listOf("a", "b"), first["selectedCategories"])
    }
}
