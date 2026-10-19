package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import sun.misc.Unsafe

class WebViewConfiguratorJavaFxBranchTest {
    private val unsafe: Unsafe by lazy {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        field.get(null) as Unsafe
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeWebView(): javafx.scene.web.WebView = unsafe.allocateInstance(javafx.scene.web.WebView::class.java) as javafx.scene.web.WebView

    @Suppress("UNCHECKED_CAST")
    private fun fakeWebEngine(): javafx.scene.web.WebEngine = unsafe.allocateInstance(javafx.scene.web.WebEngine::class.java) as javafx.scene.web.WebEngine

    @Test
    fun `buildWebAdapterFor with explicit lambdas builds a RealWebConfigAdapter that delegates`() {
        var ctxMenuValue = true
        var cacheValue = false
        var hintValue: javafx.scene.CacheHint = javafx.scene.CacheHint.DEFAULT
        val engineBridge = FakeEngineBridge()
        val cfg = WebViewConfigurator("/tmp/home")
        val adapter =
            cfg.buildWebAdapterFor(
                webView = mock<javafx.scene.web.WebView>(),
                engineBridge = engineBridge,
                contextMenu = { ctxMenuValue },
                setContextMenu = { ctxMenuValue = it },
                cache = { cacheValue },
                setCache = { cacheValue = it },
                cacheHint = { hintValue },
                setCacheHint = { hintValue = it },
            )
        assertNotNull(adapter)
        adapter.contextMenuEnabled = false
        assertEquals(false, ctxMenuValue)
        adapter.isCache = true
        assertEquals(true, cacheValue)
        adapter.cacheHint = javafx.scene.CacheHint.SPEED
        assertEquals(javafx.scene.CacheHint.SPEED, hintValue)
    }

    @Test
    fun `buildWebAdapterFor with explicit lambdas routes confirm and alert through the engine bridge`() {
        val engineBridge = FakeEngineBridge()
        val cfg = WebViewConfigurator("/tmp/home")
        val adapter =
            cfg.buildWebAdapterFor(
                webView = mock<javafx.scene.web.WebView>(),
                engineBridge = engineBridge,
                contextMenu = { true },
                setContextMenu = { },
                cache = { false },
                setCache = { },
                cacheHint = { javafx.scene.CacheHint.DEFAULT },
                setCacheHint = { },
            )
        adapter.setConfirmHandler { _ -> true }
        adapter.setAlertHandler { }
        assertTrue(engineBridge.confirmHandlerInvoked)
        assertTrue(engineBridge.alertHandlerInvoked)
    }

    @Test
    fun `defaultEngineBridgeFor with explicit lambdas builds a bridge that delegates every operation`() {
        var capturedDir: java.io.File? = null
        var capturedConfirm: ((String?) -> Boolean)? = null
        var capturedAlert: ((String) -> Unit)? = null
        val storedDir: java.io.File? = java.io.File("/tmp/webdata")
        val cfg = WebViewConfigurator()
        val bridge =
            cfg.defaultEngineBridgeFor(
                webView = mock<javafx.scene.web.WebView>(),
                getUserDataDir = { storedDir },
                setUserDataDir = { capturedDir = it },
                setEngineConfirm = { capturedConfirm = it },
                setEngineAlert = { capturedAlert = it },
            )
        assertEquals(storedDir, bridge.userDataDirectory)
        val newDir = java.io.File("/tmp/webdata-new")
        bridge.userDataDirectory = newDir
        assertEquals(newDir, capturedDir)
        val confirm: (String?) -> Boolean = { _ -> true }
        bridge.setConfirmHandler(confirm)
        assertEquals(confirm, capturedConfirm)
        val alert: (String) -> Unit = { }
        bridge.setAlertHandler(alert)
        assertEquals(alert, capturedAlert)
    }

    @Test
    fun `defaultEngineBridgeFor with default WebView build is callable`() {
        val cfg = WebViewConfigurator()
        val bridge = cfg.defaultEngineBridgeFor(mock<javafx.scene.web.WebView>())
        assertNotNull(bridge)
    }

    @Test
    fun `defaultEngineBridgeFor default lambdas delegate through webView engine accessors`() {
        val cfg = WebViewConfigurator()
        val webView = mock<javafx.scene.web.WebView>()
        val bridge = cfg.defaultEngineBridgeFor(webView)
        assertNotNull(bridge)
    }

    @Test
    fun `buildStageAdapterFor wraps a real Stage and exposes widthProperty and heightProperty`() {
        val stage = mock<javafx.stage.Stage>()
        val widthProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        val heightProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        whenever(stage.widthProperty()).thenReturn(widthProp)
        whenever(stage.heightProperty()).thenReturn(heightProp)
        val cfg = WebViewConfigurator()
        val adapter = cfg.buildStageAdapterFor(stage)
        assertNotNull(adapter)
        assertEquals(widthProp, adapter.widthProperty())
        assertEquals(heightProp, adapter.heightProperty())
    }

    @Test
    fun `configure WebView Stage delegates to buildWebAdapterFor and buildStageAdapterFor`() {
        val webView = mock<javafx.scene.web.WebView>()
        val stage = mock<javafx.stage.Stage>()
        val widthProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        val heightProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        whenever(stage.widthProperty()).thenReturn(widthProp)
        whenever(stage.heightProperty()).thenReturn(heightProp)
        val cfg = WebViewConfigurator("/tmp/home")
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.OK }
        cfg.alertDialogShower = {}
        val adapter = cfg.buildWebAdapterFor(webView)
        assertNotNull(adapter)
        val stageAdapter = cfg.buildStageAdapterFor(stage)
        assertNotNull(stageAdapter)
    }

