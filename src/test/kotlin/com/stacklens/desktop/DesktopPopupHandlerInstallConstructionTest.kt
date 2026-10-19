package com.stacklens.desktop

import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DesktopPopupHandlerInstallConstructionTest {
    private val engine = CapturingPopupEngine()
    private lateinit var popupEngine: WebEngine
    private val handler = DesktopPopupHandler(engine)

    @BeforeEach
    fun setUp() {
        JavafxTestSupport.init()
        popupEngine = mock<WebEngine>()
    }

    @Test
    fun `the installed callback builds a fully configured popup web view`() {
        val parent = mock<WebView>()
        val parentStage = mock<Stage>()
        val bridge = mock<DesktopAppBridge>()
        handler.install(parent, parentStage, 8090, bridge)
        val callback = engine.popupCallbacks.single()

        Mockito.mockConstruction(Stage::class.java).use { stages ->
            Mockito.mockConstruction(WebView::class.java) { view, _ -> whenever(view.engine).thenReturn(popupEngine) }.use { views ->
                Mockito.mockConstruction(BorderPane::class.java).use { roots ->
                    Mockito.mockConstruction(Scene::class.java).use { scenes ->
                        val engineOfPopup = callback(mock<PopupFeatures>())

                        assertEquals(popupEngine, engineOfPopup)
                        assertEquals(1, stages.constructed().size)
                        assertEquals(1, views.constructed().size)
                        assertEquals(1, roots.constructed().size)
                        assertEquals(1, scenes.constructed().size)
                        assertEquals(1, views.constructed().size)
                    }
                }
            }
        }
    }

    @Test
    fun `the installed callback reuses one popup per engine registration`() {
        val other = CapturingPopupEngine()
        DesktopPopupHandler(other).install(mock<WebView>(), mock<Stage>(), 8090, mock<DesktopAppBridge>())

        assertEquals(1, other.popupCallbacks.size)
    }
}
