package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class WebViewConfiguratorAdaptersTest {
    @Test
    fun `buildResizeHint creates a pause transition and a resize listener`() {
        val cfg = WebViewConfigurator()
        val webView = mock<javafx.scene.web.WebView>()
        val stage = mock<javafx.stage.Stage>()
        val (pause, listener) = cfg.buildResizeHint(webView, stage)
        assertNotNull(pause)
        assertNotNull(listener)
    }

    @Test
    fun `configureWeb invokes installResizeCacheHint after wiring web and stage`() {
        val cfg = WebViewConfigurator("/tmp/home")
        val web = FakeWebConfig()
        val stage = FakeStageConfig()
        cfg.confirmDialogShower = { WebViewConfigurator.ButtonChoice.OK }
        cfg.alertDialogShower = {}
        cfg.configureWeb(web, stage)
        assertEquals(1, stage.widthListeners)
        assertEquals(1, stage.heightListeners)
    }
}
