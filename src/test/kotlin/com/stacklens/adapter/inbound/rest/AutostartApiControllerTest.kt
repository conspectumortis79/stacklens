package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.AutostartChange
import com.stacklens.application.port.inbound.platform.AutostartStatus
import com.stacklens.application.port.inbound.platform.AutostartUseCase
import com.stacklens.domain.autostart.AutostartMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutostartApiControllerTest {
    private val useCase = StubUseCase()

    private val controller = AutostartApiController(useCase)

    @Test
    fun `EnableReq defaults to a null mode`() {
        assertNull(AutostartApiController.EnableReq().mode)
    }

    @Test
    fun `EnableReq keeps the given mode`() {
        assertEquals("server", AutostartApiController.EnableReq(mode = "server").mode)
    }

    @Test
    fun `status exposes the rendered view of the use case status`() {
        useCase.status = AutostartStatus("linux", supported = true, enabled = true, mode = AutostartMode.SERVER, serverPort = 8091)

        val body = controller.getStatus().body!!

        assertEquals(200, controller.getStatus().statusCode.value())
        assertEquals(true, body["supported"])
        assertEquals(true, body["enabled"])
        assertEquals("linux", body["platform"])
        assertEquals("server", body["mode"])
        assertEquals(8091, body["serverPort"])
    }

    @Test
    fun `enable forwards the requested mode and reports it back`() {
        val body = controller.enable(AutostartApiController.EnableReq(mode = "SERVER")).body!!

        assertEquals(AutostartMode.SERVER, useCase.enabledWith)
        assertEquals(true, body["ok"])
        assertEquals("server", body["mode"])
    }

    @Test
    fun `enable without a body falls back to desktop mode`() {
        val body = controller.enable(null).body!!

        assertEquals(AutostartMode.DESKTOP, useCase.enabledWith)
        assertEquals("desktop", body["mode"])
    }

    @Test
    fun `an unsupported platform is answered as a plain ok false payload`() {
        useCase.enableResult = AutostartChange.Unsupported("kein platform")

        val result = controller.enable(AutostartApiController.EnableReq())

        assertEquals(200, result.statusCode.value())
        assertEquals(false, result.body!!["ok"])
        assertEquals("kein platform", result.body!!["error"])
    }

    @Test
    fun `a rejected change is answered as an internal server error`() {
        useCase.enableResult = AutostartChange.Rejected("schreibzugriff verboten")

        val result = controller.enable(AutostartApiController.EnableReq())

        assertEquals(500, result.statusCode.value())
        assertEquals(false, result.body!!["ok"])
        assertEquals("schreibzugriff verboten", result.body!!["error"])
    }

    @Test
    fun `disable reports success without a mode and delegates to the use case`() {
        val result = controller.disable()

        assertTrue(useCase.disabled)
        assertEquals(200, result.statusCode.value())
        assertEquals(true, result.body!!["ok"])
        assertFalse(result.body!!.containsKey("mode"))
    }

    @Test
    fun `disable surfaces an unsupported platform`() {
        useCase.disableResult = AutostartChange.Unsupported("kein zugriff")

        val result = controller.disable()

        assertEquals(200, result.statusCode.value())
        assertEquals("kein zugriff", result.body!!["error"])
    }

    private class StubUseCase : AutostartUseCase {
        var status: AutostartStatus = AutostartStatus("linux", supported = true, enabled = false, mode = AutostartMode.DESKTOP, serverPort = 8090)

        var enableResult: AutostartChange = AutostartChange.Applied(AutostartMode.DESKTOP)

        var disableResult: AutostartChange = AutostartChange.Applied(AutostartMode.DESKTOP)

        var enabledWith: AutostartMode? = null

        var disabled = false

        override fun status(): AutostartStatus = status

        override fun enable(mode: AutostartMode): AutostartChange {
            enabledWith = mode
            return when (val result = enableResult) {
                is AutostartChange.Applied -> result.copy(mode = mode)
                else -> result
            }
        }

        override fun disable(): AutostartChange {
            disabled = true
            return disableResult
        }
    }
}
