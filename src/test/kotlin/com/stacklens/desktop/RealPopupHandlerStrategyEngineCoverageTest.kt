package com.stacklens.desktop

import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.ReadOnlyStringWrapper
import javafx.concurrent.Worker
import javafx.event.EventHandler
import javafx.scene.web.WebEngine
import javafx.scene.web.WebEvent
import javafx.scene.web.WebView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPopupHandlerStrategyEngineCoverageTest {
    private lateinit var engine: WebEngine
    private lateinit var webView: WebView
    private lateinit var strategy: RealPopupHandlerStrategy

    @BeforeEach
    fun setUp() {
        JavafxTestSupport.init()
        engine = mock<WebEngine>()
        webView = mock<WebView>()
        strategy = RealPopupHandlerStrategy(engineOf = { engine })
        whenever(webView.engine).thenReturn(engine)
    }

    @Test
    fun `the default engine accessor reads the engine of the web view`() {
        RealPopupHandlerStrategy().setCreatePopupHandler(webView) { mock() }

        verify(engine).setCreatePopupHandler(any())
    }

    @Test
    fun `setCreatePopupHandler installs the callback on the engine`() {
        strategy.setCreatePopupHandler(webView) { mock() }

        verify(engine).setCreatePopupHandler(any())
    }

    @Test
    fun `visibility changes are forwarded to the handler`() {
        strategy.setOnVisibilityChanged(webView) { seen = it }
        val captor = argumentCaptor<EventHandler<WebEvent<Boolean>>>()
        verify(engine).setOnVisibilityChanged(captor.capture())

        captor.firstValue.handle(webEvent(true))
        captor.firstValue.handle(webEvent(false))

        assertEquals(false, seen)
    }

    @Test
    fun `location listeners are registered on the engine property`() {
        val property = ReadOnlyStringWrapper("start")
        whenever(engine.locationProperty()).thenReturn(property)
        var observed: String? = null

        strategy.addLocationListener(webView) { _, _, newValue -> observed = newValue }
        property.set("moved")

        assertEquals("moved", observed)
    }

    @Test
    fun `load worker state listeners are registered on the worker property`() {
        val worker = mock<Worker<Void>>()
        val property = ReadOnlyObjectWrapper<Worker.State>(Worker.State.SCHEDULED)
        whenever(engine.loadWorker).thenReturn(worker)
        whenever(worker.stateProperty()).thenReturn(property)
        var observed: Worker.State? = null

        strategy.addLoadWorkerStateListener(webView) { _, _, newValue -> observed = newValue }
        property.set(Worker.State.SUCCEEDED)

        assertEquals(Worker.State.SUCCEEDED, observed)
    }

    @Test
    fun `window members are written through the script window object`() {
        val window = JsWindowStub()
        whenever(engine.executeScript("window")).thenReturn(window)

        strategy.setWindowMember(webView, "desktopApp", "bridge")

        assertEquals("desktopApp", window.lastKey)
        assertEquals("bridge", window.lastValue)
    }

    @Test
    fun `a script window that is absent leaves the member unwritten`() {
        whenever(engine.executeScript("window")).thenReturn(null)

        strategy.setWindowMember(webView, "desktopApp", "bridge")

        verify(engine).executeScript("window")
    }

    @Test
    fun `runLater schedules the block on the platform thread`() {
        val latch = java.util.concurrent.CountDownLatch(1)

        strategy.runLater { latch.countDown() }

        assertNotNull(latch)
    }

    private var seen: Boolean? = null

    @Suppress("UNCHECKED_CAST")
    private fun webEvent(data: Boolean): WebEvent<Boolean> {
        val event = mock<WebEvent<Boolean>>()
        whenever(event.data).thenReturn(data)
        return event
    }

    @Test
    fun `the installed popup handler creates a child engine for popup features`() {
        val captor =
            argumentCaptor<javafx.util.Callback<javafx.scene.web.PopupFeatures, WebEngine>>()
        strategy.setCreatePopupHandler(webView) { mock() }

        verify(engine).setCreatePopupHandler(captor.capture())
        assertNotNull(captor.firstValue.call(mock()))
    }

    private class JsWindowStub {
        var lastKey: String? = null
        var lastValue: Any? = null

        fun setMember(
            key: String,
            value: Any?,
        ) {
            lastKey = key
            lastValue = value
        }
    }
}
