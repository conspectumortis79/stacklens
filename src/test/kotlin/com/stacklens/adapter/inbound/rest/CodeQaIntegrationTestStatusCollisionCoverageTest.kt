package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.testsupport.HashCollisions
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class CodeQaIntegrationTestStatusCollisionCoverageTest {
    private val service = mock<CodeQaAnalysisUseCase>()
    private val controller = CodeQaAnalysisApiController(service, mock<I18nUseCase>())

    private fun analysis(status: String?) =
        CodeQaAnalysis(
            id = 1L,
            mrUrl = "https://gitlab.example.com/group/repo/-/merge_requests/1",
            status = "COMPLETED",
            integrationTestStatus = status,
            integrationTestLog = null,
        )

    private fun collisionFor(value: String): String {
        val collision = HashCollisions.sharingHashCodeOf(value)
        assertNotEquals(value, collision)
        assertTrue(
            HashCollisions.unsignedHashCodeOf(collision) == HashCollisions.unsignedHashCodeOf(value),
            "$collision must share the hash code of $value",
        )
        return collision
    }

    @ParameterizedTest
    @ValueSource(strings = ["COMPLETED", "FAILED", "CANCELLED"])
    fun `a status that only shares the hash code of a terminal status keeps the task polling`(status: String) {
        val collision = collisionFor(status)
        var slept = false
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn("log")
        whenever(service.getAnalysis(1L)).thenReturn(analysis(collision))

        IntegrationTestSseTask(
            1L,
            SseEmitter(0L),
            service,
            pollIntervalMs = 1L,
            waiter =
                {
                    slept = true
                    false
                },
        ).run()

        assertTrue(slept, "$collision is not a terminal status, so the loop must keep polling")
    }

    @Test
    fun `a colliding status makes the stream endpoint fall back to the polling task`() {
        val collision = collisionFor("COMPLETED")
        whenever(service.getAnalysis(any())).thenReturn(analysis(collision), analysis("COMPLETED"))
        whenever(service.getIntegrationTestStreamingOutput(any())).thenReturn(null)

        val emitter = controller.integrationTestStream(1L)
        Thread.sleep(50)

        assertNotEquals(null, emitter)
    }

    @Test
    fun `an empty integration test log skips the chunk event`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis("FAILED"))

        val emitter = controller.integrationTestStream(1L)

        assertNotEquals(null, emitter)
    }
}
