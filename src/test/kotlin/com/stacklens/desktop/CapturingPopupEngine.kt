package com.stacklens.desktop

import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage

internal class CapturingPopupEngine : PopupHandlerEngine {
    val popupCallbacks: MutableList<(PopupFeatures) -> WebEngine> = mutableListOf()
    val visibilityHandlers: MutableList<(Boolean) -> Unit> = mutableListOf()
    val locationListeners: MutableList<ChangeListener<String>> = mutableListOf()
    val loadWorkerListeners: MutableList<ChangeListener<Worker.State>> = mutableListOf()
    val showStageCalls: MutableList<Stage> = mutableListOf()
    val closeStageCalls: MutableList<Stage> = mutableListOf()
    val windowMemberNames: MutableList<String> = mutableListOf()
    val windowMemberValues: MutableList<Any> = mutableListOf()
    var closeThrows: Boolean = false
    var setWindowMemberThrows: Boolean = false

    override fun setCreatePopupHandler(
        parentWebView: WebView,
        callback: (PopupFeatures) -> WebEngine,
    ) {
        popupCallbacks += callback
    }

    override fun setOnVisibilityChanged(
        webView: WebView,
        handler: (data: Boolean) -> Unit,
    ) {
        visibilityHandlers += handler
    }

    override fun addLocationListener(
        webView: WebView,
        listener: ChangeListener<String>,
    ) {
        locationListeners += listener
    }

    override fun addLoadWorkerStateListener(
        webView: WebView,
        listener: ChangeListener<Worker.State>,
    ) {
        loadWorkerListeners += listener
    }

    override fun setWindowMember(
        webView: WebView,
        name: String,
        value: Any,
    ) {
        if (setWindowMemberThrows) throw IllegalStateException("inject failed")
        windowMemberNames += name
        windowMemberValues += value
    }

    override fun showStage(stage: Stage) {
        showStageCalls += stage
    }

    override fun closeStage(stage: Stage) {
        closeStageCalls += stage
        if (closeThrows) throw IllegalStateException("close failed")
    }

    override fun runLater(block: () -> Unit) {
        block()
    }
}
