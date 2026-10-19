@file:JvmName("StackLens")
@file:Suppress("DEPRECATION")

package com.stacklens.desktop

import com.stacklens.domain.util.closingAuto
import com.stacklens.shared.utf8Reader
import javafx.application.Application
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebView
import javafx.stage.Stage

internal fun readLoadingHtml(
    classLoader: ClassLoader = DesktopLauncherApp::class.java.classLoader,
    missingResourceStream: () -> java.io.InputStream? = {
        classLoader.getResourceAsStream("/loading.html")
    },
): String =
    missingResourceStream()
        ?.closingAuto { stream -> stream.utf8Reader().readText() }
        ?: "<html><body style='display:flex;align-items:center;" +
        "justify-content:center;height:100vh;font-family:sans-serif;" +
        "background:#f5f2ea'><p>StackLens startet...</p></body></html>"

internal class DesktopLauncherApp(
    private val stageSetup: DesktopStageSetup = DesktopStageSetup(),
    private val backendBootstrap: BackendLifecycle = DesktopBackendBootstrap(),
    private val webViewConfigurator: WebViewConfigurator = WebViewConfigurator(),
    private val jsBridgeInjector: DesktopJsBridgeInjector = DesktopJsBridgeInjector(),
    private val popupHandler: DesktopPopupHandler = DesktopPopupHandler(),
    private val keyHandlers: DesktopKeyHandlers = DesktopKeyHandlers(),
    private val externalLinkInterceptor: DesktopExternalLinkInterceptor = DesktopExternalLinkInterceptor(),
    private val startupErrorReporter: DesktopStartupErrorReporter = DesktopStartupErrorReporter(),
    private val shutdownHandler: DesktopShutdownHandler = DesktopShutdownHandler(backendBootstrap),
    internal val webViewFactory: () -> WebView = ::defaultWebViewFactory,
    internal val borderPaneFactory: () -> BorderPane = ::defaultBorderPaneFactory,
    internal val bridgeFactory: (Stage, Int, BackendLifecycle) -> DesktopAppBridge = ::defaultAppBridge,
    internal val backgroundThreadFactory: (Runnable) -> Thread = ::defaultBackgroundThreadFactory,
    internal val loadingHtmlLoader: () -> String = { readLoadingHtml() },
    internal val loadingHtmlApplier: (WebView, String) -> Unit = ::defaultLoadingHtmlApplier,
    internal val mainPageUrlBuilder: (Int) -> String = ::defaultMainPageUrlBuilder,
    internal val mainPageLoader: (WebView, String) -> Unit = ::defaultMainPageLoader,
    internal val workerStateListenerInstaller: (WebView, javafx.beans.value.ChangeListener<Worker.State>) -> Unit =
        ::defaultWorkerStateListenerInstaller,
) : Application() {
    constructor() : this(backendBootstrap = DesktopBackendBootstrap())

    @Volatile
    internal var appBridge: DesktopAppBridge? = null

    override fun start(stage: Stage) {
        startInternal(
            stage = stage,
            loadingView = webViewFactory(),
            root = borderPaneFactory(),
            shutdownRegistrar = { handler -> stage.setOnCloseRequest { handler() } },
            runBackground = { runnable ->
                backgroundThreadFactory(runnable).start()
            },
        )
    }

    internal fun startInternal(
        stage: Stage,
        loadingView: WebView,
        root: BorderPane,
        shutdownRegistrar: (() -> Unit) -> Unit,
        runBackground: (Runnable) -> Unit,
        runOnFxThread: (Runnable) -> Unit = { r -> Platform.runLater(r) },
    ) {
        val loadingHtml = loadingHtmlLoader()

        loadingHtmlApplier(loadingView, loadingHtml)

        stageSetup.setup(
            stage = stage,
            title = "StackLens",
            iconResourcePath = "/stacklens-icon.png",
            root = root,
            loadingView = loadingView,
            noScrollCssResourcePath = "/webview-noscroll.css",
        )

        shutdownRegistrar {
            shutdownHandler.requestShutdown()
        }

        runBackground(
            Runnable {
                try {
                    val port = backendBootstrap.start()
                    runOnFxThread(
                        Runnable {
                            val bridge = bridgeFactory(stage, port, backendBootstrap)
                            appBridge = bridge
                            val webView = webViewFactory()
                            configureWebView(webView, stage, port, bridge, root)
                        },
                    )
                } catch (ex: Exception) {
                    startupErrorReporter.report(ex, loadingView)
                }
            },
        )
    }

    internal fun configureWebView(
        webView: WebView,
        stage: Stage,
        port: Int,
        bridge: DesktopAppBridge,
        root: BorderPane,
    ) {
        webViewConfigurator.configure(webView, stage)
        popupHandler.install(webView, stage, port, bridge)
        keyHandlers.install(stage, webView)
        externalLinkInterceptor.install(webView, port) { appBridge }
        val stateListener =
            javafx.beans.value.ChangeListener<Worker.State> { _, _, state ->
                if (state == Worker.State.SUCCEEDED) {
                    jsBridgeInjector.injectOnLoad(webView, bridge, port)
                }
            }
        workerStateListenerInstaller(webView, stateListener)
        mainPageLoader(webView, mainPageUrlBuilder(port))
        root.center = webView
    }
}

fun main(args: Array<String>) {
    runMain(
        args = args,
        javaFxLauncher = defaultJavaFxLauncher,
        headlessServerRunner = defaultHeadlessServerRunner,
        configureJvm = ::mainConfigureJvm,
        setDockIcon = ::mainSetDockIcon,
    )
}

internal fun runMain(
    args: Array<String>,
    javaFxLauncher: JavaFxLauncher,
    headlessServerRunner: HeadlessServerRunner,
    configureJvm: () -> Unit = { configureDesktopJvmProperties() },
    setDockIcon: () -> Unit = { setMacDockIcon() },
    isServerMode: (Array<String>) -> Boolean = ::isServerModeRequested,
) {
    if (isServerMode(args)) {
        headlessServerRunner.run()
        return
    }
    configureJvm()
    setDockIcon()
    javaFxLauncher.launch(DesktopLauncherApp::class.java, args)
}

internal fun isServerModeRequested(
    args: Array<String>,
    envProvider: () -> String? = { System.getenv("STACKLENS_SERVER_MODE") },
): Boolean {
    val argFlag = args.any { it == "--server" || it == "--headless" }
    val envFlag =
        envProvider()
            ?.let { it == "1" || it.equals("true", true) } == true
    return argFlag || envFlag
}
