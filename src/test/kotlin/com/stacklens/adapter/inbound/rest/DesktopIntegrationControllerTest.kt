package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.BrowserOpenResult
import com.stacklens.application.port.inbound.platform.ExternalBrowserUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopIntegrationControllerTest {
    private val browser: ExternalBrowserUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val controller = DesktopIntegrationController(browser, i18n)

    @Test
    fun `the submitted url is handed to the use case`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = true))

        controller.openUrl(mapOf("url" to "https://example.com"))

        verify(browser).openUrl("https://example.com")
    }

    @Test
    fun `a missing url parameter is forwarded as null`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = false, errorKey = "app.be_missing_url"))

        assertEquals(400, controller.openUrl(emptyMap()).statusCode.value())
        verify(browser).openUrl(null)
    }

    @Test
    fun `the response body shape stays status and error only`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = true))

        assertEquals(mapOf("status" to "opened"), controller.openUrl(mapOf("url" to "http://x")).body)
    }

    @Test
    fun `a rejection key is translated for the client`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = false, errorKey = "app.be_invalid_url_scheme"))
        whenever(i18n.t("app.be_invalid_url_scheme")).thenReturn("nur http erlaubt")

        val response = controller.openUrl(mapOf("url" to "ftp://x"))

        assertEquals(400, response.statusCode.value())
        assertEquals("nur http erlaubt", response.body?.get("error"))
    }

    @Test
    fun `a host failure answers with the server error body`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = false, errorMessage = "boom"))

        val response = controller.openUrl(mapOf("url" to "https://x"))

        assertEquals(500, response.statusCode.value())
        assertEquals("boom", response.body?.get("error"))
    }
}
