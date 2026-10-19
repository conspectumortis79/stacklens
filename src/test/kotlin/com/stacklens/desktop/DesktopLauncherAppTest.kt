package com.stacklens.desktop

import javafx.scene.layout.BorderPane
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopLauncherAppTest {
    @Test
    fun `no-arg constructor uses defaults`() {
        val app = DesktopLauncherApp()
        val stageSetupField = DesktopLauncherApp::class.java.getDeclaredField("stageSetup")
        stageSetupField.isAccessible = true
        val stageSetupValue = stageSetupField.get(app)
        assertNotNull(stageSetupValue)
    }

    @Test
    fun `no-arg constructor invokes each default factory lambda at least once`() {
        val app = DesktopLauncherApp()

        val webViewFactoryField =
            DesktopLauncherApp::class.java.getDeclaredField("webViewFactory")
        webViewFactoryField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val webViewFactory = webViewFactoryField.get(app) as () -> javafx.scene.web.WebView
        assertNotNull(webViewFactory)

        val borderPaneFactoryField =
            DesktopLauncherApp::class.java.getDeclaredField("borderPaneFactory")
        borderPaneFactoryField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val borderPaneFactory = borderPaneFactoryField.get(app) as () -> javafx.scene.layout.BorderPane
        assertNotNull(borderPaneFactory)

        val bridgeFactoryField =
            DesktopLauncherApp::class.java.getDeclaredField("bridgeFactory")
        bridgeFactoryField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val bridgeFactory = bridgeFactoryField.get(app) as (javafx.stage.Stage, Int, BackendLifecycle) -> DesktopAppBridge
        val bridge = bridgeFactory(mock<javafx.stage.Stage>(), 1234, DesktopBackendBootstrap())
        assertNotNull(bridge)

        val backgroundThreadFactoryField =
            DesktopLauncherApp::class.java.getDeclaredField("backgroundThreadFactory")
        backgroundThreadFactoryField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val backgroundThreadFactory = backgroundThreadFactoryField.get(app) as (Runnable) -> Thread
        val thread = backgroundThreadFactory(Runnable {})
        assertEquals("desktop-backend-bootstrap", thread.name)
        assertEquals(true, thread.isDaemon)

        val loadingHtmlLoaderField =
            DesktopLauncherApp::class.java.getDeclaredField("loadingHtmlLoader")
        loadingHtmlLoaderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val loadingHtmlLoader = loadingHtmlLoaderField.get(app) as () -> String
        val html = loadingHtmlLoader()
        assertNotNull(html)
        assertTrue(html.isNotBlank())

        val loadingHtmlApplierField =
            DesktopLauncherApp::class.java.getDeclaredField("loadingHtmlApplier")
        loadingHtmlApplierField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val loadingHtmlApplier =
            loadingHtmlApplierField.get(app) as (javafx.scene.web.WebView, String) -> Unit
        assertNotNull(loadingHtmlApplier)

        val mainPageUrlBuilderField =
            DesktopLauncherApp::class.java.getDeclaredField("mainPageUrlBuilder")
        mainPageUrlBuilderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mainPageUrlBuilder = mainPageUrlBuilderField.get(app) as (Int) -> String
        assertEquals("http://127.0.0.1:8080", mainPageUrlBuilder(8080))

        val mainPageLoaderField =
            DesktopLauncherApp::class.java.getDeclaredField("mainPageLoader")
        mainPageLoaderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mainPageLoader =
            mainPageLoaderField.get(app) as (javafx.scene.web.WebView, String) -> Unit
        assertNotNull(mainPageLoader)

        val workerStateListenerInstallerField =
            DesktopLauncherApp::class.java.getDeclaredField("workerStateListenerInstaller")
        workerStateListenerInstallerField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val workerStateListenerInstaller =
            workerStateListenerInstallerField.get(app) as (
                javafx.scene.web.WebView,
                javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State>,
            ) -> Unit
        assertNotNull(workerStateListenerInstaller)
    }

    @Test
    fun `configured constructor accepts custom dependencies`() {
        val stageSetup: DesktopStageSetup = mock()
        val backendBootstrap: DesktopBackendBootstrap = mock()
        val webViewConfigurator: WebViewConfigurator = mock()
        val jsBridgeInjector: DesktopJsBridgeInjector = mock()
        val popupHandler: DesktopPopupHandler = mock()
        val keyHandlers: DesktopKeyHandlers = mock()
        val externalLinkInterceptor: DesktopExternalLinkInterceptor = mock()
        val startupErrorReporter: DesktopStartupErrorReporter = mock()
        val shutdownHandler: DesktopShutdownHandler = mock()
        val app =
            DesktopLauncherApp(
                stageSetup = stageSetup,
                backendBootstrap = backendBootstrap,
                webViewConfigurator = webViewConfigurator,
                jsBridgeInjector = jsBridgeInjector,
                popupHandler = popupHandler,
                keyHandlers = keyHandlers,
                externalLinkInterceptor = externalLinkInterceptor,
                startupErrorReporter = startupErrorReporter,
                shutdownHandler = shutdownHandler,
                webViewFactory = { error("not called") },
                borderPaneFactory = { error("not called") },
                bridgeFactory = { _, _, _ -> error("not called") },
                backgroundThreadFactory = { error("not called") },
                loadingHtmlLoader = { "<html>loading</html>" },
            )
        assertNotNull(app)
    }

    @Test
    fun `loadingHtmlLoader default reads the bundled resource`() {
        val app = DesktopLauncherApp()
        val field = DesktopLauncherApp::class.java.getDeclaredField("loadingHtmlLoader")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val loader = field.get(app) as () -> String
        val html = loader()
        assertNotNull(html)
        assert(html.isNotBlank())
    }

    @Test
    fun `loadingHtmlLoader returns inline fallback when resource is missing`() {
        val fallbackHtml = readLoadingHtml(missingResourceStream = { null })
        assertTrue(fallbackHtml.contains("StackLens startet"))
    }

    @Test
    fun `appBridge getter returns null initially`() {
        val app = DesktopLauncherApp()
        assertEquals(null, app.appBridge)
    }

    @Test
    fun `appBridge setter accepts a bridge`() {
        val app = DesktopLauncherApp()
        val bridge: DesktopAppBridge = mock()
        app.appBridge = bridge
        assertEquals(bridge, app.appBridge)
    }

    @Test
    fun `backgroundThreadFactory default creates daemon thread`() {
        val app = DesktopLauncherApp()
        val field = DesktopLauncherApp::class.java.getDeclaredField("backgroundThreadFactory")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val factory = field.get(app) as (Runnable) -> Thread
        val thread = factory(Runnable {})
        assertEquals(true, thread.isDaemon)
        assertEquals("desktop-backend-bootstrap", thread.name)
    }

    @Test
    fun `webViewFactory default is a non-null function reference`() {
        val app = DesktopLauncherApp()
        val field = DesktopLauncherApp::class.java.getDeclaredField("webViewFactory")
        field.isAccessible = true
        val factory = field.get(app)
        assertNotNull(factory)
    }

    @Test
    fun `borderPaneFactory default is a non-null function reference`() {
        val app = DesktopLauncherApp()
        val field = DesktopLauncherApp::class.java.getDeclaredField("borderPaneFactory")
        field.isAccessible = true
        val factory = field.get(app)
        assertNotNull(factory)
    }

    @Test
    fun `bridgeFactory default is a non-null function reference`() {
        val app = DesktopLauncherApp()
        val field = DesktopLauncherApp::class.java.getDeclaredField("bridgeFactory")
        field.isAccessible = true
        val factory = field.get(app)
        assertNotNull(factory)
    }

    @Test
    fun `webViewFactory is invoked twice when backend starts`() {
        var calls = 0
        val loadingView =
            mock<javafx.scene.web.WebView>(
                defaultAnswer = org.mockito.Answers.RETURNS_DEEP_STUBS,
            )
        val newWebView =
            mock<javafx.scene.web.WebView>(
                defaultAnswer = org.mockito.Answers.RETURNS_DEEP_STUBS,
            )
        val webViewFactory: () -> javafx.scene.web.WebView = {
            calls++
            if (calls == 1) loadingView else newWebView
        }
        assertEquals(0, calls)
        webViewFactory()
        webViewFactory()
        assertEquals(2, calls)
    }

    @Test
    fun `readLoadingHtml reads the bundled resource when present`() {
        val html = readLoadingHtml()
        assertTrue(html.isNotBlank())
    }

    @Test
    fun `readLoadingHtml falls back to inline HTML when stream is null`() {
        val html = readLoadingHtml(missingResourceStream = { null })
        assertTrue(html.contains("StackLens startet"))
    }

    @Test
    fun `setMacDockIcon is a no-op on non-mac hosts`() {
        var setterCalled = false
        setMacDockIcon(
            osName = "Linux",
            iconSetter = { setterCalled = true },
        )
        assertFalse(setterCalled)
    }

    @Test
    fun `setMacDockIcon skips when icon resource is missing`() {
        var setterCalled = false
        setMacDockIcon(
            osName = "Mac OS X",
            resourceStream = { null },
            iconSetter = { setterCalled = true },
        )
        assertFalse(setterCalled)
    }

    @Test
    fun `setMacDockIcon skips when the image reader returns null`() {
        var setterCalled = false
        setMacDockIcon(
            osName = "Mac OS X",
            imageReader = { null },
            iconSetter = { setterCalled = true },
        )
        assertFalse(setterCalled)
    }

    @Test
    fun `setMacDockIcon swallows exceptions from the icon setter`() {
        setMacDockIcon(
            osName = "Mac OS X",
            imageReader = { error("invalid image") },
            iconSetter = { },
        )
        assertTrue(true)
    }

    @Test
    fun `setMacDockIcon calls the setter on a successful macOS path`() {
        var setterCalled = false
        val fakeImage =
            java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        setMacDockIcon(
            osName = "Mac OS X",
            imageReader = { fakeImage },
            iconSetter = { setterCalled = true },
        )
        assertTrue(setterCalled)
    }

    @Test
    fun `the default icon setter assigns the image to the taskbar it was given`() {
        val taskbar = mock<java.awt.Taskbar>()
        val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)

        defaultIconSetter(image) { taskbar }

        verify(taskbar).setIconImage(image)
    }

    @Test
    fun `jsSetMember reflects to invoke JSObject setMember`() {
        val captured = arrayOfNulls<Any>(2)
        val fakeJsObject =
            object {
                @Suppress("UNUSED_PARAMETER")
                fun setMember(
                    name: String,
                    value: Any,
                ) {
                    captured[0] = name
                    captured[1] = value
                }
            }
        fakeJsObject.jsSetMember("k", "v")
        assertEquals("k", captured[0])
        assertEquals("v", captured[1])
    }

    @Test
    fun `jsSetMember throws if the method is missing`() {
        val obj =
            object {
                fun noMethodHere() {}
            }
        assertThrows(ReflectiveOperationException::class.java) {
            obj.jsSetMember("k", "v")
        }
    }

    @Test
    fun `start installs close request handler that delegates to shutdown`() {
        val app = DesktopLauncherApp()
        val field = DesktopLauncherApp::class.java.getDeclaredField("shutdownHandler")
        field.isAccessible = true
        val handler = field.get(app) as DesktopShutdownHandler
        assertNotNull(handler)
    }

    @Test
    fun `startInternal happy path drives stage setup, shutdown registrar, and bootstrap success`() {
        val stageSetup = mock<DesktopStageSetup>()
        val backendBootstrap = mock<DesktopBackendBootstrap>()
        whenever(backendBootstrap.start()).thenReturn(8090)
        val webViewConfigurator = mock<WebViewConfigurator>()
        val jsBridgeInjector = mock<DesktopJsBridgeInjector>()
        val popupHandler = mock<DesktopPopupHandler>()
        val keyHandlers = mock<DesktopKeyHandlers>()
        val externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>()
        val startupErrorReporter = mock<DesktopStartupErrorReporter>()
        val shutdownHandler = mock<DesktopShutdownHandler>()

        val loadingView = mock<javafx.scene.web.WebView>()
        var webViewFactoryCalls = 0
        val webViewFactory: () -> javafx.scene.web.WebView = {
            webViewFactoryCalls++
            loadingView
        }
        val root = BorderPane()
        val bridge = mock<DesktopAppBridge>()
        var registeredShutdown: (() -> Unit)? = null
        val fxCaptured = mutableListOf<Runnable>()
        val fxRunner: (Runnable) -> Unit = { fxCaptured.add(it) }
        val bgCaptured = mutableListOf<Runnable>()
        val bgRunner: (Runnable) -> Unit = { bgCaptured.add(it) }
        val appliedHtml = mutableListOf<Pair<javafx.scene.web.WebView, String>>()
        val loadedUrls = mutableListOf<Pair<javafx.scene.web.WebView, String>>()
        val stateListeners = mutableListOf<javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State>>()
        val app =
            DesktopLauncherApp(
                stageSetup = stageSetup,
                backendBootstrap = backendBootstrap,
                webViewConfigurator = webViewConfigurator,
                jsBridgeInjector = jsBridgeInjector,
                popupHandler = popupHandler,
                keyHandlers = keyHandlers,
                externalLinkInterceptor = externalLinkInterceptor,
                startupErrorReporter = startupErrorReporter,
                shutdownHandler = shutdownHandler,
                webViewFactory = webViewFactory,
                borderPaneFactory = { root },
                bridgeFactory = { _, _, _ -> bridge },
                backgroundThreadFactory = { r -> Thread(r, "test") },
                loadingHtmlLoader = { "<html>loading</html>" },
                loadingHtmlApplier = { view, html -> appliedHtml.add(view to html) },
                mainPageUrlBuilder = { port -> "http://test:$port" },
                mainPageLoader = { view, url -> loadedUrls.add(view to url) },
                workerStateListenerInstaller = { _, listener -> stateListeners.add(listener) },
            )
        val stage = mock<javafx.stage.Stage>()
        val shutdownRegister: (() -> Unit) -> Unit = { h -> registeredShutdown = h }
        app.startInternal(
            stage = stage,
            loadingView = loadingView,
            root = root,
            shutdownRegistrar = shutdownRegister,
            runBackground = bgRunner,
            runOnFxThread = fxRunner,
        )
        assertEquals(1, appliedHtml.size)
        assertEquals(loadingView, appliedHtml[0].first)
        assertEquals("<html>loading</html>", appliedHtml[0].second)
        org.mockito.kotlin
            .verify(stageSetup)
            .setup(stage, "StackLens", "/stacklens-icon.png", root, loadingView, "/webview-noscroll.css")
        assertNotNull(registeredShutdown)
        registeredShutdown!!()
        org.mockito.kotlin
            .verify(shutdownHandler)
            .requestShutdown()
        assertEquals(1, bgCaptured.size)
        bgCaptured[0].run()
        assertEquals(1, fxCaptured.size)
        fxCaptured[0].run()
        org.mockito.kotlin
            .verify(webViewConfigurator)
            .configure(loadingView, stage)
        org.mockito.kotlin
            .verify(popupHandler)
            .install(loadingView, stage, 8090, bridge)
        org.mockito.kotlin
            .verify(keyHandlers)
            .install(stage, loadingView)
        org.mockito.kotlin
            .verify(externalLinkInterceptor)
            .install(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any<() -> DesktopAppBridge?>(),
            )
        assertEquals(1, stateListeners.size)
        assertEquals(1, loadedUrls.size)
        assertEquals(loadingView, loadedUrls[0].first)
        assertEquals("http://test:8090", loadedUrls[0].second)
        assertSame(loadingView, root.center)
        assertEquals(1, webViewFactoryCalls)
        val field = DesktopLauncherApp::class.java.getDeclaredField("appBridge")
        field.isAccessible = true
        assertEquals(bridge, field.get(app))
    }

    @Test
    fun `startInternal routes bootstrap exceptions through the startup error reporter`() {
        val stageSetup = mock<DesktopStageSetup>()
        val backendBootstrap = mock<DesktopBackendBootstrap>()
        whenever(backendBootstrap.start()).thenThrow(RuntimeException("boom"))
        val startupErrorReporter = mock<DesktopStartupErrorReporter>()
        val loadingView = mock<javafx.scene.web.WebView>()

        val app =
            DesktopLauncherApp(
                stageSetup = stageSetup,
                backendBootstrap = backendBootstrap,
                startupErrorReporter = startupErrorReporter,
                loadingHtmlApplier = { _, _ -> Unit },
            )
        val bgCaptured = mutableListOf<Runnable>()
        val bgRunner: (Runnable) -> Unit = { bgCaptured.add(it) }
        app.startInternal(
            stage = mock<javafx.stage.Stage>(),
            loadingView = loadingView,
            root = BorderPane(),
            shutdownRegistrar = { },
            runBackground = bgRunner,
            runOnFxThread = { },
        )
        assertEquals(1, bgCaptured.size)
        bgCaptured[0].run()
        org.mockito.kotlin.verify(startupErrorReporter).report(
            org.mockito.kotlin.any<Exception>(),
            org.mockito.kotlin.eq(loadingView),
        )
    }

    @Test
    fun `startInternal uses the loadingHtmlLoader result verbatim when loading the view`() {
        val appliedHtml = mutableListOf<String>()
        val app =
            DesktopLauncherApp(
                stageSetup = mock<DesktopStageSetup>(),
                loadingHtmlLoader = { "<html>custom-loader</html>" },
                loadingHtmlApplier = { _, html -> appliedHtml.add(html) },
            )
        val loadingView = mock<javafx.scene.web.WebView>()
        app.startInternal(
            stage = mock<javafx.stage.Stage>(),
            loadingView = loadingView,
            root = BorderPane(),
            shutdownRegistrar = { },
            runBackground = { },
            runOnFxThread = { },
        )
        assertEquals(listOf("<html>custom-loader</html>"), appliedHtml)
    }

    @Test
    fun `start delegates to startInternal with the JavaFX wiring`() {
        val stage = mock<javafx.stage.Stage>()
        val stageSetup = mock<DesktopStageSetup>()
        val backendBootstrap = mock<DesktopBackendBootstrap>()
        val webViewConfigurator = mock<WebViewConfigurator>()
        val jsBridgeInjector = mock<DesktopJsBridgeInjector>()
        val popupHandler = mock<DesktopPopupHandler>()
        val keyHandlers = mock<DesktopKeyHandlers>()
        val externalLinkInterceptor = mock<DesktopExternalLinkInterceptor>()
        val startupErrorReporter = mock<DesktopStartupErrorReporter>()
        val shutdownHandler = mock<DesktopShutdownHandler>()
        val webView = mock<javafx.scene.web.WebView>()
        val webViewFactory: () -> javafx.scene.web.WebView = { webView }
        val root = BorderPane()
        val bridge = mock<DesktopAppBridge>()
        whenever(backendBootstrap.start()).thenReturn(9001)
        val appliedHtml = mutableListOf<Pair<javafx.scene.web.WebView, String>>()
        val loadedUrls = mutableListOf<Pair<javafx.scene.web.WebView, String>>()
        val fxRunnables = mutableListOf<Runnable>()
        val bgRunnables = mutableListOf<Runnable>()

        val app =
            DesktopLauncherApp(
                stageSetup = stageSetup,
                backendBootstrap = backendBootstrap,
                webViewConfigurator = webViewConfigurator,
                jsBridgeInjector = jsBridgeInjector,
                popupHandler = popupHandler,
                keyHandlers = keyHandlers,
                externalLinkInterceptor = externalLinkInterceptor,
                startupErrorReporter = startupErrorReporter,
                shutdownHandler = shutdownHandler,
                webViewFactory = webViewFactory,
                borderPaneFactory = { root },
                bridgeFactory = { _, _, _ -> bridge },
                backgroundThreadFactory = { r ->
                    bgRunnables.add(r)
                    Thread(r, "test").apply { isDaemon = true }
                },
                loadingHtmlLoader = { "<html>loading</html>" },
                loadingHtmlApplier = { view, html -> appliedHtml.add(view to html) },
                mainPageUrlBuilder = { port -> "http://test:$port" },
                mainPageLoader = { view, url -> loadedUrls.add(view to url) },
                workerStateListenerInstaller = { _, _ -> Unit },
            )
        app.start(stage)
        org.mockito.kotlin
            .verify(stage)
            .setOnCloseRequest(org.mockito.kotlin.any())
        assertEquals(1, appliedHtml.size)
        assertEquals(webView, appliedHtml[0].first)
        assertEquals(1, bgRunnables.size)
        org.mockito.kotlin
            .verify(stageSetup)
            .setup(stage, "StackLens", "/stacklens-icon.png", root, webView, "/webview-noscroll.css")
    }
}
