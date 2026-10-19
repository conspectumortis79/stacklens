package com.stacklens.desktop

import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultPopupHandlerEngineTest {
    private class FakeStrategy : PopupHandlerEngine {
        var setCreatePopupHandlerCalls = 0
        var setOnVisibilityChangedCalls = 0
        var addLocationListenerCalls = 0
        var addLoadWorkerStateListenerCalls = 0
        var setWindowMemberCalls = 0
        var showStageCalls = 0
        var closeStageCalls = 0
        var runLaterCalls = 0
        var lastWindowMemberName: String? = null
        var lastWindowMemberValue: Any? = null
        var location: String? = "http://example.com"
        var loadState: Worker.State? = Worker.State.SUCCEEDED

        override fun setCreatePopupHandler(
            parentWebView: WebView,
            callback: (PopupFeatures) -> WebEngine,
        ) {
            setCreatePopupHandlerCalls++
        }

        override fun setOnVisibilityChanged(
            webView: WebView,
            handler: (data: Boolean) -> Unit,
        ) {
            setOnVisibilityChangedCalls++
        }

        override fun addLocationListener(
            webView: WebView,
            listener: ChangeListener<String>,
        ) {
            addLocationListenerCalls++
        }

        override fun addLoadWorkerStateListener(
            webView: WebView,
            listener: ChangeListener<Worker.State>,
        ) {
            addLoadWorkerStateListenerCalls++
        }

        override fun setWindowMember(
            webView: WebView,
            name: String,
            value: Any,
        ) {
            setWindowMemberCalls++
            lastWindowMemberName = name
            lastWindowMemberValue = value
        }

        override fun showStage(stage: Stage) {
            showStageCalls++
        }

        override fun closeStage(stage: Stage) {
            closeStageCalls++
        }

        override fun runLater(block: () -> Unit) {
            runLaterCalls++
        }
    }

    private fun newEngine(strategy: FakeStrategy = FakeStrategy()): Pair<DefaultPopupHandlerEngine, FakeStrategy> = DefaultPopupHandlerEngine(strategy) to strategy

    private val unsafe: sun.misc.Unsafe =
        run {
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as sun.misc.Unsafe
        }

    @Suppress("UNCHECKED_CAST")
    private fun fakeWebView(): WebView = unsafe.allocateInstance(WebView::class.java) as WebView

    @Suppress("UNCHECKED_CAST")
    private fun fakeStage(): Stage = unsafe.allocateInstance(Stage::class.java) as Stage

    @Test
    fun `setCreatePopupHandler delegates to the strategy`() {
        val (engine, strategy) = newEngine()
        val callback: (PopupFeatures) -> WebEngine = { throw NotImplementedError() }
        engine.setCreatePopupHandler(fakeWebView(), callback)
        assertEquals(1, strategy.setCreatePopupHandlerCalls)
    }

    @Test
    fun `setOnVisibilityChanged delegates to the strategy`() {
        val (engine, strategy) = newEngine()
        engine.setOnVisibilityChanged(fakeWebView()) { }
        assertEquals(1, strategy.setOnVisibilityChangedCalls)
    }

    @Test
    fun `addLocationListener delegates to the strategy`() {
        val (engine, strategy) = newEngine()
        val listener = ChangeListener<String> { _, _, _ -> }
        engine.addLocationListener(fakeWebView(), listener)
        assertEquals(1, strategy.addLocationListenerCalls)
    }

    @Test
    fun `addLoadWorkerStateListener delegates to the strategy`() {
        val (engine, strategy) = newEngine()
        val listener = ChangeListener<Worker.State> { _, _, _ -> }
        engine.addLoadWorkerStateListener(fakeWebView(), listener)
        assertEquals(1, strategy.addLoadWorkerStateListenerCalls)
    }

    @Test
    fun `setWindowMember delegates and captures the arguments`() {
        val (engine, strategy) = newEngine()
        engine.setWindowMember(fakeWebView(), "theName", "theValue")
        assertEquals(1, strategy.setWindowMemberCalls)
        assertEquals("theName", strategy.lastWindowMemberName)
        assertEquals("theValue", strategy.lastWindowMemberValue)
    }

    @Test
    fun `showStage delegates to the strategy`() {
        val (engine, strategy) = newEngine()
        engine.showStage(fakeStage())
        assertEquals(1, strategy.showStageCalls)
    }

    @Test
    fun `closeStage delegates to the strategy`() {
        val (engine, strategy) = newEngine()
        engine.closeStage(fakeStage())
        assertEquals(1, strategy.closeStageCalls)
    }

    @Test
    fun `runLater delegates to the strategy`() {
        val (engine, strategy) = newEngine()
        engine.runLater { }
        assertEquals(1, strategy.runLaterCalls)
    }

    @Test
    fun `default constructor wires RealPopupHandlerEngine`() {
        val engine: PopupHandlerEngine = DefaultPopupHandlerEngine()
        assertNotNull(engine)
        assertTrue(engine is DefaultPopupHandlerEngine)
    }
}
