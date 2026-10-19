package com.stacklens.desktop

import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.web.WebView

internal class DesktopKeyHandlers(
    private val executorFactory: (WebView) -> JsExecutor = ::WebViewJsExecutor,
) {
    fun install(
        stage: javafx.stage.Stage,
        webView: WebView,
    ) {
        stage.scene.addEventFilter(KeyEvent.KEY_PRESSED, EscapeKeyHandler(webView))
        stage.scene.addEventFilter(KeyEvent.KEY_PRESSED, EnterKeyHandler(webView))
    }

    fun handleEnterKey(
        event: KeyEvent,
        webView: WebView,
    ) {
        if (event.code == KeyCode.ENTER && !event.isShiftDown) {
            try {
                val consumed =
                    executorFactory(webView).executeScript(KI_CHAT_SEND_JS) as? Boolean ?: false
                if (consumed) event.consume()
            } catch (_: Exception) {
            }
        }
    }

    fun handleEscapeKey(
        event: KeyEvent,
        webView: WebView,
    ) {
        if (event.code == KeyCode.ESCAPE) {
            try {
                executorFactory(webView).executeScript(ESCAPE_CLOSE_MODAL_JS)
            } catch (_: Exception) {
            }
        }
    }

    internal inner class EscapeKeyHandler(
        private val webView: WebView,
    ) : EventHandler<KeyEvent> {
        override fun handle(event: KeyEvent) = handleEscapeKey(event, webView)
    }

    internal inner class EnterKeyHandler(
        private val webView: WebView,
    ) : EventHandler<KeyEvent> {
        override fun handle(event: KeyEvent) = handleEnterKey(event, webView)
    }

    companion object {
        internal val KI_CHAT_SEND_JS =
            "(function(){" +
                "if(document.activeElement&&document.activeElement.id==='kiChatInput'){" +
                "kiChatSend();return true;}return false;" +
                "})()"

        internal val ESCAPE_CLOSE_MODAL_JS =
            """
            (function() {
                var m = document.querySelector('.modal.show');
                if (m && m.id) {
                    if (typeof window.hideModalById === 'function') {
                        window.hideModalById(m.id); return;
                    }
                    if (typeof window.__hideModal === 'function') {
                        window.__hideModal(m.id); return;
                    }
                    var btn = m.querySelector('.btn-close');
                    if (btn) { btn.click(); return; }
                }
                var allEls = document.querySelectorAll('[id]');
                for (var i = 0; i < allEls.length; i++) {
                    var el = allEls[i];
                    if (el.id && el.id.indexOf('Overlay') !== -1 &&
                            el.style.display && el.style.display !== 'none') {
                        var closeBtn = el.querySelector('.btn-close');
                        if (closeBtn) { closeBtn.click(); break; }
                    }
                }
                document.querySelectorAll('.lt-project-item.active').forEach(function(el) {
                    el.classList.remove('active');
                });
                document.querySelectorAll('.ca-repo-card.selected').forEach(function(el) {
                    el.classList.remove('selected');
                });
                if (typeof window.__onEscDeselect === 'function') window.__onEscDeselect();
            })();
            """.trimIndent()
    }
}
