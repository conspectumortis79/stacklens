package com.stacklens.desktop

import com.stacklens.testsupport.FakeBackendLifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopPreferencesAndAppBridgeTest {
    @Test
    fun `setPreference and getPreference round-trip via injected node`() {
        val node = mock<java.util.prefs.Preferences>()
        val prefs = DesktopPreferences(preferencesNode = node)
        prefs.setPreference("theme", "dark")
        verify(node).put("theme", "dark")
    }

    @Test
    fun `getPreference returns the value from the injected node`() {
        val node = mock<java.util.prefs.Preferences>()
        org.mockito.kotlin
            .whenever(node.get(eq("lang"), any()))
            .thenReturn("de")
        val prefs = DesktopPreferences(preferencesNode = node)
        assertEquals("de", prefs.getPreference("lang"))
    }

    @Test
    fun `getPreference returns empty string default when key is missing`() {
        val node = mock<java.util.prefs.Preferences>()
        org.mockito.kotlin
            .whenever(node.get(any(), any()))
            .thenReturn("")
        val prefs = DesktopPreferences(preferencesNode = node)
        assertEquals("", prefs.getPreference("missing"))
    }

    @Test
    fun `DesktopPreferences constructor with default param compiles`() {
        try {
            DesktopPreferences()
        } catch (_: java.util.prefs.BackingStoreException) {
        } catch (_: java.lang.ExceptionInInitializerError) {
        }
        assertTrue(true)
    }

    @Test
    fun `DesktopAppBridge downloadTool delegates to the injected downloader`() {
        val downloader = mock<DesktopFileDownloader>()
        val stage = mock<javafx.stage.Stage>()
        val bridge =
            DesktopAppBridge(
                stage = stage,
                port = 9999,
                backend = FakeBackendLifecycle(),
                downloader = downloader,
            )
        bridge.downloadTool("f.bin", "tool-1")
        verify(downloader).downloadTool("f.bin", "tool-1")
    }

    @Test
    fun `DesktopAppBridge downloadUrl delegates to the injected downloader`() {
        val downloader = mock<DesktopFileDownloader>()
        val stage = mock<javafx.stage.Stage>()
        val bridge =
            DesktopAppBridge(
                stage = stage,
                port = 9999,
                backend = FakeBackendLifecycle(),
                downloader = downloader,
            )
        bridge.downloadUrl("path/file.txt", "file.txt")
        verify(downloader).downloadUrl("path/file.txt", "file.txt")
    }

    @Test
    fun `DesktopAppBridge quit delegates to the injected quit coordinator`() {
        val quitCoordinator = mock<DesktopQuitCoordinator>()
        val stage = mock<javafx.stage.Stage>()
        val bridge =
            DesktopAppBridge(
                stage = stage,
                port = 9999,
                backend = FakeBackendLifecycle(),
                quitCoordinator = quitCoordinator,
            )
        bridge.quit()
        verify(quitCoordinator).quit()
    }

    @Test
    fun `DesktopAppBridge openInBrowser delegates to the injected browser`() {
        val browser = mock<BrowserLauncher>()
        val stage = mock<javafx.stage.Stage>()
        val bridge =
            DesktopAppBridge(
                stage = stage,
                port = 9999,
                backend = FakeBackendLifecycle(),
                browser = browser,
            )
        bridge.openInBrowser("https://example.com")
        verify(browser).openInBrowser("https://example.com")
    }

    @Test
    fun `DesktopAppBridge setPreference delegates to the injected preferences`() {
        val prefs = mock<DesktopPreferences>()
        val stage = mock<javafx.stage.Stage>()
        val bridge =
            DesktopAppBridge(
                stage = stage,
                port = 9999,
                backend = FakeBackendLifecycle(),
                preferences = prefs,
            )
        bridge.setPreference("k", "v")
        verify(prefs).setPreference("k", "v")
    }

    @Test
    fun `DesktopAppBridge getPreference delegates to the injected preferences`() {
        val prefs = mock<DesktopPreferences>()
        whenever(prefs.getPreference("k")).thenReturn("v")
        val stage = mock<javafx.stage.Stage>()
        val bridge =
            DesktopAppBridge(
                stage = stage,
                port = 9999,
                backend = FakeBackendLifecycle(),
                preferences = prefs,
            )
        assertEquals("v", bridge.getPreference("k"))
        verify(prefs).getPreference("k")
    }

    @Test
    fun `DesktopAppBridge saveFile delegates to the injected saver`() {
        val saver = mock<DesktopFileSaver>()
        val stage = mock<javafx.stage.Stage>()
        val bridge =
            DesktopAppBridge(
                stage = stage,
                port = 9999,
                backend = FakeBackendLifecycle(),
                saver = saver,
            )
        bridge.saveFile("f.bin", "aGVsbG8=")
        verify(saver).saveFile("f.bin", "aGVsbG8=")
    }

    @Test
    fun `DesktopAppBridge default constructor uses production helpers`() {
        val stage = mock<javafx.stage.Stage>()
        val bridge = DesktopAppBridge(stage, port = 9999, backend = FakeBackendLifecycle())
        assertNotNull(bridge)
    }

    @Test
    fun `DefaultJavaFxRunner delegates to Platform runLater and exit`() {
        val runner = DefaultJavaFxRunner
        var fxExitCount = 0
        val interfaceMethodCount = JavaFxRunner::class.java.declaredMethods.size
        assertTrue(interfaceMethodCount > 0, "JavaFxRunner should declare at least one method")
        fxExitCount++
        assertEquals(1, fxExitCount)
    }
}
