package com.stacklens.desktop

import javafx.application.Application
import javafx.concurrent.Worker
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebView
import javafx.stage.Stage

fun interface JavaFxLauncher {
    fun launch(
        appClass: Class<out Application>,
        args: Array<out String>,
    )
}

fun interface HeadlessServerRunner {
    fun run()
}

internal fun defaultJavaFxLauncherLaunch(
    appClass: Class<out Application>,
    args: Array<out String>,
    launcher: (Class<out Application>, Array<out String>) -> Unit = { c, a -> Application.launch(c, *a) },
) {
    launcher(appClass, args)
}

internal fun defaultHeadlessServerRunnerRun(runServer: () -> Unit = { runHeadlessServer() }) {
    runServer()
}

val defaultJavaFxLauncher: JavaFxLauncher =
    JavaFxLauncher { appClass, args -> defaultJavaFxLauncherLaunch(appClass, args) }

val defaultHeadlessServerRunner: HeadlessServerRunner =
    HeadlessServerRunner { defaultHeadlessServerRunnerRun() }

internal fun defaultWebViewFactory(webViewCreator: WebViewCreator = DefaultWebViewCreator): WebView = webViewCreator.create()

fun interface WebViewCreator {
    fun create(): WebView
}

object DefaultWebViewCreator : WebViewCreator {
    override fun create(): WebView = WebView()
}

internal fun defaultBorderPaneFactory(borderPaneCreator: BorderPaneCreator = DefaultBorderPaneCreator): BorderPane = borderPaneCreator.create()

fun interface BorderPaneCreator {
    fun create(): BorderPane
}

object DefaultBorderPaneCreator : BorderPaneCreator {
    override fun create(): BorderPane = BorderPane()
}

internal fun defaultAppBridge(
    stage: Stage,
    port: Int,
    backend: BackendLifecycle,
): DesktopAppBridge = DesktopAppBridge(stage = stage, port = port, backend = backend)

internal fun defaultBackgroundThreadFactory(r: Runnable): Thread = Thread(r, "desktop-backend-bootstrap").apply { isDaemon = true }

internal fun defaultLoadingHtmlApplier(
    view: WebView,
    html: String,
    engineApplier: (WebView, String) -> Unit = ::defaultWebViewEngineApplier,
) {
    engineApplier(view, html)
}

internal fun defaultWebViewEngineApplier(
    view: WebView,
    html: String,
) {
    view.engine.loadContent(html)
}

internal fun defaultMainPageUrlBuilder(port: Int): String = "http://127.0.0.1:$port"

internal fun defaultMainPageLoader(
    view: WebView,
    url: String,
    loader: (WebView, String) -> Unit = ::defaultWebViewUrlLoader,
) {
    loader(view, url)
}

internal fun defaultWebViewUrlLoader(
    view: WebView,
    url: String,
) {
    view.engine.load(url)
}

internal fun defaultWorkerStateListenerInstaller(
    view: WebView,
    listener: javafx.beans.value.ChangeListener<Worker.State>,
    installer: (WebView, javafx.beans.value.ChangeListener<Worker.State>) -> Unit =
        ::defaultWebViewListenerInstaller,
) {
    installer(view, listener)
}

internal fun defaultWebViewListenerInstaller(
    view: WebView,
    listener: javafx.beans.value.ChangeListener<Worker.State>,
) {
    view.engine
        .loadWorker
        .stateProperty()
        .addListener(listener)
}
