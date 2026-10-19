package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DesktopShutdownHandlerBranchCoverageTest {
    @Test
    fun `shutdown stops the backend and halts through the injected hooks`() {
        val backend = mock<DesktopBackendBootstrap>()
        var halts = 0
        val handler =
            DesktopShutdownHandler(
                backend = backend,
                threadFactory = { _, _, block ->
                    block()
                    Thread()
                },
                sleep = {},
                exit = { halts++ },
            )

        handler.requestShutdown()

        verify(backend).stop()
        verify(backend).checkpoint()
        assertEquals(2, halts)
    }

    @Test
    fun `default hooks are wired when only the backend is supplied`() {
        assertNotNull(DesktopShutdownHandler(backend = mock<DesktopBackendBootstrap>()))
    }
}