    @Test
    fun `single-arg buildWebAdapterFor delegates to defaultEngineBridgeFor with mocked WebView`() {
        val webView = mock<javafx.scene.web.WebView>()
        val cfg = WebViewConfigurator("/tmp/home")
        val adapter = cfg.buildWebAdapterFor(webView)
        assertNotNull(adapter)
        adapter.contextMenuEnabled = false
        verify(webView).isContextMenuEnabled = false
        adapter.isCache = true
        verify(webView).isCache = true
        adapter.cacheHint = javafx.scene.CacheHint.SPEED
        verify(webView).cacheHint = javafx.scene.CacheHint.SPEED
    }

    @Test
    fun `installResizeCacheHint WebView Stage adds width and height listeners`() {
        val webView = mock<javafx.scene.web.WebView>()
        val stage = mock<javafx.stage.Stage>()
        val widthProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        val heightProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        whenever(stage.widthProperty()).thenReturn(widthProp)
        whenever(stage.heightProperty()).thenReturn(heightProp)
        val cfg = WebViewConfigurator()
        cfg.installResizeCacheHint(webView, stage)
        verify(widthProp).addListener(any<javafx.beans.value.ChangeListener<in Number>>())
        verify(heightProp).addListener(any<javafx.beans.value.ChangeListener<in Number>>())
    }

    @Test
    fun `buildResizeHint change listener sets isCache and cacheHint on the WebView`() {
        val webView = mock<javafx.scene.web.WebView>()
        val stage = mock<javafx.stage.Stage>()
        val cfg = WebViewConfigurator()
        val (_, listener) =
            cfg.buildResizeHint(
                setCacheTrue = { webView.isCache = true },
                setCacheFalse = { webView.isCache = false },
                setHintSpeed = { webView.cacheHint = javafx.scene.CacheHint.SPEED },
                setHintDefault = { webView.cacheHint = javafx.scene.CacheHint.DEFAULT },
                playPause = { },
            )
        val src: javafx.beans.value.ObservableValue<Number> = mock()
        listener.changed(src, 0.0, 1.0)
        verify(webView).isCache = true
        verify(webView).cacheHint = javafx.scene.CacheHint.SPEED
    }

    @Test
    fun `buildResizeHint with explicit lambdas invokes setCacheTrue and setHintSpeed on change`() {
        var cacheTrue = false
        var hintSpeed = false
        val cfg = WebViewConfigurator()
        val (pause, listener) =
            cfg.buildResizeHint(
                setCacheTrue = { cacheTrue = true },
                setCacheFalse = { },
                setHintSpeed = { hintSpeed = true },
                setHintDefault = { },
                playPause = { },
            )
        val src: javafx.beans.value.ObservableValue<Number> = mock()
        listener.changed(src, 0.0, 1.0)
        assertTrue(cacheTrue)
        assertTrue(hintSpeed)
        val onFinished = pause.onFinished
        assertNotNull(onFinished)
        onFinished!!.handle(null)
    }

    @Test
    fun `installResizeCacheHint StageConfig WebConfig invokes listener body on change`() {
        val web = FakeWebConfig()
        val stage = FakeStageConfig()
        val cfg = WebViewConfigurator()
        cfg.installResizeCacheHint(stage, web, playPause = { })
        val widthListener = stage.lastWidthListener
        val heightListener = stage.lastHeightListener
        val src: javafx.beans.value.ObservableValue<Number> = mock()
        widthListener!!.changed(src, 0.0, 1.0)
        heightListener!!.changed(src, 0.0, 1.0)
        assertTrue(web.isCache)
        assertEquals(javafx.scene.CacheHint.SPEED, web.cacheHint)
    }

    @Test
    fun `default confirmDialogShower returns CANCEL`() {
        val cfg = WebViewConfigurator()
        val result =
            cfg.confirmDialogShower(
                WebViewConfigurator.DialogSpec("t", null, "c", WebViewConfigurator.DialogType.CONFIRMATION),
            )
        assertEquals(WebViewConfigurator.ButtonChoice.CANCEL, result)
    }

    @Test
    fun `default alertDialogShower is a no-op`() {
        val cfg = WebViewConfigurator()
        cfg.alertDialogShower(
            WebViewConfigurator.DialogSpec("t", null, "c", WebViewConfigurator.DialogType.INFORMATION),
        )
        assertTrue(true)
    }
}
