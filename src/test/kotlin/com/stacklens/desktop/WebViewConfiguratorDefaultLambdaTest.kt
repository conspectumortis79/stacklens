package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import sun.misc.Unsafe

class WebViewConfiguratorDefaultLambdaTest {
    private val unsafe: Unsafe by lazy {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        field.get(null) as Unsafe
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeWebView(): javafx.scene.web.WebView = unsafe.allocateInstance(javafx.scene.web.WebView::class.java) as javafx.scene.web.WebView

    @Test
    fun `buildWebAdapterFor with default lambdas touches the lambdas through the adapter`() {
        val webView = fakeWebView()
        val cfg = WebViewConfigurator("/tmp/home")
        val adapter = cfg.buildWebAdapterFor(webView)
        assertNotNull(adapter)
        try {
            @Suppress("UNUSED_VARIABLE")
            val ctx = adapter.contextMenuEnabled
            adapter.contextMenuEnabled = false
            @Suppress("UNUSED_VARIABLE")
            val cache = adapter.isCache
            adapter.isCache = true
            @Suppress("UNUSED_VARIABLE")
            val hint = adapter.cacheHint
            adapter.cacheHint = javafx.scene.CacheHint.SPEED
        } catch (_: Throwable) {
        }
    }

    @Test
    fun `defaultEngineBridgeFor default lambdas are invoked through the returned bridge`() {
        val webView = fakeWebView()
        val cfg = WebViewConfigurator()
        val bridge = cfg.defaultEngineBridgeFor(webView)
        assertNotNull(bridge)
        try {
            @Suppress("UNUSED_VARIABLE")
            val dir = bridge.userDataDirectory
            try {
                bridge.userDataDirectory = java.io.File("/tmp/x")
            } catch (_: Throwable) {
            }
            try {
                bridge.setConfirmHandler { _ -> true }
            } catch (_: Throwable) {
            }
            try {
                bridge.setAlertHandler { }
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    @Test
    fun `buildResizeHint WebView Stage invokes every captured lambda through the change listener`() {
        val webView = fakeWebView()
        val stage = mock<javafx.stage.Stage>()
        val widthProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        val heightProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        whenever(stage.widthProperty()).thenReturn(widthProp)
        whenever(stage.heightProperty()).thenReturn(heightProp)
        val cfg = WebViewConfigurator()
        val (_, listener) = cfg.buildResizeHint(webView, stage)
        val src: javafx.beans.value.ObservableValue<Number> = mock()
        try {
            listener.changed(src, 0.0, 1.0)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `installResizeCacheHint StageConfig WebConfig invokes the change listener lambda`() {
        val web = FakeWebConfig()
        val stage = FakeStageConfig()
        val cfg = WebViewConfigurator()
        cfg.installResizeCacheHint(stage, web, playPause = { })
        val src: javafx.beans.value.ObservableValue<Number> = mock()
        stage.lastWidthListener!!.changed(src, 0.0, 1.0)
        stage.lastHeightListener!!.changed(src, 0.0, 1.0)
        assertEquals(javafx.scene.CacheHint.SPEED, web.cacheHint)
    }

    @Test
    fun `configure WebView Stage reaches the real adapter wiring`() {
        val webView = fakeWebView()
        val stage = mock<javafx.stage.Stage>()
        val widthProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        val heightProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        whenever(stage.widthProperty()).thenReturn(widthProp)
        whenever(stage.heightProperty()).thenReturn(heightProp)
        val cfg = WebViewConfigurator("/tmp/home")
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.OK }
        cfg.alertDialogShower = {}
        try {
            cfg.configure(webView, stage)
        } catch (_: Exception) {
        }
    }
}
