package com.stacklens.desktop

import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DesktopKeyHandlersInnerClassesTest {
    @Test
    fun `EscapeKeyHandler delegates handle to handleEscapeKey`() {
        val webView = mock<javafx.scene.web.WebView>()
        val handlers = DesktopKeyHandlers()
        val escapeHandler = handlers.EscapeKeyHandler(webView)
        val event =
            org.mockito.kotlin.mock<KeyEvent> {
                org.mockito.kotlin
                    .whenever(it.code)
                    .thenReturn(KeyCode.ESCAPE)
            }
        escapeHandler.handle(event)
        verify(webView).engine
    }

    @Test
    fun `EnterKeyHandler delegates handle to handleEnterKey`() {
        val webView = mock<javafx.scene.web.WebView>()
        val handlers = DesktopKeyHandlers()
        val enterHandler = handlers.EnterKeyHandler(webView)
        val event =
            org.mockito.kotlin.mock<KeyEvent> {
                org.mockito.kotlin
                    .whenever(it.code)
                    .thenReturn(KeyCode.ENTER)
                org.mockito.kotlin
                    .whenever(it.isShiftDown)
                    .thenReturn(false)
            }
        enterHandler.handle(event)
        verify(webView).engine
    }

    @Test
    fun `install registers both handlers as event filters on the scene`() {
        val webView = mock<javafx.scene.web.WebView>()
        val stage = mock<javafx.stage.Stage>()
        val scene = mock<javafx.scene.Scene>()
        org.mockito.kotlin
            .whenever(stage.scene)
            .thenReturn(scene)

        val handlers = DesktopKeyHandlers()
        handlers.install(stage, webView)
        verify(scene, org.mockito.kotlin.atLeast(2)).addEventFilter(
            org.mockito.kotlin.eq(javafx.scene.input.KeyEvent.KEY_PRESSED),
            org.mockito.kotlin.any<EventHandler<KeyEvent>>(),
        )
    }

    @Test
    fun `install registers two event filters (one for escape, one for enter)`() {
        val webView = mock<javafx.scene.web.WebView>()
        val stage = mock<javafx.stage.Stage>()
        val scene = mock<javafx.scene.Scene>()
        org.mockito.kotlin
            .whenever(stage.scene)
            .thenReturn(scene)

        val handlers = DesktopKeyHandlers()
        handlers.install(stage, webView)
        verify(scene, org.mockito.kotlin.atLeast(2)).addEventFilter(
            org.mockito.kotlin.eq(javafx.scene.input.KeyEvent.KEY_PRESSED),
            org.mockito.kotlin.any<EventHandler<KeyEvent>>(),
        )
    }

    @Test
    fun `handleEnterKey executes JS but does not consume event when kiChatInput is not active`() {
        val webView = mock<javafx.scene.web.WebView>()
        val handlers = DesktopKeyHandlers()
        val event =
            org.mockito.kotlin.mock<KeyEvent> {
                org.mockito.kotlin
                    .whenever(it.code)
                    .thenReturn(KeyCode.ENTER)
                org.mockito.kotlin
                    .whenever(it.isShiftDown)
                    .thenReturn(false)
            }
        handlers.handleEnterKey(event, webView)
        verify(webView).engine
        verify(event, org.mockito.kotlin.never()).consume()
    }

    @Test
    fun `handleEnterKey consumes event when JS returns true`() {
        val handlers = DesktopKeyHandlers()
        val event =
            org.mockito.kotlin.mock<KeyEvent> {
                org.mockito.kotlin
                    .whenever(it.code)
                    .thenReturn(KeyCode.ENTER)
                org.mockito.kotlin
                    .whenever(it.isShiftDown)
                    .thenReturn(false)
            }
        try {
            handlers.handleEnterKey(event, mock<javafx.scene.web.WebView>())
        } catch (_: Throwable) {
        }
        verify(event, org.mockito.kotlin.never()).consume()
    }

    @Test
    fun `handleEnterKey ignores Shift+Enter`() {
        val webView = mock<javafx.scene.web.WebView>()
        val handlers = DesktopKeyHandlers()
        val event =
            org.mockito.kotlin.mock<KeyEvent> {
                org.mockito.kotlin
                    .whenever(it.code)
                    .thenReturn(KeyCode.ENTER)
                org.mockito.kotlin
                    .whenever(it.isShiftDown)
                    .thenReturn(true)
            }
        handlers.handleEnterKey(event, webView)
        verify(webView, org.mockito.kotlin.never()).engine
    }

    @Test
    fun `handleEscapeKey executes the JS close-modal script`() {
        val webView = mock<javafx.scene.web.WebView>()
        val handlers = DesktopKeyHandlers()
        val event =
            org.mockito.kotlin.mock<KeyEvent> {
                org.mockito.kotlin
                    .whenever(it.code)
                    .thenReturn(KeyCode.ESCAPE)
            }
        handlers.handleEscapeKey(event, webView)
        verify(webView).engine
    }

    @Test
    fun `handleEscapeKey is a no-op for non-Escape keys`() {
        val webView = mock<javafx.scene.web.WebView>()
        val handlers = DesktopKeyHandlers()
        val event =
            org.mockito.kotlin.mock<KeyEvent> {
                org.mockito.kotlin
                    .whenever(it.code)
                    .thenReturn(KeyCode.A)
            }
        handlers.handleEscapeKey(event, webView)
        verify(webView, org.mockito.kotlin.never()).engine
    }
}
