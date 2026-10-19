package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.config.UiPreferencesUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UiPreferencesControllerTest {
    private val preferences: UiPreferencesUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val controller = UiPreferencesController(preferences, i18n)

    @Test
    fun `getUiPrefs exposes the whole preference map`() {
        whenever(preferences.allPreferences()).thenReturn(mapOf("theme" to "modern"))

        assertEquals(mapOf("theme" to "modern"), controller.getUiPrefs())
    }

    @Test
    fun `getUiPref answers the stored value of one key`() {
        whenever(preferences.preference("language")).thenReturn("de")

        assertEquals(mapOf("value" to "de"), controller.getUiPref("language").body)
    }

    @Test
    fun `setUiPref without a value answers with the translated error`() {
        whenever(i18n.t(any(), *emptyArray())).thenReturn("Wert fehlt")

        val response = controller.setUiPref("theme", emptyMap())

        assertEquals(400, response.statusCode.value())
        assertEquals("Wert fehlt", response.body?.get("error"))
        verify(preferences, never()).setPreference(any(), any())
    }

    @Test
    fun `setUiPref writes the value and acknowledges`() {
        val response = controller.setUiPref("theme", mapOf("value" to "dark"))

        assertEquals(200, response.statusCode.value())
        assertEquals(mapOf("ok" to true), response.body)
        verify(preferences).setPreference("theme", "dark")
    }
}
