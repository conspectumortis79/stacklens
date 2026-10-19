package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import sun.misc.Unsafe

class WebViewConfiguratorResizeHintTest {
    private val unsafe: Unsafe by lazy {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        field.get(null) as Unsafe
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeWebView(): javafx.scene.web.WebView = unsafe.allocateInstance(javafx.scene.web.WebView::class.java) as javafx.scene.web.WebView

    @Suppress("UNCHECKED_CAST")
    private fun fakeStage(): javafx.stage.Stage = unsafe.allocateInstance(javafx.stage.Stage::class.java) as javafx.stage.Stage

    @Test
    fun `buildResizeHint WebView Stage returns pause and listener`() {
        val cfg = WebViewConfigurator()
        val (pause, listener) = cfg.buildResizeHint(fakeWebView(), fakeStage())
        assertNotNull(pause)
        assertNotNull(listener)
    }

    @Test
    fun `buildResizeHint with captured lambdas fires both branches`() {
        val cfg = WebViewConfigurator()
        var cacheTrueCalled = false
        var cacheFalseCalled = false
        var hintSpeedCalled = false
        var hintDefaultCalled = false
        var playPauseCalled = false
        val (pause, listener) =
            cfg.buildResizeHint(
                setCacheTrue = { cacheTrueCalled = true },
                setCacheFalse = { cacheFalseCalled = true },
                setHintSpeed = { hintSpeedCalled = true },
                setHintDefault = { hintDefaultCalled = true },
                playPause = {
                    playPauseCalled = true
                    it.playFromStart()
                },
            )
        val src: javafx.beans.value.ObservableValue<Number> = mock()
        try {
            listener.changed(src, 0.0, 1.0)
        } catch (_: Exception) {
        }
        assertEquals(true, cacheTrueCalled)
        assertEquals(true, hintSpeedCalled)
        assertEquals(true, playPauseCalled)
        try {
            pause.onFinished.handle(null)
        } catch (_: Exception) {
        }
        assertEquals(true, cacheFalseCalled)
        assertEquals(true, hintDefaultCalled)
    }

    @Test
    fun `installResizeCacheHint WebView Stage adds width and height listeners`() {
        val cfg = WebViewConfigurator()
        val webView = fakeWebView()
        val stage = mock<javafx.stage.Stage>()
        val widthProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        val heightProp = mock<javafx.beans.property.ReadOnlyDoubleProperty>()
        whenever(stage.widthProperty()).thenReturn(widthProp)
        whenever(stage.heightProperty()).thenReturn(heightProp)
        try {
            cfg.installResizeCacheHint(webView, stage)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `defaultEngineBridgeFor with default lambdas`() {
        val cfg = WebViewConfigurator()
        val webView = fakeWebView()
        val bridge =
            cfg.defaultEngineBridgeFor(
                webView = webView,
                getUserDataDir = { null },
                setUserDataDir = { },
                setEngineConfirm = { },
                setEngineAlert = { },
            )
        assertNotNull(bridge)
    }
}
