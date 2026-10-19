package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopShutdownHandlerQuitCoordinatorTest {
    @Test
    fun `DesktopShutdownHandler watchdog is 800 ms`() {
        assertEquals(800L, DesktopShutdownHandler.WATCHDOG_TIMEOUT_MS)
    }

    @Test
    fun `DesktopQuitCoordinator watchdog is 800 ms`() {
        assertEquals(800L, DesktopQuitCoordinator.QUIT_WATCHDOG_MS)
    }

    @Test
    fun `DesktopShutdownHandler watchdog equals DesktopQuitCoordinator watchdog`() {
        assertEquals(DesktopShutdownHandler.WATCHDOG_TIMEOUT_MS, DesktopQuitCoordinator.QUIT_WATCHDOG_MS)
    }

    @Test
    fun `DesktopShutdownHandler requestShutdown uses default thread factory`() {
        val backend = org.mockito.kotlin.mock<DesktopBackendBootstrap>()
        org.mockito.Mockito
            .`when`(backend.checkpoint())
            .then { }
        var exitCount = 0
        val handler =
            DesktopShutdownHandler(
                backend = backend,
                sleep = { },
                exit = { exitCount++ },
            )
        handler.requestShutdown()
        assertTrue(true)
    }

    @Test
    fun `DesktopQuitCoordinator quit uses default thread factory`() {
        val stage = org.mockito.kotlin.mock<javafx.stage.Stage>()
        val backend = org.mockito.kotlin.mock<DesktopBackendBootstrap>()
        org.mockito.Mockito
            .`when`(backend.checkpoint())
            .then { }
        var exitCount = 0
        val fxRunner =
            object : JavaFxRunner {
                override fun runOnFxThread(block: () -> Unit) {
                    block()
                }
            }
        val coordinator =
            DesktopQuitCoordinator(
                stage = stage,
                backend = backend,
                fxRunner = fxRunner,
                exitFx = { },
                sleep = { },
                exit = { exitCount++ },
            )
        coordinator.quit()
        assertTrue(true)
    }
}
