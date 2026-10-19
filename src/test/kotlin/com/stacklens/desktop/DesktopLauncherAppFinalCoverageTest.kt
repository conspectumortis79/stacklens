@file:Suppress("DEPRECATION", "UNCHECKED_CAST")

package com.stacklens.desktop

import com.stacklens.testsupport.FakeBackendLifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DesktopLauncherAppFinalCoverageTest {
    @Test
    fun `no arg launcher wiring hands one backend lifecycle to boot and shutdown`() {
        val app = DesktopLauncherApp()

        val backend = readField<Any>(app, "backendBootstrap")
        val shutdownHandler = readField<Any>(app, "shutdownHandler")

        assertSame(backend, readField<Any>(shutdownHandler, "backend"))
    }

    @Test
    fun `startInternal composes the launcher lifecycle into the app bridge`() {
        val backend = FakeBackendLifecycle(port = 8097)
        val app =
            DesktopLauncherApp(
                stageSetup = mock<DesktopStageSetup>(),
                backendBootstrap = backend,
                webViewConfigurator = mock<WebViewConfigurator>(),
                jsBridgeInjector = mock<DesktopJsBridgeInjector>(),
                popupHandler = mock<DesktopPopupHandler>(),
                keyHandlers = mock<DesktopKeyHandlers>(),
                externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>(),
                startupErrorReporter = mock<DesktopStartupErrorReporter>(),
                shutdownHandler = mock<DesktopShutdownHandler>(),
                webViewFactory = { mock<javafx.scene.web.WebView>() },
                borderPaneFactory = { javafx.scene.layout.BorderPane() },
                backgroundThreadFactory = { r -> Thread(r, "test") },
                loadingHtmlApplier = { _, _ -> },
                mainPageLoader = { _, _ -> },
                workerStateListenerInstaller = { _, _ -> },
            )
        var background: Runnable? = null
        var onFx: Runnable? = null

        app.startInternal(
            stage = mock<javafx.stage.Stage>(),
            loadingView = mock<javafx.scene.web.WebView>(),
            root = javafx.scene.layout.BorderPane(),
            shutdownRegistrar = {},
            runBackground = { background = it },
            runOnFxThread = { onFx = it },
        )
        background!!.run()
        onFx!!.run()

        val bridge = readField<Any>(app, "appBridge")
        val coordinator = readField<Any>(bridge, "quitCoordinator")

        assertSame(backend, readField<Any>(coordinator, "backend"))
        assertEquals(1, backend.starts)
    }

    @Test
    fun `default app bridge wires the injected lifecycle into the quit coordinator`() {
        val backend = FakeBackendLifecycle()
        val bridge = defaultAppBridge(mock<javafx.stage.Stage>(), 8085, backend)

        val coordinator = readField<Any>(bridge, "quitCoordinator")

        assertSame(backend, readField<Any>(coordinator, "backend"))
    }

    @Test
    fun `default backgroundThreadFactory lambda body creates a daemon thread`() {
        val app = DesktopLauncherApp()
        val factory = readField<Any>(app, "backgroundThreadFactory") as (Runnable) -> Thread
        val t = factory {}
        assertEquals("desktop-backend-bootstrap", t.name)
        assertTrue(t.isDaemon)
    }

    @Test
    fun `default loadingHtmlLoader lambda body returns non-empty HTML`() {
        val app = DesktopLauncherApp()
        val loader = readField<Any>(app, "loadingHtmlLoader") as () -> String
        val html = loader()
        assertTrue(html.isNotBlank())
    }

    @Test
    fun `default mainPageUrlBuilder lambda body builds the loopback URL`() {
        val app = DesktopLauncherApp()
        val builder = readField<Any>(app, "mainPageUrlBuilder") as (Int) -> String
        assertEquals("http://127.0.0.1:8080", builder(8080))
        assertEquals("http://127.0.0.1:0", builder(0))
        assertEquals("http://127.0.0.1:65535", builder(65535))
    }

    @Test
    fun `default bridgeFactory lambda body returns a DesktopAppBridge`() {
        val app = DesktopLauncherApp()
        val factory =
            readField<Any>(app, "bridgeFactory") as (javafx.stage.Stage, Int, BackendLifecycle) -> DesktopAppBridge
        val stage = mock<javafx.stage.Stage>()
        val bridge = factory(stage, 9001, DesktopBackendBootstrap())
        assertNotNull(bridge)
    }

    @Test
    fun `configureWebView worker-state listener calls injectOnLoad when SUCCEEDED`() {
        val webViewConfigurator = mock<WebViewConfigurator>()
        val jsBridgeInjector = mock<DesktopJsBridgeInjector>()
        val popupHandler = mock<DesktopPopupHandler>()
        val keyHandlers = mock<DesktopKeyHandlers>()
        val externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>()
        val bridge = mock<DesktopAppBridge>()
        val view = mock<javafx.scene.web.WebView>()
        var captured: javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State>? = null
        val app =
            DesktopLauncherApp(
                webViewConfigurator = webViewConfigurator,
                jsBridgeInjector = jsBridgeInjector,
                popupHandler = popupHandler,
                keyHandlers = keyHandlers,
                externalLinkInterceptor = externalLinkInterceptor,
                workerStateListenerInstaller = { _, listener -> captured = listener },
                loadingHtmlApplier = { _, _ -> },
                mainPageLoader = { _, _ -> },
            )
        val root = javafx.scene.layout.BorderPane()
        app.configureWebView(view, mock<javafx.stage.Stage>(), 9001, bridge, root)
        assertNotNull(captured)
        captured!!.changed(
            mock<javafx.beans.value.ObservableValue<javafx.concurrent.Worker.State>>(),
            javafx.concurrent.Worker.State.RUNNING,
            javafx.concurrent.Worker.State.SUCCEEDED,
        )
        verify(jsBridgeInjector).injectOnLoad(view, bridge, 9001)
    }

    @Test
    fun `configureWebView worker-state listener does NOT call injectOnLoad for non-SUCCEEDED`() {
        val jsBridgeInjector = mock<DesktopJsBridgeInjector>()
        val webViewConfigurator = mock<WebViewConfigurator>()
        val popupHandler = mock<DesktopPopupHandler>()
        val keyHandlers = mock<DesktopKeyHandlers>()
        val externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>()
        var captured: javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State>? = null
        val app =
            DesktopLauncherApp(
                webViewConfigurator = webViewConfigurator,
                jsBridgeInjector = jsBridgeInjector,
                popupHandler = popupHandler,
                keyHandlers = keyHandlers,
                externalLinkInterceptor = externalLinkInterceptor,
                workerStateListenerInstaller = { _, listener -> captured = listener },
                loadingHtmlApplier = { _, _ -> },
                mainPageLoader = { _, _ -> },
            )
        val view = mock<javafx.scene.web.WebView>()
        val bridge = mock<DesktopAppBridge>()
        val root = javafx.scene.layout.BorderPane()
        app.configureWebView(view, mock<javafx.stage.Stage>(), 9001, bridge, root)
        assertNotNull(captured)
        captured!!.changed(
            mock<javafx.beans.value.ObservableValue<javafx.concurrent.Worker.State>>(),
            javafx.concurrent.Worker.State.SCHEDULED,
            javafx.concurrent.Worker.State.RUNNING,
        )
        org.mockito.kotlin.verifyNoInteractions(jsBridgeInjector)
    }

    @Test
    fun `DefaultStartupRunner is a StartupRunner implementation`() {
        val sr: StartupRunner = DefaultStartupRunner
        assertNotNull(sr)
        assertTrue(DefaultStartupRunner === DefaultStartupRunner)
    }

    @Test
    fun `appBridge is null before configureWebView runs`() {
        val app = DesktopLauncherApp()
        assertNull(app.appBridge)
    }

    @Test
    fun `appBridge can be assigned through the setter`() {
        val app = DesktopLauncherApp()
        val bridge = mock<DesktopAppBridge>()
        app.appBridge = bridge
        assertEquals(bridge, app.appBridge)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readField(
        target: Any,
        name: String,
    ): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }
}
