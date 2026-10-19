package com.stacklens.desktop

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DesktopPopupHandlerStylesheetCoverageTest {
    private val engine = CapturingPopupEngine()

    private class Popup(
        val stage: Stage,
        val stylesheets: ObservableList<String>,
    )

    private fun buildPopup(cssResource: (String) -> java.net.URL?): Popup {
        val stage = mock<Stage>()
        val scene = mock<Scene>()
        val stylesheets = FXCollections.observableArrayList<String>()
        whenever(stage.scene).thenReturn(scene)
        whenever(scene.stylesheets).thenReturn(stylesheets)

        DesktopPopupHandler(engine, cssResource = cssResource)
            .buildAndConfigurePopup(
                parentWebView = mock<WebView>(),
                parentStage = mock<Stage>(),
                port = 8080,
                bridge = mock<DesktopAppBridge>(),
                popupStageFactory = { stage },
                popupWebViewFactory = { mock<WebView>() },
                popupRootFactory = { mock<BorderPane>() },
                sceneFactory = { scene },
            )

        return Popup(stage, stylesheets)
    }

    @Test
    fun `the no scroll stylesheet is added to the scene of the popup`() {
        val popup = buildPopup { javaClass.getResource(it) }

        assertEquals(1, popup.stylesheets.size)
        assertTrue(popup.stylesheets.first().endsWith("webview-noscroll.css"))
    }

    @Test
    fun `a missing no scroll stylesheet leaves the scene unstyled`() {
        val popup = buildPopup { null }

        assertTrue(popup.stylesheets.isEmpty())
    }
}
