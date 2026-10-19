package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopLauncherSmokeTest {
    @Test
    fun `isServerModeRequested returns true for --server flag`() {
        assertTrue(isServerModeRequested(arrayOf("--server")))
    }

    @Test
    fun `isServerModeRequested returns true for --headless flag`() {
        assertTrue(isServerModeRequested(arrayOf("--headless")))
    }

    @Test
    fun `isServerModeRequested returns true when STACKLENS_SERVER_MODE env var is 1`() {
        assertTrue(isServerModeRequested(arrayOf(), envProvider = { "1" }))
    }

    @Test
    fun `isServerModeRequested returns true when env var is true (case-insensitive)`() {
        assertTrue(isServerModeRequested(arrayOf(), envProvider = { "TRUE" }))
    }

    @Test
    fun `isServerModeRequested returns false when neither flag nor env is set`() {
        assertFalse(isServerModeRequested(arrayOf(), envProvider = { null }))
        assertFalse(isServerModeRequested(arrayOf("start"), envProvider = { "0" }))
    }

    @Test
    fun `runMain dispatches to headless server when --server flag is present`() {
        var javaFxCalled = false
        var headlessCalled = false
        val javaFxLauncher = JavaFxLauncher { _, _ -> javaFxCalled = true }
        val headlessRunner = HeadlessServerRunner { headlessCalled = true }

        runMain(
            args = arrayOf("--server"),
            javaFxLauncher = javaFxLauncher,
            headlessServerRunner = headlessRunner,
            configureJvm = {},
            setDockIcon = {},
        )

        assertTrue(headlessCalled)
        assertFalse(javaFxCalled)
    }

    @Test
    fun `runMain dispatches to headless server when STACKLENS_SERVER_MODE env is set`() {
        var headlessCalled = false
        val javaFxLauncher = JavaFxLauncher { _, _ -> error("should not be called") }
        val headlessRunner = HeadlessServerRunner { headlessCalled = true }

        runMain(
            args = arrayOf(),
            javaFxLauncher = javaFxLauncher,
            headlessServerRunner = headlessRunner,
            configureJvm = {},
            setDockIcon = {},
            isServerMode = { isServerModeRequested(it, envProvider = { "1" }) },
        )

        assertTrue(headlessCalled)
    }

    @Test
    fun `runMain dispatches to JavaFX when no server flag is present`() {
        var javaFxCalled = false
        val javaFxLauncher = JavaFxLauncher { _, _ -> javaFxCalled = true }
        val headlessRunner = HeadlessServerRunner { error("should not be called") }
        var jvmConfigured = false
        var dockSet = false

        runMain(
            args = arrayOf(),
            javaFxLauncher = javaFxLauncher,
            headlessServerRunner = headlessRunner,
            configureJvm = { jvmConfigured = true },
            setDockIcon = { dockSet = true },
        )

        assertTrue(javaFxCalled)
        assertTrue(jvmConfigured)
        assertTrue(dockSet)
    }

    @Test
    fun `setMacDockIcon is a no-op on non-mac OS`() {
        var readerCalled = false
        setMacDockIcon(
            osName = "Linux",
            resourceStream = {
                readerCalled = true
                null
            },
            imageReader = { error("should not be called") },
            iconSetter = { error("should not be called") },
        )
        assertFalse(readerCalled)
    }

    @Test
    fun `setMacDockIcon swallows exceptions from image reader`() {
        setMacDockIcon(
            osName = "Mac OS X",
            resourceStream = { java.io.ByteArrayInputStream(byteArrayOf()) },
            imageReader = { null },
            iconSetter = { error("should not be called because image is null") },
        )
    }

    @Test
    fun `setMacDockIcon relies on the default hooks without breaking the start`() {
        assertDoesNotThrow { setMacDockIcon(osName = "Mac OS X") }
    }

    @Test
    fun `bundledDockIconStream serves the icon that ships with the launcher`() {
        val stream = requireNotNull(bundledDockIconStream()) { "stacklens-icon.png must be part of the launcher resources" }

        stream.use {
            assertTrue(it.readBytes().isNotEmpty(), "the bundled icon must not be empty")
        }
    }

    @Test
    fun `the platform dock icon chain never throws`() {
        val icon = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)

        assertDoesNotThrow { setPlatformDockIcon(icon) }
    }
}
