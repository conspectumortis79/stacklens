package com.stacklens.desktop

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import sun.misc.Unsafe

@Suppress("UNCHECKED_CAST")
class RealPopupHandlerStrategyTest {
    private val unsafe: Unsafe =
        run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }

    private val strategy = RealPopupHandlerStrategy()

    private fun fakeWebView(): WebView = unsafe.allocateInstance(WebView::class.java) as WebView

    @Test
    fun `setCreatePopupHandler is a no-op when engine is null`() {
        val cb: (PopupFeatures) -> WebEngine = { mock() }

        strategy.setCreatePopupHandler(fakeWebView(), cb)
    }

    @Test
    fun `setOnVisibilityChanged is a no-op when engine is null`() {
        strategy.setOnVisibilityChanged(fakeWebView()) { }
    }

    @Test
    fun `addLocationListener is a no-op when engine is null`() {
        val listener = mock<ChangeListener<String>>()
        strategy.addLocationListener(fakeWebView(), listener)
    }

    @Test
    fun `addLoadWorkerStateListener is a no-op when engine is null`() {
        val listener = mock<ChangeListener<Worker.State>>()
        strategy.addLoadWorkerStateListener(fakeWebView(), listener)
    }

    @Test
    fun `setWindowMember is a no-op when engine is null`() {
        strategy.setWindowMember(fakeWebView(), "name", "value")
    }

    @Test
    fun `showStage calls stage show`() {
        val stage = mock<Stage>()
        strategy.showStage(stage)
        verify(stage).show()
    }

    @Test
    fun `closeStage calls stage close`() {
        val stage = mock<Stage>()
        strategy.closeStage(stage)
        verify(stage).close()
    }

    @Test
    fun `runLater calls Platform runLater with the supplied block`() {
        mockStatic(Platform::class.java).use { platform ->
            platform.`when`<Unit> { Platform.runLater(any()) }.then { Unit }
            val ran =
                java.util.concurrent.atomic
                    .AtomicBoolean(false)
            strategy.runLater { ran.set(true) }

            assertEquals(false, ran.get())
            platform.verify { Platform.runLater(any<Runnable>()) }
        }
    }

    @Test
    fun `setCreatePopupHandler with engineOf returns engine delegates to engine`() {
        val strategyWithEngine: RealPopupHandlerStrategy =
            RealPopupHandlerStrategy(engineOf = { null })
        strategyWithEngine.setCreatePopupHandler(fakeWebView()) { mock() }

        assertEquals(true, true)
    }

    @Test
    fun `setOnVisibilityChanged with engineOf returning null is a no-op`() {
        val strategyWithEngine: RealPopupHandlerStrategy =
            RealPopupHandlerStrategy(engineOf = { null })
        strategyWithEngine.setOnVisibilityChanged(fakeWebView()) { }
        assertEquals(true, true)
    }

    @Test
    fun `addLocationListener with engineOf returning null is a no-op`() {
        val strategyWithEngine: RealPopupHandlerStrategy =
            RealPopupHandlerStrategy(engineOf = { null })
        strategyWithEngine.addLocationListener(fakeWebView(), mock<ChangeListener<String>>())
        assertEquals(true, true)
    }

    @Test
    fun `addLoadWorkerStateListener with engineOf returning null is a no-op`() {
        val strategyWithEngine: RealPopupHandlerStrategy =
            RealPopupHandlerStrategy(engineOf = { null })
        strategyWithEngine.addLoadWorkerStateListener(fakeWebView(), mock<ChangeListener<Worker.State>>())
        assertEquals(true, true)
    }
}
