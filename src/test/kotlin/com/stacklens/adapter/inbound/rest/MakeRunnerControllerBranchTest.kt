package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.MakeRunnerUseCase
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MakeRunnerControllerBranchTest {
    @Test
    fun `MakeRunnerController is constructible`() {
        val ctl =
            MakeRunnerController(
                makeRunnerUseCase = mock<MakeRunnerUseCase>(),
            )
        assertNotNull(ctl)
    }

    @Test
    fun `waitForProcess returns true for alive process`() {
        val ctl =
            MakeRunnerController(
                makeRunnerUseCase = mock<MakeRunnerUseCase>(),
            )
        assertTrue(ctl.waitForProcess(mock<Process>()))
    }

    @Test
    fun `createEmitter returns an SseEmitter instance`() {
        val ctl =
            MakeRunnerController(
                makeRunnerUseCase = mock<MakeRunnerUseCase>(),
            )
        val emitter = ctl.createEmitter()
        assertNotNull(emitter)
    }
}
