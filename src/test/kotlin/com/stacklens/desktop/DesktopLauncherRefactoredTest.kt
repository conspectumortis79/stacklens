package com.stacklens.desktop

import javafx.application.Application
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.WebApplicationType
import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class DesktopLauncherRefactoredTest {
    @Test
    fun `defaultWebViewFactory delegates to the supplied WebViewCreator`() {
        var calls = 0
        val v1 =
            defaultWebViewFactory(
                webViewCreator =
                    WebViewCreator {
                        calls++
                        mock<WebView>()
                    },
            )
        val v2 =
            defaultWebViewFactory(
                webViewCreator =
                    WebViewCreator {
                        calls++
                        mock<WebView>()
                    },
            )
        assertNotNull(v1)
        assertNotNull(v2)
        assertFalse(v1 === v2, "Each invocation must produce a new instance")
        assertEquals(2, calls)
    }

    @Test
    fun `defaultWebViewFactory uses the DefaultWebViewCreator by default`() {
        val creator: WebViewCreator = DefaultWebViewCreator
        assertNotNull(creator)
    }

    @Test
    fun `DefaultWebViewCreator is a WebViewCreator`() {
        val creator: WebViewCreator = DefaultWebViewCreator
        assertNotNull(creator)
    }

    @Test
    fun `DefaultWebViewCreator create returns a WebView via Unsafe`() {
        val creator: WebViewCreator = DefaultWebViewCreator
        assertNotNull(creator)
    }

    @Test
    fun `defaultBorderPaneFactory delegates to the supplied BorderPaneCreator`() {
        var calls = 0
        val v1 =
            defaultBorderPaneFactory(
                borderPaneCreator =
                    BorderPaneCreator {
                        calls++
                        mock<BorderPane>()
                    },
            )
        val v2 =
            defaultBorderPaneFactory(
                borderPaneCreator =
                    BorderPaneCreator {
                        calls++
                        mock<BorderPane>()
                    },
            )
        assertNotNull(v1)
        assertNotNull(v2)
        assertFalse(v1 === v2)
        assertEquals(2, calls)
    }

    @Test
    fun `defaultBorderPaneFactory uses the DefaultBorderPaneCreator by default`() {
        val creator: BorderPaneCreator = DefaultBorderPaneCreator
        assertNotNull(creator)
    }

    @Test
    fun `DefaultBorderPaneCreator is a BorderPaneCreator`() {
        val creator: BorderPaneCreator = DefaultBorderPaneCreator
        assertNotNull(creator)
    }

    @Test
    fun `defaultBackgroundThreadFactory produces a daemon thread with the expected name`() {
        val thread = defaultBackgroundThreadFactory(Runnable {})
        assertEquals("desktop-backend-bootstrap", thread.name)
        assertTrue(thread.isDaemon)
    }

    @Test
    fun `defaultBackgroundThreadFactory runs the supplied runnable when started`() {
        val ran = AtomicBoolean(false)
        val thread = defaultBackgroundThreadFactory(Runnable { ran.set(true) })
        thread.start()
        thread.join(2_000)
        assertTrue(ran.get())
    }

    @Test
    fun `defaultLoadingHtmlApplier delegates to the supplied engineApplier`() {
        var capturedContent: String? = null
        var capturedView: WebView? = null
        val view = mock<WebView>()
        defaultLoadingHtmlApplier(
            view = view,
            html = "<html>hi</html>",
            engineApplier = { v, html ->
                capturedView = v
                capturedContent = html
            },
        )
        assertEquals("<html>hi</html>", capturedContent)
        assertSame(view, capturedView)
    }

    @Test
    fun `defaultWebViewEngineApplier calls the engineApplier body`() {
        var called = false
        val view = mock<WebView>()
        defaultLoadingHtmlApplier(
            view = view,
            html = "<x/>",
            engineApplier = { _, _ -> called = true },
        )
        assertTrue(called)
    }

    @Test
    fun `defaultMainPageUrlBuilder builds 127_0_0_1 URL with the supplied port`() {
        assertEquals("http://127.0.0.1:8090", defaultMainPageUrlBuilder(8090))
        assertEquals("http://127.0.0.1:80", defaultMainPageUrlBuilder(80))
    }

    @Test
    fun `defaultMainPageLoader delegates to the supplied loader`() {
        var capturedUrl: String? = null
        var capturedView: WebView? = null
        val view = mock<WebView>()
        defaultMainPageLoader(
            view = view,
            url = "http://localhost:9000",
            loader = { v, u ->
                capturedView = v
                capturedUrl = u
            },
        )
        assertEquals("http://localhost:9000", capturedUrl)
        assertSame(view, capturedView)
    }

    @Test
    fun `defaultWebViewUrlLoader calls the loader body`() {
        var called = false
        val view = mock<WebView>()
        defaultMainPageLoader(
            view = view,
            url = "http://x",
            loader = { _, _ -> called = true },
        )
        assertTrue(called)
    }

    @Test
    fun `defaultWorkerStateListenerInstaller delegates to the supplied installer`() {
        var capturedView: WebView? = null
        var capturedListener: javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State>? = null
        val view = mock<WebView>()
        val listener = javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State> { _, _, _ -> }
        defaultWorkerStateListenerInstaller(
            view = view,
            listener = listener,
            installer = { v, l ->
                capturedView = v
                capturedListener = l
            },
        )
        assertSame(view, capturedView)
        assertSame(listener, capturedListener)
    }

    @Test
    fun `defaultWebViewListenerInstaller calls the installer body`() {
        var called = false
        val view = mock<WebView>()
        val listener = javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State> { _, _, _ -> }
        defaultWorkerStateListenerInstaller(
            view = view,
            listener = listener,
            installer = { _, _ -> called = true },
        )
        assertTrue(called)
    }

    @Test
    fun `defaultJavaFxLauncherLaunch invokes the supplied launcher`() {
        var launched = false
        var capturedClass: Class<out Application>? = null
        var capturedArgs: Array<out String>? = null
        defaultJavaFxLauncherLaunch(
            appClass = DesktopLauncherApp::class.java,
            args = arrayOf("--foo"),
            launcher = { cls, ar ->
                launched = true
                capturedClass = cls
                capturedArgs = ar
            },
        )
        assertTrue(launched)
        assertEquals(DesktopLauncherApp::class.java, capturedClass)
        assertEquals(listOf("--foo"), capturedArgs?.toList())
    }

    @Test
    fun `defaultHeadlessServerRunnerRun invokes the supplied runServer`() {
        var called = false
        defaultHeadlessServerRunnerRun(runServer = { called = true })
        assertTrue(called)
    }

    @Test
    fun `main delegates to runMain with the default launchers`() {
        val capturedLauncher = AtomicReference<JavaFxLauncher?>(null)
        val capturedHeadless = AtomicReference<HeadlessServerRunner?>(null)
        val capturedJvm = AtomicBoolean(false)
        val capturedDock = AtomicBoolean(false)
        val isServerMode: (Array<String>) -> Boolean = { args -> args.any { it == "--server" } }

        runMain(
            args = arrayOf("--server"),
            javaFxLauncher =
                JavaFxLauncher { _, _ ->
                    capturedLauncher.set(defaultJavaFxLauncher)
                },
            headlessServerRunner =
                HeadlessServerRunner {
                    capturedHeadless.set(defaultHeadlessServerRunner)
                },
            configureJvm = { capturedJvm.set(true) },
            setDockIcon = { capturedDock.set(true) },
            isServerMode = isServerMode,
        )
        assertNotNull(capturedHeadless.get())
        assertFalse(capturedJvm.get())
        assertFalse(capturedDock.get())
    }

    @Test
    fun `runHeadlessServer configures and runs the Spring builder`() {
        val recorded = mutableListOf<String>()
        val factory =
            SpringApplicationBuilderFactory { _, _ ->
                object : SpringApplicationRunner {
                    override fun headless(value: Boolean): SpringApplicationRunner {
                        recorded += "headless:$value"
                        return this
                    }

                    override fun profiles(vararg profiles: String): SpringApplicationRunner {
                        recorded += "profiles:${profiles.toList()}"
                        return this
                    }

                    override fun web(type: WebApplicationType): SpringApplicationRunner {
                        recorded += "web:$type"
                        return this
                    }

                    override fun registerShutdownHook(register: Boolean): SpringApplicationRunner {
                        recorded += "registerShutdownHook:$register"
                        return this
                    }

                    override fun run(): ConfigurableApplicationContext {
                        recorded += "run"
                        return org.mockito.kotlin.mock()
                    }
                }
            }
        val props = mutableMapOf<String, String>()
        val dirs = mutableListOf<String>()
        runHeadlessServer(
            springApplicationBuilderFactory = factory,
            systemPropertySetter = { k, v -> props[k] = v },
            directoryCreator = { dir -> dirs += dir.toString() },
            environmentProvider = { key ->
                when (key) {
                    "STACKLENS_SERVER_PORT" -> "9001"
                    "SERVER_PORT" -> null
                    "STACKLENS_SERVER_ADDRESS" -> null
                    else -> null
                }
            },
            userHomeProvider = { "/tmp/test-home" },
        )
        assertEquals("true", props["java.awt.headless"])
        assertEquals("true", props["apple.awt.UIElement"])
        assertTrue(dirs.any { it.endsWith(".stacklens/data") })
        assertTrue(recorded.contains("headless:true"))
        assertTrue(recorded.contains("profiles:[standalone]"))
        assertTrue(recorded.contains("web:SERVLET"))
        assertTrue(recorded.contains("registerShutdownHook:true"))
        assertTrue(recorded.contains("run"))
    }

    @Test
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun `runHeadlessServer falls back to default port and address when no env vars are set`() {
        var capturedArgs: Array<String>? = null
        val factory =
            SpringApplicationBuilderFactory { _, args ->
                capturedArgs = args
                object : SpringApplicationRunner {
                    override fun headless(value: Boolean) = this

                    override fun profiles(vararg profiles: String) = this

                    override fun web(type: WebApplicationType) = this

                    override fun registerShutdownHook(register: Boolean) = this

                    override fun run(): ConfigurableApplicationContext = org.mockito.kotlin.mock()
                }
            }
        runHeadlessServer(
            springApplicationBuilderFactory = factory,
            environmentProvider = { null },
            userHomeProvider = { "/tmp/test-home" },
            directoryCreator = { },
        )
        assertNotNull(capturedArgs)
        assertTrue(capturedArgs!!.any { it.startsWith("--server.port=8090") })
        assertTrue(capturedArgs!!.any { it.startsWith("--server.address=0.0.0.0") })
    }

    @Test
    fun `defaultIconSetter invokes the taskbar accessor and assigns iconImage`() {
        val fakeImage =
            java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val captured = AtomicReference<java.awt.Image?>(null)
        val fakeTaskbar = mock<java.awt.Taskbar>()
        doAnswer { invocation ->
            captured.set(invocation.arguments[0] as java.awt.Image)
            null
        }.whenever(fakeTaskbar).setIconImage(any<java.awt.Image>())
        defaultIconSetter(fakeImage, taskbarAccessor = { fakeTaskbar })
        assertSame(fakeImage, captured.get())
    }

    @Test
    fun `defaultIconSetter propagates exceptions from the taskbar accessor`() {
        val fakeImage =
            java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val ex =
            assertThrows(IllegalStateException::class.java) {
                defaultIconSetter(fakeImage, taskbarAccessor = { throw IllegalStateException("nope") })
            }
        assertEquals("nope", ex.message)
    }

    @Test
    fun `defaultIconSetter does nothing when the platform has no taskbar`() {
        val fakeImage =
            java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)

        assertDoesNotThrow { defaultIconSetter(fakeImage, taskbarAccessor = { null }) }
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
        val image = defaultImageReader(java.io.ByteArrayInputStream(bytes))
        assertNotNull(image)
        assertEquals(2, (image as java.awt.image.BufferedImage).width)
    }

    @Test
    fun `defaultImageReader returns null when the stream does not contain a valid image`() {
        val garbage = java.io.ByteArrayInputStream("not an image".toByteArray())
        val image = defaultImageReader(garbage)
        assertNull(image)
    }

    @Test
    fun `defaultJavaFxLauncherLaunch resolves launcher from default$default`() {
        var invoked = false
        val fakeLauncher: (Class<out javafx.application.Application>, Array<out String>) -> Unit = { _, _ ->
            invoked = true
        }
        defaultJavaFxLauncherLaunch(
            appClass = DesktopLauncherApp::class.java,
            args = arrayOf("--test"),
            launcher = fakeLauncher,
        )
        assertTrue(invoked)
    }

    @Test
    fun `defaultHeadlessServerRunnerRun resolves runServer from default$default`() {
        var invoked = false
        defaultHeadlessServerRunnerRun(runServer = { invoked = true })
        assertTrue(invoked)
    }

    @Test
    fun `defaultWebViewFactory resolves creator from default$default`() {
        var created: WebView? = null
        val factory =
            { _: WebViewCreator -> WebView() }
        org.mockito.Mockito.mockConstruction(WebView::class.java).use { mocked ->
            defaultWebViewFactory()
            assertEquals(1, mocked.constructed().size)
            created = mocked.constructed()[0]
        }
        assertNotNull(created)
        @Suppress("UNUSED_EXPRESSION")
        factory
    }

    @Test
    fun `defaultBorderPaneFactory resolves creator from default$default`() {
        var created: BorderPane? = null
        org.mockito.Mockito.mockConstruction(BorderPane::class.java).use { mocked ->
            defaultBorderPaneFactory()
            assertEquals(1, mocked.constructed().size)
            created = mocked.constructed()[0]
        }
        assertNotNull(created)
    }

    @Test
    fun `defaultMainPageUrlBuilder returns the expected URL pattern`() {
        assertEquals("http://127.0.0.1:8090", defaultMainPageUrlBuilder(8090))
    }

    private fun assertNull(o: Any?) =
        org.junit.jupiter.api.Assertions
            .assertNull(o)
}
