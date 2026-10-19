package com.stacklens.application.service

import com.stacklens.application.service.pipeline.loopback.FayeFinnLoopback
import com.stacklens.application.service.pipeline.loopback.QuinnLoopback
import com.stacklens.application.service.pipeline.loopback.SageTessLoopback
import com.stacklens.application.service.pipeline.loopback.SydFinnLoopback
import com.stacklens.application.service.pipeline.loopback.TessFinnLoopback
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentPipelineRunnerCompanionTest {
    private val loopbacks =
        listOf(
            FayeFinnLoopback(),
            TessFinnLoopback(),
            SydFinnLoopback(),
            QuinnLoopback(),
            SageTessLoopback(),
        )

    @Test
    fun `the loopback catalog is a non-empty list`() {
        assertNotNull(loopbacks)
        assertTrue(loopbacks.isNotEmpty())
    }

    @Test
    fun `the loopback catalog has five entries`() {
        assertEquals(5, loopbacks.size)
    }

    @Test
    fun `every loopback has a non-blank name`() {
        for (lb in loopbacks) {
            assertNotNull(lb.name)
            assertTrue(lb.name.isNotBlank(), "loopback ${lb.javaClass.simpleName} has blank name")
        }
    }
}
