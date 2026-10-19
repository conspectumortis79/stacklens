package com.stacklens.desktop

import javafx.scene.layout.BorderPane
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebView
import javafx.stage.Stage

internal fun DesktopPopupHandler.buildPopupHandler(
    port: Int,
    bridge: DesktopAppBridge,
    popupWebView: WebView,
    popupRoot: BorderPane,
    popupStage: Stage,
    webEngineFactory: () -> Any?,
): (PopupFeatures) -> Any? =
    { _ ->
        try {
            popupStage.icons.addAll(popupStage.icons)
        } catch (_: Exception) {
        }
        installPopupCloseOnVisibilityLoss(popupStage, popupWebView)
        routePopupNavigation(popupWebView, popupStage, port, bridge)
        webEngineFactory()
    }
