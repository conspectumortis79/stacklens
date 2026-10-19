package com.stacklens.desktop

import javafx.scene.CacheHint
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WebViewConfiguratorDefaultsBranchTest {
    @Test
    fun `the web adapter reads cache state from the web view itself`() {
        val webView = mock<WebView>()
        whenever(webView.isCache).thenReturn(true)
        whenever(webView.cacheHint).thenReturn(CacheHint.QUALITY)

        val adapter = WebViewConfigurator().buildWebAdapterFor(webView)

        assertEquals(true, adapter.isCache)
        assertEquals(CacheHint.QUALITY, adapter.cacheHint)
    }

    @Test
    fun `the stage adapter reads both size properties from the stage`() {
        val stage = mock<javafx.stage.Stage>()
        whenever(stage.widthProperty()).thenReturn(mock())
        whenever(stage.heightProperty()).thenReturn(mock())

        val adapter = WebViewConfigurator().buildStageAdapterFor(stage)

        assertEquals(stage.widthProperty(), adapter.widthProperty())
        assertEquals(stage.heightProperty(), adapter.heightProperty())
    }
}
