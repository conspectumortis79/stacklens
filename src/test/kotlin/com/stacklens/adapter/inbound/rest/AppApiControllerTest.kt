package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.config.UiPreferencesUseCase
import com.stacklens.application.port.inbound.platform.AppLifecycleUseCase
import com.stacklens.application.port.inbound.platform.BrowserOpenResult
import com.stacklens.application.port.inbound.platform.ExternalBrowserUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppApiControllerTest {
    private val preferences: UiPreferencesUseCase = mock()
    private val lifecycle: AppLifecycleUseCase = mock()
    private val browser: ExternalBrowserUseCase = mock()
    private val i18n: I18nUseCase = mock()

    private val uiController by lazy { UiPreferencesController(preferences, i18n) }
    private val lifecycleController by lazy { AppLifecycleController(lifecycle) }
    private val desktopController by lazy { DesktopIntegrationController(browser, i18n) }

    @Test
    fun `getUiPref returns the value of the use case`() {
        whenever(preferences.preference("someKey")).thenReturn("stored")

        val resp = uiController.getUiPref("someKey")

        assertEquals(200, resp.statusCode.value())
        assertEquals("stored", resp.body?.get("value"))
    }

    @Test
    fun `getUiPref answers an empty value when the key is unknown`() {
        whenever(preferences.preference("missing")).thenReturn("")

        assertEquals("", uiController.getUiPref("missing").body?.get("value"))
    }

    @Test
    fun `getUiPrefs returns the whole map`() {
        whenever(preferences.allPreferences()).thenReturn(mapOf("theme" to "dark"))

        assertEquals(mapOf("theme" to "dark"), uiController.getUiPrefs())
    }

    @Test
    fun `setUiPref returns 400 when value is missing`() {
        assertEquals(400, uiController.setUiPref("myKey", emptyMap()).statusCode.value())
    }

    @Test
    fun `setUiPref stores the value through the use case`() {
        val resp = uiController.setUiPref("myKey", mapOf("value" to "testValue"))

        assertEquals(200, resp.statusCode.value())
        assertTrue(resp.body?.get("ok") == true)
        org.mockito.kotlin
            .verify(preferences)
            .setPreference("myKey", "testValue")
    }

    @Test
    fun `quit answers while the lifecycle use case is triggered`() {
        val resp = lifecycleController.quitApp()

        assertEquals(200, resp.statusCode.value())
        assertEquals("shutting down", resp.body?.get("status"))
    }

    @Test
    fun `the shutdown is scheduled asynchronously so the response can leave first`() {
        var started = false
        val controller =
            object : AppLifecycleController(lifecycle) {
                override fun scheduleShutdown() {
                    started = true
                }
            }

        controller.quitApp()

        assertTrue(started)
    }

    @Test
    fun `the desktop controller answers opened when the use case succeeds`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = true))

        val resp = desktopController.openUrl(mapOf("url" to "https://example.com"))

        assertEquals(200, resp.statusCode.value())
        assertEquals("opened", resp.body?.get("status"))
    }

    @Test
    fun `the desktop controller maps a rejection to the translated bad request`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = false, errorKey = "app.be_invalid_url_scheme"))
        whenever(i18n.t("app.be_invalid_url_scheme")).thenReturn("nur http")

        val resp = desktopController.openUrl(mapOf("url" to "ftp://x"))

        assertEquals(400, resp.statusCode.value())
        assertEquals("nur http", resp.body?.get("error"))
    }

    @Test
    fun `the desktop controller maps a host failure to the internal server error`() {
        whenever(browser.openUrl(anyOrNull())).thenReturn(BrowserOpenResult(opened = false, errorMessage = "no browser"))

        val resp = desktopController.openUrl(mapOf("url" to "https://example.com"))

        assertEquals(500, resp.statusCode.value())
        assertEquals("no browser", resp.body?.get("error"))
    }
}
