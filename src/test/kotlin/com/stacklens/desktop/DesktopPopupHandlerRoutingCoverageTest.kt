package com.stacklens.desktop

import javafx.scene.layout.BorderPane
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class DesktopPopupHandlerRoutingCoverageTest {
    private val engine = CapturingPopupEngine()
    private val handler = DesktopPopupHandler(engine)
    private val popupStage = mock<Stage>()
    private val popupWebView = mock<WebView>()
    private val bridge = mock<DesktopAppBridge>()

    private val popupCallback: (PopupFeatures) -> Any? =
        handler
            .buildPopupHandler(
                port = 8080,
                bridge = bridge,
                popupWebView = popupWebView,
                popupRoot = mock<BorderPane>(),
                popupStage = popupStage,
                webEngineFactory = { null },
            ).also { it.invoke(mock<PopupFeatures>()) }

    private fun navigate(location: String?) {
        engine.locationListeners.last().changed(null, null, location)
    }

    private fun visibilityChangesTo(visible: Boolean) {
        engine.visibilityHandlers.last().invoke(visible)
    }

    private fun popupFinishedLoading(state: javafx.concurrent.Worker.State) {
        engine.loadWorkerListeners.last().changed(null, null, state)
    }

    @Test
    fun `the popup handler returns the engine built for the popup`() {
        assertEquals(null, popupCallback.invoke(mock<PopupFeatures>()))
    }

    @Test
    fun `a null or blank location never reaches the navigation decision`() {
        navigate(null)
        navigate("")

        assertTrue(engine.showStageCalls.isEmpty())
        verify(bridge, never()).openInBrowser(any())
    }

    @Test
    fun `about blank is ignored while the popup is still closed`() {
        navigate("about:blank")

        assertTrue(engine.showStageCalls.isEmpty())
        verify(bridge, never()).openInBrowser(any())
    }

    @Test
    fun `an internal location shows the popup stage`() {
        navigate("http://127.0.0.1:8080/dashboard")

        assertEquals(listOf(popupStage), engine.showStageCalls)
        verify(bridge, never()).openInBrowser(any())
    }

    @Test
    fun `a localhost location is treated as internal too`() {
        navigate("http://localhost:8080/dashboard")

        assertEquals(listOf(popupStage), engine.showStageCalls)
    }

    @Test
    fun `a location on another port is external and only the first one is processed`() {
        navigate("http://127.0.0.1:9999/dashboard")
        navigate("http://127.0.0.1:8080/second")

        assertEquals(1, engine.closeStageCalls.size)
        assertEquals(0, engine.showStageCalls.size)
    }

    @Test
    fun `an external location closes the popup and forwards it to the browser`() {
        navigate("https://example.com/spec")

        assertEquals(listOf(popupStage), engine.closeStageCalls)
        verify(bridge).openInBrowser("https://example.com/spec")
    }

    @Test
    fun `an external location is still forwarded when closing the stage fails`() {
        engine.closeThrows = true

        navigate("https://example.com/spec")

        verify(bridge).openInBrowser("https://example.com/spec")
    }

    @Test
    fun `losing the visibility closes the popup`() {
        visibilityChangesTo(false)

        assertEquals(listOf(popupStage), engine.closeStageCalls)
    }

    @Test
    fun `a visible popup stays open`() {
        visibilityChangesTo(true)

        assertTrue(engine.closeStageCalls.isEmpty())
    }

    @Test
    fun `losing the visibility survives a failing stage close`() {
        engine.closeThrows = true

        visibilityChangesTo(false)
    }

    @Test
    fun `the bridge is injected once the popup finished loading`() {
        navigate("http://127.0.0.1:8080/dashboard")

        popupFinishedLoading(javafx.concurrent.Worker.State.SUCCEEDED)

        assertEquals(listOf("desktopApp"), engine.windowMemberNames)
        assertEquals(listOf<Any>(bridge), engine.windowMemberValues)
    }

    @Test
    fun `a failed bridge injection is ignored`() {
        engine.setWindowMemberThrows = true
        navigate("http://127.0.0.1:8080/dashboard")

        popupFinishedLoading(javafx.concurrent.Worker.State.SUCCEEDED)
    }

    @Test
    fun `a running load state does not inject the bridge`() {
        navigate("http://127.0.0.1:8080/dashboard")

        popupFinishedLoading(javafx.concurrent.Worker.State.RUNNING)

        assertTrue(engine.windowMemberNames.isEmpty())
    }
}
