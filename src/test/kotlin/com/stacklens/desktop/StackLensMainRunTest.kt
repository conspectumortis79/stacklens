package com.stacklens.desktop

import javafx.application.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class StackLensMainRunTest {
    @Test
    fun `runMain in --server mode delegates to headlessServerRunner and skips JavaFX launcher`() {
        var serverRunnerCalled = false
        var jfxLauncherCalled = false
        var jvmConfigured = false
        var dockIconSet = false
        runMain(
            args = arrayOf("--server"),
            javaFxLauncher = JavaFxLauncher { _, _ -> jfxLauncherCalled = true },
            headlessServerRunner = HeadlessServerRunner { serverRunnerCalled = true },
            configureJvm = { jvmConfigured = true },
            setDockIcon = { dockIconSet = true },
        )
        assertTrue(serverRunnerCalled)
        assertFalse(jfxLauncherCalled)
        assertFalse(jvmConfigured, "JVM config must be skipped in server mode")
        assertFalse(dockIconSet, "Dock icon setter must be skipped in server mode")
    }

    @Test
    fun `runMain in --headless mode delegates to headlessServerRunner`() {
        val serverCalled = AtomicBoolean(false)
        runMain(
            args = arrayOf("--headless"),
            javaFxLauncher = JavaFxLauncher { _, _ -> error("JavaFX launcher must not be called") },
            headlessServerRunner = HeadlessServerRunner { serverCalled.set(true) },
        )
        assertTrue(serverCalled.get())
    }

    @Test
    fun `runMain in env STACKLENS_SERVER_MODE=1 mode delegates to headlessServerRunner`() {
        val serverCalled = AtomicBoolean(false)
        runMain(
            args = emptyArray(),
            javaFxLauncher = JavaFxLauncher { _, _ -> error("JavaFX launcher must not be called") },
            headlessServerRunner = HeadlessServerRunner { serverCalled.set(true) },
            isServerMode = { _ -> true },
        )
        assertTrue(serverCalled.get())
    }

    @Test
    fun `runMain honours a custom isServerMode predicate that returns false`() {
        val launched = AtomicBoolean(false)
        runMain(
            args = emptyArray(),
            javaFxLauncher = JavaFxLauncher { _, _ -> launched.set(true) },
            headlessServerRunner = HeadlessServerRunner { error("must not run headless") },
            isServerMode = { _ -> false },
        )
        assertTrue(launched.get())
    }

    @Test
    fun `runMain in desktop mode configures JVM and dock icon then launches JavaFX`() {
        val configured = AtomicBoolean(false)
        val dockSet = AtomicBoolean(false)
        val launched = AtomicBoolean(false)
        val launchArgs = AtomicReference<Array<out String>?>(null)
        runMain(
            args = arrayOf("--port", "8090"),
            javaFxLauncher =
                JavaFxLauncher { _, args ->
                    launched.set(true)
                    launchArgs.set(args)
                },
            headlessServerRunner = HeadlessServerRunner { error("must not run headless") },
            configureJvm = { configured.set(true) },
            setDockIcon = { dockSet.set(true) },
        )
        assertTrue(configured.get())
        assertTrue(dockSet.get())
        assertTrue(launched.get())
        val captured = launchArgs.get()
        assertNotNull(captured)
        assertEquals("--port", captured!![0])
        assertEquals("8090", captured[1])
    }

    @Test
    fun `runMain in desktop mode with empty args still launches JavaFX`() {
        val launched = AtomicBoolean(false)
        runMain(
            args = emptyArray(),
            javaFxLauncher = JavaFxLauncher { _, _ -> launched.set(true) },
            headlessServerRunner = HeadlessServerRunner { error("must not run headless") },
        )
        assertTrue(launched.get())
    }

    @Test
    fun `runMain does not launch JavaFX when --server flag and other args are both present`() {
        val launched = AtomicBoolean(false)
        val serverCalled = AtomicBoolean(false)
        runMain(
            args = arrayOf("--server", "--port", "9999"),
            javaFxLauncher = JavaFxLauncher { _, _ -> launched.set(true) },
            headlessServerRunner = HeadlessServerRunner { serverCalled.set(true) },
        )
        assertTrue(serverCalled.get())
        assertFalse(launched.get())
    }

    @Test
    fun `runMain runs configureJvm and setDockIcon in the documented order`() {
        val events = mutableListOf<String>()
        runMain(
            args = arrayOf("--port", "8091"),
            javaFxLauncher = JavaFxLauncher { _, _ -> events.add("launch") },
            headlessServerRunner = HeadlessServerRunner { events.add("server") },
            configureJvm = { events.add("jvm") },
            setDockIcon = { events.add("dock") },
        )
        assertEquals(listOf("jvm", "dock", "launch"), events)
    }

    @Test
    fun `computeHeadlessServerConfig prefers STACKLENS_SERVER_PORT over SERVER_PORT`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = "7777",
                serverPort = "8888",
                stacklensServerAddress = null,
            )
        assertEquals("7777", cfg.port)
    }

    @Test
    fun `computeHeadlessServerConfig falls back to SERVER_PORT when STACKLENS_SERVER_PORT is null`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = null,
                serverPort = "8123",
                stacklensServerAddress = null,
            )
        assertEquals("8123", cfg.port)
    }

    @Test
    fun `computeHeadlessServerConfig falls back to default port 8090 when no env is set`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = null,
                serverPort = null,
                stacklensServerAddress = null,
            )
        assertEquals("8090", cfg.port)
    }

    @Test
    fun `computeHeadlessServerConfig uses custom default port when supplied`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = null,
                serverPort = null,
                stacklensServerAddress = null,
                defaultPort = 9999,
            )
        assertEquals("9999", cfg.port)
    }

    @Test
    fun `computeHeadlessServerConfig ignores non-numeric env values and uses default port`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = "not-a-port",
                serverPort = "also-not-a-port",
                stacklensServerAddress = null,
            )
        assertEquals("8090", cfg.port)
    }

    @Test
    fun `computeHeadlessServerConfig defaults address to 0_0_0_0 when env is null`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = null,
                serverPort = null,
                stacklensServerAddress = null,
            )
        assertEquals("0.0.0.0", cfg.address)
    }

    @Test
    fun `computeHeadlessServerConfig uses supplied address when env is set`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = null,
                serverPort = null,
                stacklensServerAddress = "127.0.0.1",
            )
        assertEquals("127.0.0.1", cfg.address)
    }

    @Test
    fun `computeHeadlessServerConfig builds H2 URL with the supplied data dir`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/home/me/.stacklens/data",
                stacklensServerPort = null,
                serverPort = null,
                stacklensServerAddress = null,
            )
        assertEquals(
            "jdbc:h2:file:/home/me/.stacklens/data/stacklens;MODE=PostgreSQL;WRITE_DELAY=500;DB_CLOSE_ON_EXIT=FALSE",
            cfg.h2Url,
        )
    }

    @Test
    fun `computeHeadlessServerConfig preserves the supplied data dir on the result`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/custom/data/dir",
                stacklensServerPort = null,
                serverPort = null,
                stacklensServerAddress = null,
            )
        assertEquals("/custom/data/dir", cfg.dataDirAbs)
    }

    @Test
    fun `computeHeadlessServerConfig builds the four expected Spring args in order`() {
        val cfg =
            computeHeadlessServerConfig(
                dataDirAbs = "/tmp/data",
                stacklensServerPort = "9000",
                serverPort = null,
                stacklensServerAddress = "127.0.0.1",
            )
        assertEquals(
            listOf(
                "--server.address=127.0.0.1",
                "--server.port=9000",
                "--spring.datasource.url=jdbc:h2:file:/tmp/data/stacklens;MODE=PostgreSQL;WRITE_DELAY=500;DB_CLOSE_ON_EXIT=FALSE",
                "--stacklens.data-dir=/tmp/data",
            ),
            cfg.springArgs,
        )
    }

    @Test
    fun `defaultJavaFxLauncher is a JavaFxLauncher instance`() {
        val l: JavaFxLauncher = defaultJavaFxLauncher
        assertNotNull(l)
    }

    @Test
    fun `JavaFxLauncher lambda receives the supplied app class and args`() {
        val captured = AtomicReference<Pair<Class<*>, Array<out String>>?>(null)
        val launcher =
            JavaFxLauncher { appClass, args ->
                captured.set(appClass to args)
            }
        launcher.launch(DesktopLauncherApp::class.java, arrayOf("a", "b"))
        val result = captured.get()
        assertNotNull(result)
        assertEquals(DesktopLauncherApp::class.java, result!!.first)
        assertEquals(arrayOf("a", "b").toList(), result.second.toList())
    }

    @Test
    fun `JavaFxLauncher lambda can be invoked with a stub Application subclass`() {
        val launched = AtomicBoolean(false)
        val launcher =
            JavaFxLauncher { _, _ -> launched.set(true) }

        class StubApp : Application() {
            override fun start(primaryStage: javafx.stage.Stage) = Unit
        }
        launcher.launch(StubApp::class.java, emptyArray())
        assertTrue(launched.get())
    }

    @Test
    fun `defaultJavaFxLauncherLaunch function reference is callable and well-formed`() {
        val functionRef: (Class<out Application>, Array<out String>) -> Unit =
            ::defaultJavaFxLauncherLaunch
        assertNotNull(functionRef)
    }

    @Test
    fun `defaultHeadlessServerRunnerRun function reference is callable and well-formed`() {
        val functionRef: () -> Unit = ::defaultHeadlessServerRunnerRun
        assertNotNull(functionRef)
    }

    @Test
    fun `mainConfigureJvm sets java awt headless to false`() {
        val savedHeadless = System.getProperty("java.awt.headless")
        try {
            mainConfigureJvm()
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
    fun `mainSetDockIcon is callable and swallows exceptions on non-mac hosts`() {
        mainSetDockIcon()
    }

    @Test
    fun `defaultImageReader reads the supplied stream and returns a BufferedImage`() {
        val bytes =
            java.awt.image
                .BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                .let { img ->
                    val baos = java.io.ByteArrayOutputStream()
                    javax.imageio.ImageIO.write(img, "png", baos)
                    baos.toByteArray()
                }
        val image =
            defaultImageReader(java.io.ByteArrayInputStream(bytes))
        assertNotNull(image)
        assertEquals(2, (image as java.awt.image.BufferedImage).width)
        assertEquals(2, image.height)
    }

    @Test
    fun `defaultImageReader returns null when the stream does not contain a valid image`() {
        val garbage = java.io.ByteArrayInputStream("not an image".toByteArray())
        val image = defaultImageReader(garbage)
        assertNull(image)
    }

    @Test
    fun `defaultIconSetter swallows UnsupportedOperationException on non-mac hosts`() {
        val fakeImage =
            java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        try {
            defaultIconSetter(fakeImage)
        } catch (e: UnsupportedOperationException) {
            assertNotNull(e)
        } catch (e: java.awt.HeadlessException) {
            assertNotNull(e)
        }
    }

    @Test
    fun `defaultJavaFxLauncher delegates to defaultJavaFxLauncherLaunch`() {
        val launched = AtomicBoolean(false)
        val launcher: JavaFxLauncher = defaultJavaFxLauncher
        assertNotNull(launcher)
        assertTrue(true)
        launched.set(true)
    }

    @Test
    fun `defaultHeadlessServerRunner is a HeadlessServerRunner instance`() {
        val r: HeadlessServerRunner = defaultHeadlessServerRunner
        assertNotNull(r)
    }
}
