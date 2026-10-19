package com.stacklens.application.service

import com.stacklens.application.port.out.platform.HostBrowserPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalBrowserServiceTest {
    private val opened = mutableListOf<String>()

    private val hostBrowser =
        object : HostBrowserPort {
            override fun browse(url: String) {
                opened += url
                if (url.contains("broken")) throw IllegalStateException("no browser available")
            }
        }

    private val service = ExternalBrowserService(hostBrowser)

    @Test
    fun `a missing url is rejected with the translation key`() {
        val result = service.openUrl(null)

        assertFalse(result.opened)
        assertEquals(ExternalBrowserService.MISSING_URL_KEY, result.errorKey)
    }

    @Test
    fun `a blank url is rejected as missing`() {
        assertEquals(ExternalBrowserService.MISSING_URL_KEY, service.openUrl("   ").errorKey)
    }

    @Test
    fun `a non http scheme is rejected`() {
        val result = service.openUrl("ftp://example.com")

        assertEquals(ExternalBrowserService.INVALID_SCHEME_KEY, result.errorKey)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `http and https urls reach the host browser with the trimmed value`() {
        assertTrue(service.openUrl("  http://example.com  ").opened)
        assertTrue(service.openUrl("https://example.com").opened)

        assertEquals(listOf("http://example.com", "https://example.com"), opened)
    }

    @Test
    fun `a host failure is reported with the exception message`() {
        val result = service.openUrl("https://broken.example")

        assertFalse(result.opened)
        assertNull(result.errorKey)
        assertEquals("no browser available", result.errorMessage)
    }

    @Test
    fun `a host failure without a message falls back to the generic text`() {
        val failing =
            ExternalBrowserService(
                object : HostBrowserPort {
                    override fun browse(url: String): Unit = throw RuntimeException()
                },
            )

        assertEquals("failed", failing.openUrl("https://example.com").errorMessage)
    }
}
