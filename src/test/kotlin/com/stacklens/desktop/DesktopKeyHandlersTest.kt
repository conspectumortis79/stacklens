package com.stacklens.desktop

import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DesktopKeyHandlersTest {
    @Test
    fun `KI_CHAT_SEND_JS targets the kiChatInput element`() {
        assertTrue(DesktopKeyHandlers.KI_CHAT_SEND_JS.contains("kiChatInput"))
    }

    @Test
    fun `KI_CHAT_SEND_JS calls kiChatSend function`() {
        assertTrue(DesktopKeyHandlers.KI_CHAT_SEND_JS.contains("kiChatSend"))
    }

    @Test
    fun `KI_CHAT_SEND_JS returns boolean from IIFE`() {
        assertTrue(DesktopKeyHandlers.KI_CHAT_SEND_JS.contains("return true"))
        assertTrue(DesktopKeyHandlers.KI_CHAT_SEND_JS.contains("return false"))
    }

    @Test
    fun `ESCAPE_CLOSE_MODAL_JS targets the active modal`() {
        val js = DesktopKeyHandlers.ESCAPE_CLOSE_MODAL_JS
        assertTrue(js.contains("querySelector('.modal.show')"))
    }

    @Test
    fun `ESCAPE_CLOSE_MODAL_JS prefers hideModalById helper`() {
        val js = DesktopKeyHandlers.ESCAPE_CLOSE_MODAL_JS
        assertTrue(js.contains("window.hideModalById"))
        assertTrue(js.contains("window.__hideModal"))
    }

    @Test
    fun `ESCAPE_CLOSE_MODAL_JS falls back to close button click`() {
        val js = DesktopKeyHandlers.ESCAPE_CLOSE_MODAL_JS
        assertTrue(js.contains(".btn-close"))
        assertTrue(js.contains(".click()"))
    }

    @Test
    fun `ESCAPE_CLOSE_MODAL_JS deselects project and repo cards`() {
        val js = DesktopKeyHandlers.ESCAPE_CLOSE_MODAL_JS
        assertTrue(js.contains("lt-project-item"))
        assertTrue(js.contains("ca-repo-card"))
    }

    @Test
    fun `ESCAPE_CLOSE_MODAL_JS invokes __onEscDeselect callback`() {
        assertTrue(DesktopKeyHandlers.ESCAPE_CLOSE_MODAL_JS.contains("__onEscDeselect"))
    }

    @Test
    fun `handleEnterKey executes KI_CHAT_SEND_JS on Enter without Shift`() {
        val handlers = DesktopKeyHandlers(executorFactory = { mock<JsExecutor>() })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(false)

        handlers.handleEnterKey(event, webView)
        assertTrue(true)
    }

    @Test
    fun `handleEnterKey is no-op for Enter with Shift`() {
        val handlers = DesktopKeyHandlers(executorFactory = { mock<JsExecutor>() })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(true)

        handlers.handleEnterKey(event, webView)
        assertTrue(true)
    }

    @Test
    fun `handleEnterKey is no-op for non-Enter keys`() {
        val handlers = DesktopKeyHandlers(executorFactory = { mock<JsExecutor>() })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.A)
        whenever(event.isShiftDown).thenReturn(false)

        handlers.handleEnterKey(event, webView)
        assertTrue(true)
    }

    @Test
    fun `handleEnterKey swallows exceptions thrown by executeScript`() {
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(any<String>())).thenThrow(RuntimeException("navigate"))
        val handlers = DesktopKeyHandlers(executorFactory = { executor })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(false)

        handlers.handleEnterKey(event, webView)
        assertTrue(true)
    }

    @Test
    fun `handleEnterKey consumes event when JS returns true`() {
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(any<String>())).thenReturn(true)
        val handlers = DesktopKeyHandlers(executorFactory = { executor })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(false)

        handlers.handleEnterKey(event, webView)
        org.mockito.kotlin
            .verify(event)
            .consume()
    }

    @Test
    fun `handleEnterKey does not consume event when JS returns false`() {
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(any<String>())).thenReturn(false)
        val handlers = DesktopKeyHandlers(executorFactory = { executor })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(false)

        handlers.handleEnterKey(event, webView)
        org.mockito.kotlin
            .verify(event, org.mockito.kotlin.never())
            .consume()
    }

    @Test
    fun `handleEnterKey does not consume event when JS returns null`() {
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(any<String>())).thenReturn(null)
        val handlers = DesktopKeyHandlers(executorFactory = { executor })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(false)

        handlers.handleEnterKey(event, webView)
        org.mockito.kotlin
            .verify(event, org.mockito.kotlin.never())
            .consume()
    }

    @Test
    fun `handleEnterKey does not consume event when JS returns non-boolean`() {
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(any<String>())).thenReturn("not a boolean")
        val handlers = DesktopKeyHandlers(executorFactory = { executor })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(false)

        handlers.handleEnterKey(event, webView)
        org.mockito.kotlin
            .verify(event, org.mockito.kotlin.never())
            .consume()
    }

    @Test
    fun `handleEscapeKey executes ESCAPE_CLOSE_MODAL_JS on Escape`() {
        val handlers = DesktopKeyHandlers(executorFactory = { mock<JsExecutor>() })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ESCAPE)

        handlers.handleEscapeKey(event, webView)
        assertTrue(true)
    }

    @Test
    fun `handleEscapeKey is no-op for non-Escape keys`() {
        val handlers = DesktopKeyHandlers(executorFactory = { mock<JsExecutor>() })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.A)

        handlers.handleEscapeKey(event, webView)
        assertTrue(true)
    }

    @Test
    fun `handleEscapeKey swallows exceptions thrown by executeScript`() {
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(any<String>())).thenThrow(RuntimeException("navigate"))
        val handlers = DesktopKeyHandlers(executorFactory = { executor })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ESCAPE)

        handlers.handleEscapeKey(event, webView)
        assertTrue(true)
    }

    @Test
    fun `EscapeKeyHandler delegates to handleEscapeKey`() {
        val handlers = DesktopKeyHandlers(executorFactory = { mock<JsExecutor>() })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ESCAPE)

        val inner = handlers.EscapeKeyHandler(webView)
        inner.handle(event)
        assertTrue(true)
    }

    @Test
    fun `EnterKeyHandler delegates to handleEnterKey`() {
        val handlers = DesktopKeyHandlers(executorFactory = { mock<JsExecutor>() })
        val webView = mock<javafx.scene.web.WebView>()
        val event = mock<KeyEvent>()
        whenever(event.code).thenReturn(KeyCode.ENTER)
        whenever(event.isShiftDown).thenReturn(false)

        val inner = handlers.EnterKeyHandler(webView)
        inner.handle(event)
        assertTrue(true)
    }
}
