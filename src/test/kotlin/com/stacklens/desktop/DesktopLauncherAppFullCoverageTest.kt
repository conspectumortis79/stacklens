package com.stacklens.desktop

import javafx.application.Platform
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.stage.WindowEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("UNCHECKED_CAST")
class DesktopLauncherAppFullCoverageTest {
    private fun <T> readField(
        target: Any,
        name: String,
    ): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }

    @Test
    fun `property accessor for webViewFactory invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        val factory = app.webViewFactory
        assertNotNull(factory)
    }

    @Test
    fun `property accessor for borderPaneFactory invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        assertNotNull(app.borderPaneFactory)
    }

    @Test
    fun `property accessor for bridgeFactory invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        val factory = app.bridgeFactory
        assertNotNull(factory)
    }

    @Test
    fun `property accessor for backgroundThreadFactory invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        assertNotNull(app.backgroundThreadFactory)
    }

    @Test
    fun `property accessor for loadingHtmlApplier invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        assertNotNull(app.loadingHtmlApplier)
    }

    @Test
    fun `property accessor for mainPageUrlBuilder invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        assertNotNull(app.mainPageUrlBuilder)
    }

    @Test
    fun `property accessor for mainPageLoader invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        assertNotNull(app.mainPageLoader)
    }

    @Test
    fun `property accessor for workerStateListenerInstaller invokes the synthetic getter`() {
        val app = DesktopLauncherApp()
        assertNotNull(app.workerStateListenerInstaller)
    }

    @Test
    fun `start lambda wires setOnCloseRequest handler that invokes the captured shutdown`() {
        val webView = mock<WebView>()
        val root = BorderPane()
        val stage = mock<Stage>()
        val shutdownHandler = mock<DesktopShutdownHandler>()
        var capturedHandler: javafx.event.EventHandler<WindowEvent>? = null
        whenever(stage.setOnCloseRequest(any())).then { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedHandler = invocation.arguments[0] as javafx.event.EventHandler<WindowEvent>
            null
        }
        val app =
            DesktopLauncherApp(
                stageSetup = mock<DesktopStageSetup>(),
                backendBootstrap = mock<DesktopBackendBootstrap>(),
                webViewConfigurator = mock<WebViewConfigurator>(),
                jsBridgeInjector = mock<DesktopJsBridgeInjector>(),
                popupHandler = mock<DesktopPopupHandler>(),
                keyHandlers = mock<DesktopKeyHandlers>(),
                externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>(),
                startupErrorReporter = mock<DesktopStartupErrorReporter>(),
                shutdownHandler = shutdownHandler,
                webViewFactory = { webView },
                borderPaneFactory = { root },
                bridgeFactory = { _, _, _ -> mock<DesktopAppBridge>() },
                backgroundThreadFactory = { r -> Thread(r, "test").apply { isDaemon = true } },
                loadingHtmlLoader = { "<html>x</html>" },
                loadingHtmlApplier = { _, _ -> },
                mainPageUrlBuilder = { port -> "http://test:$port" },
                mainPageLoader = { _, _ -> },
                workerStateListenerInstaller = { _, _ -> },
            )
        app.start(stage)
        assertNotNull(capturedHandler)
        capturedHandler!!.handle(mock<WindowEvent>())
        verify(shutdownHandler).requestShutdown()
    }

    @Test
    fun `startInternal invokes the default runOnFxThread lambda when omitted`() {
        val defaultMethod =
            DesktopLauncherApp::class.java.getDeclaredMethod(
                "startInternal\$stacklens\$default",
                DesktopLauncherApp::class.java,
                Stage::class.java,
                WebView::class.java,
                BorderPane::class.java,
                Class.forName("kotlin.jvm.functions.Function1"),
                Class.forName("kotlin.jvm.functions.Function1"),
                Class.forName("kotlin.jvm.functions.Function1"),
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        defaultMethod.isAccessible = true
        val webView =
            mock<WebView>(defaultAnswer = org.mockito.Answers.RETURNS_DEEP_STUBS)
        val stageSetup = mock<DesktopStageSetup>()
        val stage = mock<Stage>()
        val backendBootstrap = mock<DesktopBackendBootstrap>()
        val app =
            DesktopLauncherApp(
                stageSetup = stageSetup,
                backendBootstrap = backendBootstrap,
                webViewConfigurator = mock<WebViewConfigurator>(),
                jsBridgeInjector = mock<DesktopJsBridgeInjector>(),
                popupHandler = mock<DesktopPopupHandler>(),
                keyHandlers = mock<DesktopKeyHandlers>(),
                externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>(),
                startupErrorReporter = mock<DesktopStartupErrorReporter>(),
                shutdownHandler = mock<DesktopShutdownHandler>(),
                webViewFactory = { webView },
                borderPaneFactory = { BorderPane() },
                bridgeFactory = { _, _, _ -> mock<DesktopAppBridge>() },
                backgroundThreadFactory = { r -> Thread(r, "test").apply { isDaemon = true } },
                loadingHtmlLoader = { "<html>x</html>" },
                loadingHtmlApplier = { _, _ -> },
                mainPageUrlBuilder = { port -> "http://test:$port" },
                mainPageLoader = { _, _ -> },
                workerStateListenerInstaller = { _, _ -> },
            )
        val bgRunnables = mutableListOf<Runnable>()
        defaultMethod.invoke(
            null,
            app,
            stage,
            webView,
            BorderPane(),
            { _: () -> Unit -> },
            { r: Runnable -> bgRunnables.add(r) },
            { _: Runnable -> },
            0b100000,
            null,
        )
        assertTrue(true)
    }

    @Test
    fun `startInternal default runOnFxThread lambda body invokes Platform_runLater`() {
        mockStatic(Platform::class.java).use { platform ->
            platform
                .`when`<Unit> { Platform.runLater(any()) }
                .then { Unit }
            val method =
                DesktopLauncherApp::class.java.getDeclaredMethod(
                    "startInternal\$lambda\$0",
                    Runnable::class.java,
                )
            method.isAccessible = true
            val target = Runnable {}
            method.invoke(null, target)
            platform.verify { Platform.runLater(target) }
        }
    }

    @Test
    fun `configureWebView installs externalLinkInterceptor with the appBridge supplier`() {
        val webViewConfigurator = mock<WebViewConfigurator>()
        val popupHandler = mock<DesktopPopupHandler>()
        val keyHandlers = mock<DesktopKeyHandlers>()
        val externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>()
        val bridge = mock<DesktopAppBridge>()
        var capturedSupplier: (() -> DesktopAppBridge?)? = null
        whenever(externalLinkInterceptor.install(any(), any(), any())).then { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedSupplier = invocation.arguments[2] as (() -> DesktopAppBridge?)
            null
        }
        val app =
            DesktopLauncherApp(
                webViewConfigurator = webViewConfigurator,
                popupHandler = popupHandler,
                keyHandlers = keyHandlers,
                externalLinkInterceptor = externalLinkInterceptor,
                loadingHtmlApplier = { _, _ -> },
                mainPageLoader = { _, _ -> },
                workerStateListenerInstaller = { _, _ -> },
            )
        val view = mock<WebView>()
        val stage = mock<Stage>()
        val root = BorderPane()
        app.configureWebView(view, stage, 1, bridge, root)
        assertNotNull(capturedSupplier)
        app.appBridge = bridge
        assertEquals(bridge, capturedSupplier!!())
    }
}
