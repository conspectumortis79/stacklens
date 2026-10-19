package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.config.TextFilterSettingsUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.concurrent.atomic.AtomicBoolean

class TextFilterConfigControllerTest {
    private class FakeSettings(
        initial: Boolean,
    ) : TextFilterSettingsUseCase {
        private val state = AtomicBoolean(initial)

        var lastSet: Boolean? = null

        override fun isTextFilterEnabled(): Boolean = state.get()

        override fun setTextFilterEnabled(enabled: Boolean): Boolean {
            lastSet = enabled
            state.set(enabled)
            return state.get()
        }
    }

    private fun controller(settings: TextFilterSettingsUseCase) = TextFilterConfigController(settings)

    @Test fun `GET returns current enabled state when port says true`() {
        val settings = FakeSettings(initial = true)
        val response: ResponseEntity<Map<String, Any>> = controller(settings).getConfig()
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body!!["enabled"])
    }

    @Test fun `GET returns current enabled state when port says false`() {
        val settings = FakeSettings(initial = false)
        val response: ResponseEntity<Map<String, Any>> = controller(settings).getConfig()
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body!!["enabled"])
    }

    @Test fun `PUT with boolean true updates port and returns new state`() {
        val settings = FakeSettings(initial = false)
        val response = controller(settings).setConfig(mapOf("enabled" to true))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, settings.lastSet)
        assertEquals(true, response.body!!["ok"])
        assertEquals(true, response.body!!["enabled"])
    }

    @Test fun `PUT with boolean false updates port and returns new state`() {
        val settings = FakeSettings(initial = true)
        val response = controller(settings).setConfig(mapOf("enabled" to false))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, settings.lastSet)
        assertEquals(false, response.body!!["enabled"])
    }

    @Test fun `PUT with string true is parsed as enabled`() {
        val settings = FakeSettings(initial = false)
        val response = controller(settings).setConfig(mapOf("enabled" to "true"))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(settings.lastSet!!)
    }

    @Test fun `PUT with string TRUE is parsed as enabled (case-insensitive)`() {
        val settings = FakeSettings(initial = false)
        val response = controller(settings).setConfig(mapOf("enabled" to "TRUE"))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(settings.lastSet!!)
    }

    @Test fun `PUT with string false leaves port unchanged`() {
        val settings = FakeSettings(initial = true)
        val response = controller(settings).setConfig(mapOf("enabled" to "false"))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, settings.lastSet)
    }

    @Test fun `PUT with missing enabled field returns 400 and does not touch port`() {
        val settings = FakeSettings(initial = true)
        val response = controller(settings).setConfig(mapOf("foo" to "bar"))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(null, settings.lastSet)
    }

    @Test fun `PUT with non-boolean non-string value returns 400`() {
        val settings = FakeSettings(initial = true)
        val response = controller(settings).setConfig(mapOf("enabled" to 42))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(null, settings.lastSet)
    }

    @Test fun `PUT with empty body returns 400`() {
        val settings = FakeSettings(initial = true)
        val response = controller(settings).setConfig(emptyMap())
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(null, settings.lastSet)
    }
}
