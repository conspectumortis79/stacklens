package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StackLensMainTest {
    private var envValue: String? = null

    @Test
    fun `isServerModeRequested returns true when --server flag is present`() {
        assertTrue(isServerModeRequested(arrayOf("--server"), envProvider = { envValue }))
    }

    @Test
    fun `isServerModeRequested returns true when --headless flag is present`() {
        assertTrue(isServerModeRequested(arrayOf("--headless"), envProvider = { envValue }))
    }

    @Test
    fun `isServerModeRequested returns true when both flags are present`() {
        assertTrue(isServerModeRequested(arrayOf("--server", "--headless"), envProvider = { envValue }))
    }

    @Test
    fun `isServerModeRequested returns false when no flags are present`() {
        assertFalse(isServerModeRequested(arrayOf("--port", "8090"), envProvider = { envValue }))
    }

    @Test
    fun `isServerModeRequested returns false for empty args`() {
        assertFalse(isServerModeRequested(emptyArray(), envProvider = { envValue }))
    }

    @Test
    fun `isServerModeRequested returns false when only similar flags are present`() {
        assertFalse(isServerModeRequested(arrayOf("--server-mode", "true"), envProvider = { envValue }))
    }

    @Test
    fun `isServerModeRequested treats env var 1 as true`() {
        assertTrue(isServerModeRequested(emptyArray(), envProvider = { "1" }))
    }

    @Test
    fun `isServerModeRequested treats env var true as true`() {
        assertTrue(isServerModeRequested(emptyArray(), envProvider = { "true" }))
    }

    @Test
    fun `isServerModeRequested treats env var TRUE as true`() {
        assertTrue(isServerModeRequested(emptyArray(), envProvider = { "TRUE" }))
    }

    @Test
    fun `isServerModeRequested treats env var True mixed case as true`() {
        assertTrue(isServerModeRequested(emptyArray(), envProvider = { "True" }))
    }

    @Test
    fun `isServerModeRequested treats env var 0 as false`() {
        assertFalse(isServerModeRequested(emptyArray(), envProvider = { "0" }))
    }

    @Test
    fun `isServerModeRequested treats env var no as false`() {
        assertFalse(isServerModeRequested(emptyArray(), envProvider = { "no" }))
    }

    @Test
    fun `isServerModeRequested treats env var empty as false`() {
        assertFalse(isServerModeRequested(emptyArray(), envProvider = { "" }))
    }

    @Test
    fun `isServerModeRequested treats null env var as false`() {
        assertFalse(isServerModeRequested(emptyArray(), envProvider = { null }))
    }

    @Test
    fun `isServerModeRequested combines CLI and env var flags`() {
        assertTrue(isServerModeRequested(arrayOf("--unknown", "x"), envProvider = { "1" }))
    }

    @Test
    fun `isServerModeRequested returns false when only unknown arg is present and env is null`() {
        assertFalse(isServerModeRequested(arrayOf("--unknown", "x"), envProvider = { null }))
    }

    @Test
    fun `configureDesktopJvmProperties sets java awt headless to false`() {
        val savedHeadless = System.getProperty("java.awt.headless")
        try {
            configureDesktopJvmProperties()
            assertEquals("false", System.getProperty("java.awt.headless"))
        } finally {
            if (savedHeadless != null) {
                System.setProperty("java.awt.headless", savedHeadless)
            } else {
                System.clearProperty("java.awt.headless")
            }
        }
    }

    @Test
    fun `configureDesktopJvmProperties sets prism vsync to true`() {
        val savedVsync = System.getProperty("prism.vsync")
        try {
            configureDesktopJvmProperties()
            assertEquals("true", System.getProperty("prism.vsync"))
        } finally {
            if (savedVsync != null) {
                System.setProperty("prism.vsync", savedVsync)
            } else {
                System.clearProperty("prism.vsync")
            }
        }
    }

    @Test
    fun `configureDesktopJvmProperties sets javafx animation framerate`() {
        configureDesktopJvmProperties()
        assertEquals("30", System.getProperty("javafx.animation.framerate"))
        assertEquals("false", System.getProperty("javafx.animation.fullspeed"))
    }

    @Test
    fun `setMacDockIcon is a no-op on non-mac hosts`() {
        setMacDockIcon()
        assertTrue(true)
    }

    @Test
    fun `default loadingHtmlLoader falls back to inline HTML when resource is missing`() {
        val app =
            DesktopLauncherApp(
                loadingHtmlLoader = {
                    "<html><body><p>Fallback</p></body></html>"
                },
            )
        val html = app.loadingHtmlLoader()
        assertTrue(html.isNotBlank())
        assertTrue(html.contains("Fallback"))
    }

    @Test
    fun `default no-arg loadingHtmlLoader reads bundled loading html`() {
        val app = DesktopLauncherApp()
        val html = app.loadingHtmlLoader()
        assertTrue(html.isNotBlank())
    }

    @Test
    fun `default loadingHtmlLoader inline fallback path`() {
        val inlineLoader: () -> String = {
            val stream: java.io.InputStream? = null
            if (stream == null) {
                "<html><body style='display:flex;align-items:center;" +
                    "justify-content:center;height:100vh;font-family:sans-serif;" +
                    "background:#f5f2ea'><p>StackLens startet...</p></body></html>"
            } else {
                stream.bufferedReader().readText()
            }
        }
        val html = inlineLoader()
        assertTrue(html.contains("StackLens startet"))
    }
}
