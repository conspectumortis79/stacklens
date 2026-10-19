
package com.stacklens.desktop

import javafx.scene.web.WebView

interface JsExecutor {
    fun executeScript(script: String): Any?

    fun setWindowMember(
        name: String,
        value: Any,
    )
}

interface JsExecutorStrategy {
    fun executeScript(script: String): Any?

    fun executeScriptAndSetMember(
        script: String,
        memberName: String,
        memberValue: Any,
    )
}

internal class RealJsExecutorStrategy(
    private val webView: WebView,
) : JsExecutorStrategy {
    override fun executeScript(script: String): Any? = webView.engine.executeScript(script)

    override fun executeScriptAndSetMember(
        script: String,
        memberName: String,
        memberValue: Any,
    ) {
        val jsWindow = webView.engine.executeScript(script)
        jsWindow?.jsSetMember(memberName, memberValue)
    }
}

class WebViewJsExecutor(
    private val webView: WebView,
    private val strategyFactory: (WebView) -> JsExecutorStrategy = ::RealJsExecutorStrategy,
) : JsExecutor {
    private val strategy: JsExecutorStrategy = strategyFactory(webView)

    override fun executeScript(script: String): Any? = strategy.executeScript(script)

    override fun setWindowMember(
        name: String,
        value: Any,
    ) {
        strategy.executeScriptAndSetMember("window", name, value)
    }
}

internal class DesktopJsBridgeInjector(
    private val executorFactory: (WebView) -> JsExecutor = ::WebViewJsExecutor,
) {
    fun injectOnLoad(
        webView: WebView,
        bridge: DesktopAppBridge,
        port: Int,
    ) {
        val executor = executorFactory(webView)
        try {
            executor.executeScript(MODAL_HELPERS_JS)
            executor.setWindowMember("desktopApp", bridge)
            executor.executeScript(EXTERNAL_LINK_JS)
            executor.executeScript(VISIBILITY_TRACKER_JS)
        } catch (_: Exception) {
        }
    }

    companion object {
        internal val MODAL_HELPERS_JS =
            """
            window.showModalById = function(id) {
                var m = document.getElementById(id);
                if (!m) return;
                m.className = m.className.replace(/\bshow\b/g, '').trim() + ' show';
                    m.style.setProperty('display', 'block', 'important');
                m.removeAttribute('aria-hidden');
                m.setAttribute('aria-modal', 'true');
                document.body.classList.remove('modal-open');
                document.body.classList.add('modal-open');
                if (!document.querySelector('.modal-backdrop')) {
                    var b = document.createElement('div');
                    b.className = 'modal-backdrop fade show';
                        b.style.setProperty('display', 'block', 'important');
                    document.body.appendChild(b);
                }
            };
            window.hideModalById = function(id) {
                var m = document.getElementById(id);
                if (!m) return;
                m.className = m.className.replace(/\bshow\b/g, '').trim();
                    m.style.setProperty('display', 'none', 'important');
                m.setAttribute('aria-hidden', 'true');
                m.removeAttribute('aria-modal');
                var b = document.querySelector('.modal-backdrop');
                if (b) b.parentNode.removeChild(b);
                if (!document.querySelector('.modal.show')) {
                    document.body.className = document.body.className.replace(/\bmodal-open\b/g, '').trim();
                }
            };
            window.__desktopAppReady = true;
            if (typeof window.__onDesktopAppReady === 'function') {
                window.__onDesktopAppReady();
            }
            """.trimIndent()

        internal val EXTERNAL_LINK_JS =
            """
            (function() {
                if (window.__externalLinkHandlerInstalled) return;
                window.__externalLinkHandlerInstalled = true;
                document.addEventListener('click', function(e) {
                    var el = e.target.closest('a');
                    if (!el || !el.href) return;
                    var href = el.href;
                    if (!href.startsWith('http://') && !href.startsWith('https://')) return;
                    if (el.hostname !== location.hostname) {
                        e.preventDefault();
                        e.stopImmediatePropagation();
                        window.desktopApp.openInBrowser(href);
                    }
                }, true);
                var _origOpen = window.open;
                window.open = function(url, target, features) {
                    if (url && typeof url === 'string') {
                        try {
                            var a = document.createElement('a');
                            a.href = url;
                            if (a.hostname && a.hostname !== location.hostname) {
                                window.desktopApp.openInBrowser(url);
                                return null;
                            }
                        } catch (ex) {}
                    }
                    return _origOpen ? _origOpen.call(this, url, target, features) : null;
                };
            })();
            """.trimIndent()

        internal val VISIBILITY_TRACKER_JS =
            """
            window._appVisible = true;
            window.addEventListener('blur',  function() { window._appVisible = false; });
            window.addEventListener('focus', function() { window._appVisible = true;  });
            document.addEventListener('visibilitychange', function() {
                window._appVisible = !document.hidden;
            });
            """.trimIndent()
    }
}
