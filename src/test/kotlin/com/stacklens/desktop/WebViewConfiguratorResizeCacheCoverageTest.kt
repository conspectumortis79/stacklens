package com.stacklens.desktop

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableNumberValue
import javafx.scene.CacheHint
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WebViewConfiguratorResizeCacheCoverageTest {
    private val configurator = WebViewConfigurator()
    private val webView = mock<WebView>()

    @Test
    fun `the resize listener of a web view switches the cache hint to speed`() {
        val (_, onResize) = configurator.buildResizeHint(webView, mock<Stage>())

        onResize.changed(null, 0.0, 1.0)

        verify(webView).isCache = true
        verify(webView).cacheHint = CacheHint.SPEED
    }

    @Test
    fun `the finished pause of a web view resets the cache hint`() {
        val (pause, _) = configurator.buildResizeHint(webView, mock<Stage>())

        pause.onFinished.handle(null)

        verify(webView).isCache = false
        verify(webView).cacheHint = CacheHint.DEFAULT
    }

    @Test
    fun `the installed resize hint registers the same listener on width and height`() {
        val property = mock<ObservableNumberValue>()
        val stage = mock<WebViewConfigurator.StageConfig>()
        val web = mock<WebViewConfigurator.WebConfig>()
        val listeners = argumentCaptor<ChangeListener<Number>>()
        whenever(stage.widthProperty()).thenReturn(property)
        whenever(stage.heightProperty()).thenReturn(property)

        configurator.installResizeCacheHint(stage, web) { pause -> pause.onFinished.handle(null) }

        verify(property, times(2)).addListener(listeners.capture())
        listeners.firstValue.changed(null, 0.0, 1.0)
        verify(web).isCache = true
        verify(web).cacheHint = CacheHint.SPEED
        verify(web).isCache = false
        verify(web).cacheHint = CacheHint.DEFAULT
    }

    @Test
    fun `installing the resize hint on a stage registers listeners for both dimensions`() {
        val property = mock<ReadOnlyDoubleProperty>()
        val stage = mock<Stage>()
        whenever(stage.widthProperty()).thenReturn(property)
        whenever(stage.heightProperty()).thenReturn(property)

        configurator.installResizeCacheHint(webView, stage)

        verify(property, times(2)).addListener(any<ChangeListener<Number>>())
    }

    @Test
    fun `the play pause callback defaults to restarting the transition`() {
        var started = false

        val pair =
            runCatching {
                configurator.buildResizeHint(
                    setCacheTrue = { started = true },
                    setCacheFalse = {},
                    setHintSpeed = {},
                    setHintDefault = {},
                )
            }

        assertTrue(pair.isSuccess)
        runCatching { pair.getOrThrow().second.changed(null, 0.0, 1.0) }
        assertTrue(started)
    }
}
