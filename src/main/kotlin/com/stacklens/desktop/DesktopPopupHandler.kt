package com.stacklens.desktop

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import java.util.concurrent.atomic.AtomicBoolean

internal interface PopupHandlerEngine {
    fun setCreatePopupHandler(
        parentWebView: WebView,
        callback: (PopupFeatures) -> WebEngine,
    )

    fun setOnVisibilityChanged(
        webView: WebView,
        handler: (data: Boolean) -> Unit,
    )

    fun addLocationListener(
        webView: WebView,
        listener: ChangeListener<String>,
    )

    fun addLoadWorkerStateListener(
        webView: WebView,
        listener: ChangeListener<Worker.State>,
    )

    fun setWindowMember(
        webView: WebView,
        name: String,
        value: Any,
    )

    fun showStage(stage: Stage)

    fun closeStage(stage: Stage)

    fun runLater(block: () -> Unit)
}

internal open class RealPopupHandlerStrategy(
    private val engineOf: (WebView) -> WebEngine? = { it.engine },
) : PopupHandlerEngine {
    override fun setCreatePopupHandler(
        parentWebView: WebView,
        callback: (PopupFeatures) -> WebEngine,
    ) {
        val engine = engineOf(parentWebView) ?: return
        engine.setCreatePopupHandler(callback)
    }

    override fun setOnVisibilityChanged(
        webView: WebView,
        handler: (data: Boolean) -> Unit,
    ) {
        val engine = engineOf(webView) ?: return
        engine.setOnVisibilityChanged { event ->
            handler(event.data)
        }
    }

    override fun addLocationListener(
        webView: WebView,
        listener: ChangeListener<String>,
    ) {
        val engine = engineOf(webView) ?: return
        engine.locationProperty().addListener(listener)
    }

    override fun addLoadWorkerStateListener(
        webView: WebView,
        listener: ChangeListener<Worker.State>,
    ) {
        val engine = engineOf(webView) ?: return
        engine.loadWorker
            .stateProperty()
            .addListener(listener)
    }

    override fun setWindowMember(
        webView: WebView,
        name: String,
        value: Any,
    ) {
        val jsWin = engineOf(webView)?.executeScript("window")
        jsWin?.jsSetMember(name, value)
    }

    override fun showStage(stage: Stage) {
        stage.show()
    }

    override fun closeStage(stage: Stage) {
        stage.close()
    }

    override fun runLater(block: () -> Unit) {
        Platform.runLater(block)
    }
}

internal class DefaultPopupHandlerEngine(
    private val strategy: PopupHandlerEngine = RealPopupHandlerStrategy(),
) : PopupHandlerEngine by strategy

internal class DesktopPopupHandler(
    private val engine: PopupHandlerEngine = DefaultPopupHandlerEngine(),
    private val cssResource: (String) -> java.net.URL? = { javaClass.getResource(it) },
) {
    fun install(
        parentWebView: WebView,
        parentStage: Stage,
        port: Int,
        bridge: DesktopAppBridge,
    ) {
        engine.setCreatePopupHandler(parentWebView) { _ ->
            buildAndConfigurePopup(
                parentWebView = parentWebView,
                parentStage = parentStage,
                port = port,
                bridge = bridge,
                popupStageFactory = ::Stage,
                popupWebViewFactory = ::WebView,
                popupRootFactory = { wv -> BorderPane(wv) },
                sceneFactory = { root -> Scene(root, 1100.0, 720.0) },
            ).engine
        }
    }

    internal fun buildAndConfigurePopup(
        parentWebView: WebView,
        parentStage: Stage,
        port: Int,
        bridge: DesktopAppBridge,
        popupStageFactory: () -> Stage,
        popupWebViewFactory: () -> WebView,
        popupRootFactory: (WebView) -> BorderPane,
        sceneFactory: (BorderPane) -> Scene,
    ): WebView {
        val popupStage = popupStageFactory()
        val popupWebView = popupWebViewFactory()
        popupWebView.isContextMenuEnabled = false
        val popupRoot = popupRootFactory(popupWebView)
        popupStage.scene = sceneFactory(popupRoot)
        applyNoScrollCss(popupStage)
        popupStage.title = "StackLens"
        try {
            popupStage.icons.addAll(parentStage.icons)
        } catch (_: Exception) {
        }
        installPopupCloseOnVisibilityLoss(popupStage, popupWebView)
        routePopupNavigation(popupWebView, popupStage, port, bridge)
        return popupWebView
    }

    private fun applyNoScrollCss(stage: Stage) {
        val css = cssResource("/webview-noscroll.css") ?: return
        val scene = stage.scene ?: return
        scene.stylesheets.add(css.toExternalForm())
    }

    internal fun installPopupCloseOnVisibilityLoss(
        popupStage: Stage,
        popupWebView: WebView,
    ) {
        engine.setOnVisibilityChanged(popupWebView) { data ->
            if (data == false) {
                engine.runLater {
                    try {
                        engine.closeStage(popupStage)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    internal fun routePopupNavigation(
        popupWebView: WebView,
        popupStage: Stage,
        port: Int,
        bridge: DesktopAppBridge,
    ) {
        val opened = AtomicBoolean(false)
        engine.addLocationListener(popupWebView) { _, _, newLocation ->
            if (opened.get() || newLocation.isNullOrEmpty()) return@addLocationListener
            if (newLocation == "about:blank") return@addLocationListener
            opened.set(true)
            if (isInternalUrl(newLocation, port)) {
                wireBridgeWhenLoaded(popupWebView, bridge)
                engine.runLater { engine.showStage(popupStage) }
            } else {
                engine.runLater {
                    try {
                        engine.closeStage(popupStage)
                    } catch (_: Exception) {
                    }
                    bridge.openInBrowser(newLocation)
                }
            }
        }
    }

    private fun wireBridgeWhenLoaded(
        popupWebView: WebView,
        bridge: DesktopAppBridge,
    ) {
        engine.addLoadWorkerStateListener(popupWebView) { _, _, state ->
            if (state == Worker.State.SUCCEEDED) {
                try {
                    engine.setWindowMember(popupWebView, "desktopApp", bridge)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        internal fun isInternalUrl(
            url: String,
            port: Int,
        ): Boolean =
            url.startsWith("http://127.0.0.1:$port") ||
                url.startsWith("http://localhost:$port")
    }
}
