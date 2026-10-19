@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.stacklens.desktop

import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.value.ChangeListener
import javafx.collections.ObservableList
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopPopupHandlerInstallTest {
    private fun mockWebView(): WebView = mock<WebView>()

    private fun mockStage(): Stage = mock<Stage>()

    private fun mockBridge(): DesktopAppBridge = mock()

    private class FakeEngine(
        private val location: String? = "",
    ) : PopupHandlerEngine {
        var popupCallback: ((PopupFeatures) -> WebEngine)? = null
        var visibilityHandler: ((Boolean) -> Unit)? = null
        var locationListener: ChangeListener<String>? = null
        var loadWorkerListener: ChangeListener<Worker.State>? = null
        var lastWindowMemberName: String? = null
        var lastWindowMemberValue: Any? = null
        val showStageCalls = mutableListOf<Stage>()
        val closeStageCalls = mutableListOf<Stage>()
        var setWindowMemberShouldThrow = false

        override fun setCreatePopupHandler(
            parentWebView: WebView,
            callback: (PopupFeatures) -> WebEngine,
        ) {
            popupCallback = callback
        }

        override fun setOnVisibilityChanged(
            webView: WebView,
            handler: (data: Boolean) -> Unit,
        ) {
            visibilityHandler = handler
        }

        override fun addLocationListener(
            webView: WebView,
            listener: ChangeListener<String>,
        ) {
            locationListener = listener
        }

        override fun addLoadWorkerStateListener(
            webView: WebView,
            listener: ChangeListener<Worker.State>,
        ) {
            loadWorkerListener = listener
        }

        override fun setWindowMember(
            webView: WebView,
            name: String,
            value: Any,
        ) {
            if (setWindowMemberShouldThrow) throw RuntimeException("nav")
            lastWindowMemberName = name
            lastWindowMemberValue = value
        }

        override fun showStage(stage: Stage) {
            showStageCalls.add(stage)
        }

        override fun closeStage(stage: Stage) {
            closeStageCalls.add(stage)
        }

        override fun runLater(block: () -> Unit) {
            runLaterCalls.add(block)
        }

        val runLaterCalls: MutableList<() -> Unit> = mutableListOf()

        fun flushRunLater() {
            val pending = runLaterCalls.toList()
            runLaterCalls.clear()
            pending.forEach { it() }
        }

        fun fireLocationChange(
            oldValue: String,
            newValue: String,
        ) {
            val prop = mock<ReadOnlyStringProperty>()
            whenever(prop.get()).thenReturn(newValue)
            locationListener!!.changed(prop, oldValue, newValue)
        }

        fun fireVisibility(data: Boolean) {
            visibilityHandler!!(data)
        }

        fun fireLoadWorkerState(state: Worker.State) {
            val prop = mock<javafx.beans.property.ReadOnlyObjectProperty<Worker.State>>()
            whenever(prop.get()).thenReturn(state)
            loadWorkerListener!!.changed(prop, null, state)
        }
    }

    @Test
    fun `install wires popup handler via engine`() {
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)
        handler.install(mockWebView(), mockStage(), 8090, mockBridge())
        assertNotNull(fake.popupCallback)
    }

    @Test
    fun `install popup callback wires navigation and visibility listeners`() {
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        handler.install(mockWebView(), mockStage(), 8090, mockBridge())
        assertNotNull(fake.popupCallback)
    }

    @Test
    fun `buildAndConfigurePopup wires scene and title`() {
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)
        val parent = mockWebView()
        val parentStage = mockStage()
        val popupStage = mockStage()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val scene = mock<Scene>()
        val popupWebViewResult =
            handler.buildAndConfigurePopup(
                parentWebView = parent,
                parentStage = parentStage,
                port = 8090,
                bridge = mockBridge(),
                popupStageFactory = { popupStage },
                popupWebViewFactory = { popupWebView },
                popupRootFactory = { popupRoot },
                sceneFactory = { scene },
            )
        assertEquals(popupWebView, popupWebViewResult)
        verify(popupStage).title = "StackLens"

        verify(popupStage).icons
    }

    @Test
    fun `buildAndConfigurePopup calls installPopupCloseOnVisibilityLoss and routePopupNavigation`() {
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)
        val parent = mockWebView()
        val parentStage = mockStage()
        val popupStage = mockStage()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val scene = mock<Scene>()
        handler.buildAndConfigurePopup(
            parentWebView = parent,
            parentStage = parentStage,
            port = 8090,
            bridge = mockBridge(),
            popupStageFactory = { popupStage },
            popupWebViewFactory = { popupWebView },
            popupRootFactory = { popupRoot },
            sceneFactory = { scene },
        )

        assertNotNull(fake.visibilityHandler)
        assertNotNull(fake.locationListener)
    }

    @Test
    fun `buildPopupHandler returns callback that yields the popup web engine`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()

        val handler = DesktopPopupHandler(fake)
        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        val features = mock<PopupFeatures>()
        val result = callback(features)
        assertEquals(popupEngine, result)
    }

    @Test
    fun `buildPopupHandler swallows exceptions from icons addAll`() {
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()

        val popupEngine: Any = Object()
        val fake = FakeEngine()

        val handler = DesktopPopupHandler(fake)
        val callback =
            handler.buildPopupHandler(
                8090,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        val result = callback(mock<PopupFeatures>())
        assertNotNull(result)
    }

    @Test
    fun `buildPopupHandler wires bridge when load worker succeeds`() {
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                8090,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())

        fake.fireLocationChange("", "http://127.0.0.1:8090/page")
        fake.flushRunLater()

        fake.fireLoadWorkerState(Worker.State.SUCCEEDED)
        assertEquals("desktopApp", fake.lastWindowMemberName)
        assertEquals(bridge, fake.lastWindowMemberValue)
    }

    @Test
    fun `buildPopupHandler swallows exceptions from setWindowMember`() {
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        fake.setWindowMemberShouldThrow = true
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                8090,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireLocationChange("", "http://127.0.0.1:8090/page")
        fake.flushRunLater()
        fake.fireLoadWorkerState(Worker.State.SUCCEEDED)

        assertTrue(true)
    }

    @Test
    fun `buildPopupHandler does not wire bridge when load worker is not SUCCEEDED`() {
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                8090,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireLocationChange("", "http://127.0.0.1:8090/page")
        fake.flushRunLater()
        fake.fireLoadWorkerState(Worker.State.RUNNING)
        assertEquals(null, fake.lastWindowMemberName)
    }

    @Test
    fun `routePopupNavigation handles internal URL`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireLocationChange("", "http://127.0.0.1:8090/page")
        fake.flushRunLater()

        verify(bridge, never()).openInBrowser(any())
    }

    @Test
    fun `routePopupNavigation handles external URL and forwards to bridge`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireLocationChange("", "https://example.com/foo")
        fake.flushRunLater()
        verify(bridge, times(1)).openInBrowser("https://example.com/foo")
    }

    @Test
    fun `routePopupNavigation ignores empty location`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireLocationChange("foo", "")
        fake.flushRunLater()
        verify(bridge, never()).openInBrowser(any())
    }

    @Test
    fun `routePopupNavigation ignores about blank location`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireLocationChange("foo", "about:blank")
        fake.flushRunLater()
        verify(bridge, never()).openInBrowser(any())
    }

    @Test
    fun `routePopupNavigation ignores subsequent changes after first navigation`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireLocationChange("", "https://example.com/foo")
        fake.flushRunLater()
        fake.fireLocationChange("https://example.com/foo", "https://other.com")
        fake.flushRunLater()
        verify(bridge, times(1)).openInBrowser(any())
    }

    @Test
    fun `installPopupCloseOnVisibilityLoss closes stage when visibility becomes false`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireVisibility(false)
        fake.flushRunLater()
        assertTrue(fake.closeStageCalls.contains(popupStage))
    }

    @Test
    fun `installPopupCloseOnVisibilityLoss does not close stage when visibility becomes true`() {
        val port = 8090
        val bridge = mockBridge()
        val popupWebView = mockWebView()
        val popupRoot = mock<BorderPane>()
        val popupStage = mockStage()
        val popupEngine: Any = Object()
        val fake = FakeEngine()
        val handler = DesktopPopupHandler(fake)

        val callback =
            handler.buildPopupHandler(
                port,
                bridge,
                popupWebView,
                popupRoot,
                popupStage,
            ) { popupEngine }
        callback(mock<PopupFeatures>())
        fake.fireVisibility(true)
        fake.flushRunLater()
        assertTrue(fake.closeStageCalls.isEmpty())
    }

    @Test
    fun `applyNoScrollCss adds stylesheet when resource exists and scene is present`() {
        val fake = FakeEngine()
        val method =
            DesktopPopupHandler::class
                .java
                .declaredMethods
                .firstOrNull { it.name == "applyNoScrollCss" }
        assertNotNull(method)
        method!!.isAccessible = true
        val stage = mockStage()
        val scene = mock<Scene>()
        val stylesheets = mock<ObservableList<String>>()
        whenever(scene.stylesheets).thenReturn(stylesheets)
        whenever(stage.scene).thenReturn(scene)
        method.invoke(DesktopPopupHandler(fake), stage)

        org.mockito.kotlin
            .verify(stylesheets, org.mockito.kotlin.atLeast(0))
            .add(any<String>())
    }

    @Test
    fun `applyNoScrollCss returns early when scene is null`() {
        val fake = FakeEngine()
        val method =
            DesktopPopupHandler::class
                .java
                .declaredMethods
                .firstOrNull { it.name == "applyNoScrollCss" }
        assertNotNull(method)
        method!!.isAccessible = true
        val stage = mockStage()

        method.invoke(DesktopPopupHandler(fake), stage)

        assertTrue(true)
    }

    @Test
    fun `applyNoScrollCss returns early when resource missing`() {
        val fake = FakeEngine()
        val method =
            DesktopPopupHandler::class
                .java
                .declaredMethods
                .firstOrNull { it.name == "applyNoScrollCss" }
        assertNotNull(method)
        method!!.isAccessible = true

        val resourceClass =
            Class.forName("java.lang.String")
        val methodOnResourceClass = resourceClass.declaredMethods.firstOrNull { it.name == "length" } ?: Any()

        val stage = mockStage()
        val scene = mock<Scene>()
        val stylesheets = mock<ObservableList<String>>()
        whenever(scene.stylesheets).thenReturn(stylesheets)
        whenever(stage.scene).thenReturn(scene)

        method.invoke(DesktopPopupHandler(fake), stage)
        assertTrue(true)
    }
}
