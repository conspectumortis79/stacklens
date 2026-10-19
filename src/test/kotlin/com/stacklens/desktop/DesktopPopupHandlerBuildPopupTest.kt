package com.stacklens.desktop

import com.stacklens.testsupport.FakeBackendLifecycle
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopPopupHandlerBuildPopupTest {
    @Test
    fun `buildAndConfigurePopup wires the popup with the parent webview properties`() {
        val engine = RecordingPopupHandlerEngine()
        val handler = DesktopPopupHandler(engine)
        val parentWebView = mock<WebView>()
        val parentStage = mock<Stage>()
        val parentIcons: javafx.collections.ObservableList<javafx.scene.image.Image> = mock()
        whenever(parentStage.icons).thenReturn(parentIcons)
        val popupStage = mock<Stage>()
        val popupIcons: javafx.collections.ObservableList<javafx.scene.image.Image> = mock()
        whenever(popupStage.icons).thenReturn(popupIcons)
        val popupWebView = mock<WebView>()
        val popupRoot = mock<javafx.scene.layout.BorderPane>()
        val popupScene = mock<javafx.scene.Scene>()
        val bridge = DesktopAppBridge(popupStage, 8080, FakeBackendLifecycle())
        val out =
            handler.buildAndConfigurePopup(
                parentWebView = parentWebView,
                parentStage = parentStage,
                port = 8080,
                bridge = bridge,
                popupStageFactory = { popupStage },
                popupWebViewFactory = { popupWebView },
                popupRootFactory = { popupRoot },
                sceneFactory = { popupScene },
            )

        verify(popupWebView).isContextMenuEnabled = false

        verify(popupStage).scene = popupScene
        verify(popupStage).title = "StackLens"

        verify(popupIcons).addAll(parentIcons)
        assertNotNull(out)

        assertEquals(1, engine.visibilityChangedCalls.size)
        assertEquals(1, engine.locationListenerCalls.size)
    }

    @Test
    fun `buildAndConfigurePopup swallows exception when copying icons fails`() {
        val engine = RecordingPopupHandlerEngine()
        val handler = DesktopPopupHandler(engine)
        val parentWebView = mock<WebView>()
        val parentStage = mock<Stage>()
        val parentIcons: javafx.collections.ObservableList<javafx.scene.image.Image> = mock()
        whenever(parentStage.icons).thenReturn(parentIcons)
        val popupStage = mock<Stage>()
        val popupIcons: javafx.collections.ObservableList<javafx.scene.image.Image> = mock()
        whenever(popupStage.icons).thenReturn(popupIcons)

        doThrow(RuntimeException("nope"))
            .whenever(popupIcons)
            .addAll(any<javafx.collections.ObservableList<javafx.scene.image.Image>>())
        val popupWebView = mock<WebView>()
        val popupRoot = mock<javafx.scene.layout.BorderPane>()
        val popupScene = mock<javafx.scene.Scene>()
        val bridge = DesktopAppBridge(popupStage, 8080, FakeBackendLifecycle())

        handler.buildAndConfigurePopup(
            parentWebView = parentWebView,
            parentStage = parentStage,
            port = 8080,
            bridge = bridge,
            popupStageFactory = { popupStage },
            popupWebViewFactory = { popupWebView },
            popupRootFactory = { popupRoot },
            sceneFactory = { popupScene },
        )
        assertNotNull(popupWebView)
    }

    @Test
    fun `buildPopupHandler invokes the engine factory and returns a callable`() {
        val engine = RecordingPopupHandlerEngine()
        val handler = DesktopPopupHandler(engine)
        val popupWebView = mock<WebView>()
        val popupRoot = mock<javafx.scene.layout.BorderPane>()
        val popupStage = mock<Stage>()
        val popupIcons: javafx.collections.ObservableList<javafx.scene.image.Image> = mock()
        whenever(popupStage.icons).thenReturn(popupIcons)
        val bridge = DesktopAppBridge(popupStage, 8080, FakeBackendLifecycle())
        val engineRef = "engine-stub"
        val callback =
            handler.buildPopupHandler(
                port = 8080,
                bridge = bridge,
                popupWebView = popupWebView,
                popupRoot = popupRoot,
                popupStage = popupStage,
                webEngineFactory = { engineRef },
            )
        val out = callback.invoke(mock<PopupFeatures>())
        assertEquals(engineRef, out)

        assertEquals(1, engine.visibilityChangedCalls.size)
        assertEquals(1, engine.locationListenerCalls.size)
    }
}

internal class RecordingPopupHandlerEngine : PopupHandlerEngine {
    data class VisibilityCall(
        val webView: WebView,
        val handler: (Boolean) -> Unit,
    )

    data class LocationCall(
        val webView: WebView,
        val listener: ChangeListener<String>,
    )

    val visibilityChangedCalls: MutableList<VisibilityCall> = mutableListOf()
    val locationListenerCalls: MutableList<LocationCall> = mutableListOf()
    val loadWorkerCalls: MutableList<Pair<WebView, ChangeListener<Worker.State>>> = mutableListOf()
    val showStageCalls: MutableList<Stage> = mutableListOf()
    val closeStageCalls: MutableList<Stage> = mutableListOf()

    override fun setCreatePopupHandler(
        parentWebView: WebView,
        callback: (PopupFeatures) -> WebEngine,
    ) {
    }

    override fun setOnVisibilityChanged(
        webView: WebView,
        handler: (data: Boolean) -> Unit,
    ) {
        visibilityChangedCalls += VisibilityCall(webView, handler)
    }

    override fun addLocationListener(
        webView: WebView,
        listener: ChangeListener<String>,
    ) {
        locationListenerCalls += LocationCall(webView, listener)
    }

    override fun addLoadWorkerStateListener(
        webView: WebView,
        listener: ChangeListener<Worker.State>,
    ) {
        loadWorkerCalls += webView to listener
    }

    override fun setWindowMember(
        webView: WebView,
        name: String,
        value: Any,
    ) {
    }

    override fun showStage(stage: Stage) {
        showStageCalls += stage
    }

    override fun closeStage(stage: Stage) {
        closeStageCalls += stage
    }

    override fun runLater(block: () -> Unit) {
        block()
    }
}
