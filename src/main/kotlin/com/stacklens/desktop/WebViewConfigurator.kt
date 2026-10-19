package com.stacklens.desktop

import javafx.scene.web.WebView
import java.nio.file.Path

internal class WebViewConfigurator(
    private val home: String = System.getProperty("user.home", ""),
) {
    fun configure(
        webView: WebView,
        stage: javafx.stage.Stage,
    ): Unit = configureWeb(buildWebAdapterFor(webView), buildStageAdapterFor(stage))

    internal fun buildWebAdapterFor(webView: WebView): RealWebConfigAdapter =
        buildWebAdapterFor(
            webView = webView,
            engineBridge = defaultEngineBridgeFor(webView),
            contextMenu = { webView.isContextMenuEnabled },
            setContextMenu = { webView.isContextMenuEnabled = it },
            cache = { webView.isCache },
            setCache = { webView.isCache = it },
            cacheHint = { webView.cacheHint },
            setCacheHint = { webView.cacheHint = it },
        )

    internal fun buildWebAdapterFor(
        webView: WebView,
        engineBridge: EngineBridge,
        contextMenu: () -> Boolean,
        setContextMenu: (Boolean) -> Unit,
        cache: () -> Boolean,
        setCache: (Boolean) -> Unit,
        cacheHint: () -> javafx.scene.CacheHint,
        setCacheHint: (javafx.scene.CacheHint) -> Unit,
    ): RealWebConfigAdapter =
        RealWebConfigAdapter(
            engineBridge = engineBridge,
            getCtxMenu = contextMenu,
            setCtxMenu = setContextMenu,
            getCache = cache,
            setCache = setCache,
            getHint = cacheHint,
            setHint = setCacheHint,
        )

    internal fun defaultEngineBridgeFor(
        webView: WebView,
        getUserDataDir: () -> java.io.File? = { webView.engine.userDataDirectory },
        setUserDataDir: (java.io.File?) -> Unit = { webView.engine.userDataDirectory = it },
        setEngineConfirm: ((String?) -> Boolean) -> Unit = { webView.engine.setConfirmHandler(it) },
        setEngineAlert: ((String) -> Unit) -> Unit = { handler ->
            webView.engine.onAlert =
                javafx.event.EventHandler<javafx.scene.web.WebEvent<String>> { event -> handler(event.data) }
        },
    ): EngineBridge =
        object : EngineBridge {
            override var userDataDirectory: java.io.File?
                get() = getUserDataDir()
                set(value) {
                    setUserDataDir(value)
                }

            override fun setConfirmHandler(handler: (String?) -> Boolean) {
                setEngineConfirm(handler)
            }

            override fun setAlertHandler(handler: (String) -> Unit) {
                setEngineAlert(handler)
            }
        }

    internal fun buildStageAdapterFor(stage: javafx.stage.Stage): RealStageConfigAdapter =
        RealStageConfigAdapter(
            getWidth = { stage.widthProperty() },
            getHeight = { stage.heightProperty() },
        )

    internal fun configureWeb(
        web: WebConfig,
        stage: StageConfig,
    ) {
        web.userDataDirectory = resolveWebDataDir(home)
        web.setConfirmHandler(buildConfirmHandler())
        web.setAlertHandler(buildAlertHandler())
        web.contextMenuEnabled = false
        installResizeCacheHint(stage, web)
    }

    interface WebConfig {
        var userDataDirectory: java.io.File?
        var contextMenuEnabled: Boolean
        var isCache: Boolean
        var cacheHint: javafx.scene.CacheHint

        fun setConfirmHandler(handler: (String?) -> Boolean)

        fun setAlertHandler(handler: (String) -> Unit)
    }

    interface StageConfig {
        fun widthProperty(): javafx.beans.value.ObservableNumberValue

        fun heightProperty(): javafx.beans.value.ObservableNumberValue
    }

    internal interface EngineBridge {
        var userDataDirectory: java.io.File?

        fun setConfirmHandler(handler: (String?) -> Boolean)

        fun setAlertHandler(handler: (String) -> Unit)
    }

    internal class RealWebConfigAdapter(
        private val engineBridge: EngineBridge,
        private var getCtxMenu: () -> Boolean,
        private var setCtxMenu: (Boolean) -> Unit,
        private var getCache: () -> Boolean,
        private var setCache: (Boolean) -> Unit,
        private var getHint: () -> javafx.scene.CacheHint,
        private var setHint: (javafx.scene.CacheHint) -> Unit,
    ) : WebConfig {
        override var userDataDirectory: java.io.File?
            get() = engineBridge.userDataDirectory
            set(value) {
                engineBridge.userDataDirectory = value
            }
        override var contextMenuEnabled: Boolean
            get() = getCtxMenu()
            set(value) {
                setCtxMenu(value)
            }
        override var isCache: Boolean
            get() = getCache()
            set(value) {
                setCache(value)
            }
        override var cacheHint: javafx.scene.CacheHint
            get() = getHint()
            set(value) {
                setHint(value)
            }

        override fun setConfirmHandler(handler: (String?) -> Boolean) {
            engineBridge.setConfirmHandler(handler)
        }

        override fun setAlertHandler(handler: (String) -> Unit) {
            engineBridge.setAlertHandler(handler)
        }
    }

    internal class RealStageConfigAdapter(
        private val getWidth: () -> javafx.beans.value.ObservableNumberValue,
        private val getHeight: () -> javafx.beans.value.ObservableNumberValue,
    ) : StageConfig {
        override fun widthProperty(): javafx.beans.value.ObservableNumberValue = getWidth()

        override fun heightProperty(): javafx.beans.value.ObservableNumberValue = getHeight()
    }

    internal fun buildConfirmHandler(): (String?) -> Boolean =
        { message ->
            val spec = buildConfirmSpec(message)
            showConfirmDialog(spec)
        }

    internal fun buildAlertHandler(): (String) -> Unit = { data -> showAlert(buildAlertSpec(data)) }

    data class DialogSpec(
        val title: String,
        val header: String?,
        val content: String,
        val type: DialogType,
    )

    enum class DialogType { CONFIRMATION, INFORMATION }

    internal fun buildConfirmSpec(message: String?): DialogSpec =
        DialogSpec(
            title = "Bestaetigung",
            header = null,
            content = message ?: "Aktion bestaetigen?",
            type = DialogType.CONFIRMATION,
        )

    internal fun buildAlertSpec(data: String): DialogSpec =
        DialogSpec(
            title = "Hinweis",
            header = null,
            content = data,
            type = DialogType.INFORMATION,
        )

    internal fun showConfirmDialog(spec: DialogSpec): Boolean = confirmDialogShower(spec) == ButtonChoice.OK

    internal fun showAlert(spec: DialogSpec) {
        alertDialogShower(spec)
    }

    var confirmDialogShower: (DialogSpec) -> ButtonChoice = { _ -> ButtonChoice.CANCEL }

    var alertDialogShower: (DialogSpec) -> Unit = {}

    enum class ButtonChoice { OK, CANCEL }

    internal fun resolveWebDataDir(home: String): java.io.File =
        Path
            .of(home, ".stacklens", "webdata")
            .toFile()
            .apply { mkdirs() }

    internal fun buildResizeHint(
        webView: WebView,
        stage: javafx.stage.Stage,
    ): Pair<javafx.animation.PauseTransition, javafx.beans.value.ChangeListener<Number>> =
        buildResizeHint(
            setCacheTrue = { webView.isCache = true },
            setCacheFalse = { webView.isCache = false },
            setHintSpeed = { webView.cacheHint = javafx.scene.CacheHint.SPEED },
            setHintDefault = { webView.cacheHint = javafx.scene.CacheHint.DEFAULT },
            playPause = { it.playFromStart() },
        )

    internal fun buildResizeHint(
        setCacheTrue: () -> Unit,
        setCacheFalse: () -> Unit,
        setHintSpeed: () -> Unit,
        setHintDefault: () -> Unit,
        playPause: (javafx.animation.PauseTransition) -> Unit = { it.playFromStart() },
    ): Pair<javafx.animation.PauseTransition, javafx.beans.value.ChangeListener<Number>> {
        val resizePause = javafx.animation.PauseTransition(javafx.util.Duration.millis(200.0))
        resizePause.setOnFinished {
            setCacheFalse()
            setHintDefault()
        }
        val onResize =
            javafx.beans.value.ChangeListener<Number> { _, _, _ ->
                setCacheTrue()
                setHintSpeed()
                playPause(resizePause)
            }
        return resizePause to onResize
    }

    internal fun installResizeCacheHint(
        webView: WebView,
        stage: javafx.stage.Stage,
    ) {
        val (_, onResize) = buildResizeHint(webView, stage)
        stage.widthProperty().addListener(onResize)
        stage.heightProperty().addListener(onResize)
    }

    internal fun installResizeCacheHint(
        stage: StageConfig,
        web: WebConfig,
        playPause: (javafx.animation.PauseTransition) -> Unit = { it.playFromStart() },
    ) {
        val resizePause = javafx.animation.PauseTransition(javafx.util.Duration.millis(200.0))
        resizePause.setOnFinished {
            web.isCache = false
            web.cacheHint = javafx.scene.CacheHint.DEFAULT
        }
        val onResize =
            javafx.beans.value.ChangeListener<Number> { _, _, _ ->
                web.isCache = true
                web.cacheHint = javafx.scene.CacheHint.SPEED
                playPause(resizePause)
            }
        stage.widthProperty().addListener(onResize)
        stage.heightProperty().addListener(onResize)
    }
}
