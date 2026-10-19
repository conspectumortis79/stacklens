package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.EssentialToolsUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.platform.InstallToolResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EssentialToolsApiControllerTest {
    private val useCase = mock<EssentialToolsUseCase>()
    private val i18n = mock<I18nUseCase>()
    private val controller = EssentialToolsApiController(useCase, i18n)

    @Test
    fun `getStatus returns status from use case`() {
        val status = mapOf("git" to "available", "docker" to "missing")
        whenever(useCase.getStatus()).thenReturn(status)
        val r = controller.getStatus()
        assertEquals(200, r.statusCode.value())
        assertEquals(status, r.body)
    }

    @Test
    fun `install with null request body defaults to gh`() {
        whenever(useCase.install("gh")).thenReturn(
            InstallToolResult(true, 0, "ok", "", false, null, emptyList()),
        )
        val r = controller.install(null)
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `install with valid tool delegates to use case`() {
        whenever(useCase.install("git")).thenReturn(
            InstallToolResult(true, 0, "ok", "", false, null, emptyList()),
        )
        val r = controller.install(EssentialToolsApiController.InstallReq(tool = "git"))
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `install returning failure with no exitCode returns unsupported platform response`() {
        whenever(useCase.install("x")).thenReturn(
            InstallToolResult(false, null, "", "", false, null, emptyList()),
        )
        val r = controller.install(EssentialToolsApiController.InstallReq(tool = "x"))
        assertEquals(200, r.statusCode.value())
        assertTrue(r.body!!.containsKey("success"))
        assertEquals(false, r.body!!["success"])
    }

    @Test
    fun `install with unknown tool returns error response`() {
        whenever(useCase.install(any())).thenReturn(
            InstallToolResult(false, null, "", "", false, null, emptyList()),
        )
        whenever(i18n.t(any<String>(), any<Any>())).thenReturn("UNKNOWN_TOOL_MSG")
        val r = controller.install(EssentialToolsApiController.InstallReq(tool = "unknown-tool"))
        assertEquals(200, r.statusCode.value())
        val body = r.body!!
        assertEquals(false, body["success"])
        assertEquals("UNKNOWN_TOOL_MSG", body["error"])
    }

    @Test
    fun `install with manualCommand set does not return unknown-tool error`() {
        whenever(useCase.install(any())).thenReturn(
            InstallToolResult(false, null, "some output", "", true, "sudo apt install", emptyList()),
        )
        val r = controller.install(EssentialToolsApiController.InstallReq(tool = "docker"))
        assertEquals(200, r.statusCode.value())
        val body = r.body!!
        assertEquals(false, body["success"])
        assertEquals("sudo apt install", body["manualCommand"])
    }

    @Test
    fun `install with non-blank stdout does not return unknown-tool error`() {
        whenever(useCase.install(any())).thenReturn(
            InstallToolResult(false, null, "downloaded installer", "", false, null, emptyList()),
        )
        val r = controller.install(EssentialToolsApiController.InstallReq(tool = "docker"))
        assertEquals(200, r.statusCode.value())
        val body = r.body!!
        assertEquals(false, body["success"])
    }

    @Test
    fun `install returning failure with non-blank stderr uses stderr in error message`() {
        whenever(useCase.install("docker")).thenReturn(
            InstallToolResult(false, null, "some output", "real error message", true, "sudo apt install", emptyList()),
        )
        val r = controller.install(EssentialToolsApiController.InstallReq(tool = "docker"))
        assertEquals(200, r.statusCode.value())
        val body = r.body!!
        assertEquals(false, body["success"])
    }

    @Test
    fun `tr with a key-echoing i18n returns the key as-is`() {
        whenever(useCase.install("x")).thenReturn(
            InstallToolResult(false, 1, "out", "boom", false, null, emptyList()),
        )
        val ctrl =
            EssentialToolsApiController(
                useCase,
                com.stacklens.application.service
                    .FakeI18n(),
            )
        val result = ctrl.install(EssentialToolsApiController.InstallReq(tool = "x"))
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `install returning failure with exitCode returns failure response`() {
        whenever(useCase.install(any())).thenReturn(
            InstallToolResult(false, 1, "", "boom", false, null, emptyList()),
        )
        val r = controller.install(EssentialToolsApiController.InstallReq(tool = "x"))
        assertEquals(200, r.statusCode.value())
        assertTrue(r.body!!.containsKey("success"))
    }

    @Test
    fun `InstallReq default constructor uses null tool`() {
        val req = EssentialToolsApiController.InstallReq()
        assertEquals(null, req.tool)
    }
}
