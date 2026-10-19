package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.AppLifecycleUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AppLifecycleControllerTest {
    private val lifecycle: AppLifecycleUseCase = mock()

    @Test
    fun `quitApp answers with the shutdown acknowledgement`() {
        val response = AppLifecycleController(lifecycle).quitApp()

        assertEquals(200, response.statusCode.value())
        assertEquals("shutting down", response.body?.get("status"))
    }

    @Test
    fun `quitApp schedules the shutdown through the use case`() {
        var scheduled = false
        val controller =
            object : AppLifecycleController(lifecycle) {
                override fun scheduleShutdown() {
                    scheduled = true
                    lifecycle.quit()
                }
            }

        controller.quitApp()

        assertEquals(true, scheduled)
        verify(lifecycle).quit()
    }
}
