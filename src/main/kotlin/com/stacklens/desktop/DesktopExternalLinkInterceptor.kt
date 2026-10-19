package com.stacklens.desktop

import javafx.beans.value.ChangeListener
import javafx.scene.web.WebView

internal class DesktopExternalLinkInterceptor(
    private val fxRunner: JavaFxRunner = DefaultJavaFxRunner,
) {
    fun install(
        webView: WebView,
        port: Int,
        bridgeProvider: () -> DesktopAppBridge?,
    ) {
        webView.engine.locationProperty().addListener(
            LocationChangeListener(port, bridgeProvider, webView, fxRunner),
        )
    }

    internal class LocationChangeListener(
        private val port: Int,
        private val bridgeProvider: () -> DesktopAppBridge?,
        private val webView: WebView,
        private val fxRunner: JavaFxRunner,
        private val loadCallback: (String) -> Unit = { webView.engine.load(it) },
    ) : ChangeListener<String?> {
        override fun changed(
            observable: javafx.beans.value.ObservableValue<out String?>?,
            oldValue: String?,
            newValue: String?,
        ) {
            val b = bridgeProvider() ?: return
            if (!isExternal(newValue, port)) return
            b.openInBrowser(newValue!!)
            val returnUrl = chooseReturnUrl(oldValue, port)
            fxRunner.runOnFxThread { loadCallback(returnUrl) }
        }
    }

    companion object {
        internal fun chooseReturnUrl(
            oldLoc: String?,
            port: Int,
        ): String =
            if (!oldLoc.isNullOrEmpty() && oldLoc.startsWith("http://127.0.0.1:$port")) {
                oldLoc
            } else {
                "http://127.0.0.1:$port"
            }

        internal fun isExternal(
            url: String?,
            port: Int,
        ): Boolean {
            if (url.isNullOrEmpty()) return false
            if (!url.startsWith("http://") && !url.startsWith("https://")) return false
            if (url.startsWith("http://127.0.0.1:$port")) return false
            return true
        }
    }
}
