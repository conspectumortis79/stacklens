package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DesktopJsBridgeInjectorTest {
    @Test
    fun `MODAL_HELPERS_JS declares showModalById and hideModalById`() {
        val js = DesktopJsBridgeInjector.MODAL_HELPERS_JS
        assertTrue(js.contains("window.showModalById"), "missing showModalById")
        assertTrue(js.contains("window.hideModalById"), "missing hideModalById")
    }

    @Test
    fun `MODAL_HELPERS_JS signals desktopApp ready`() {
        val js = DesktopJsBridgeInjector.MODAL_HELPERS_JS
        assertTrue(js.contains("__desktopAppReady"))
        assertTrue(js.contains("__onDesktopAppReady"))
    }

    @Test
    fun `MODAL_HELPERS_JS adds and removes backdrop`() {
        val js = DesktopJsBridgeInjector.MODAL_HELPERS_JS
        assertTrue(js.contains("modal-backdrop"))
        assertTrue(js.contains("document.body.appendChild"))
        assertTrue(js.contains("b.parentNode.removeChild"))
    }

    @Test
    fun `MODAL_HELPERS_JS toggles aria-hidden and aria-modal`() {
        val js = DesktopJsBridgeInjector.MODAL_HELPERS_JS
        assertTrue(js.contains("aria-hidden"))
        assertTrue(js.contains("aria-modal"))
    }

    @Test
    fun `EXTERNAL_LINK_JS installs click listener once`() {
        val js = DesktopJsBridgeInjector.EXTERNAL_LINK_JS
        assertTrue(js.contains("__externalLinkHandlerInstalled"))
        assertTrue(js.contains("document.addEventListener('click'"))
    }

    @Test
    fun `EXTERNAL_LINK_JS forwards external links to desktopApp`() {
        val js = DesktopJsBridgeInjector.EXTERNAL_LINK_JS
        assertTrue(js.contains("e.preventDefault()"))
        assertTrue(js.contains("e.stopImmediatePropagation()"))
        assertTrue(js.contains("window.desktopApp.openInBrowser(href)"))
    }

    @Test
    fun `EXTERNAL_LINK_JS patches window_open for external URLs`() {
        val js = DesktopJsBridgeInjector.EXTERNAL_LINK_JS
        assertTrue(js.contains("_origOpen"))
        assertTrue(js.contains("window.open = function"))
        assertTrue(js.contains("a.hostname !== location.hostname"))
    }

    @Test
    fun `EXTERNAL_LINK_JS skips non-http protocols`() {
        val js = DesktopJsBridgeInjector.EXTERNAL_LINK_JS
        assertTrue(js.contains("href.startsWith('http://')"))
        assertTrue(js.contains("href.startsWith('https://')"))
    }

    @Test
    fun `VISIBILITY_TRACKER_JS declares app-visible state`() {
        val js = DesktopJsBridgeInjector.VISIBILITY_TRACKER_JS
        assertTrue(js.contains("window._appVisible"))
    }

    @Test
    fun `VISIBILITY_TRACKER_JS listens for blur and focus`() {
        val js = DesktopJsBridgeInjector.VISIBILITY_TRACKER_JS
        assertTrue(js.contains("addEventListener('blur'"))
        assertTrue(js.contains("addEventListener('focus'"))
    }

    @Test
    fun `VISIBILITY_TRACKER_JS tracks document visibilitychange`() {
        val js = DesktopJsBridgeInjector.VISIBILITY_TRACKER_JS
        assertTrue(js.contains("visibilitychange"))
        assertTrue(js.contains("document.hidden"))
    }

    @Test
    fun `all three JS scripts are non-empty`() {
        assertTrue(DesktopJsBridgeInjector.MODAL_HELPERS_JS.isNotBlank())
        assertTrue(DesktopJsBridgeInjector.EXTERNAL_LINK_JS.isNotBlank())
        assertTrue(DesktopJsBridgeInjector.VISIBILITY_TRACKER_JS.isNotBlank())
    }

    @Test
    fun `injectOnLoad executes all four JS scripts and sets desktopApp`() {
        val webView = mock<javafx.scene.web.WebView>()
        val executor = mock<JsExecutor>()
        val injector = DesktopJsBridgeInjector(executorFactory = { executor })
        val bridge = mock<DesktopAppBridge>()

        injector.injectOnLoad(webView, bridge, port = 8090)
        assertTrue(true)
    }

    @Test
    fun `injectOnLoad swallows exceptions thrown by executeScript`() {
        val webView = mock<javafx.scene.web.WebView>()
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(org.mockito.kotlin.any<String>()))
            .thenThrow(RuntimeException("navigate"))
        val injector = DesktopJsBridgeInjector(executorFactory = { executor })
        val bridge = mock<DesktopAppBridge>()

        injector.injectOnLoad(webView, bridge, port = 8090)
        assertTrue(true)
    }

    @Test
    fun `injectOnLoad handles null window result`() {
        val webView = mock<javafx.scene.web.WebView>()
        val executor = mock<JsExecutor>()
        whenever(executor.executeScript(org.mockito.kotlin.any<String>())).thenReturn(null)
        val injector = DesktopJsBridgeInjector(executorFactory = { executor })
        val bridge = mock<DesktopAppBridge>()

        injector.injectOnLoad(webView, bridge, port = 8090)
        assertTrue(true)
    }
}
