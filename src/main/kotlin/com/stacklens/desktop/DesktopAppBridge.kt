package com.stacklens.desktop

import javafx.stage.Stage

internal class DuplicateInstanceException : RuntimeException("StackLens is already running")

internal class DesktopAppBridge(
    private val stage: Stage,
    private val port: Int,
    backend: BackendLifecycle,
    private val downloader: DesktopFileDownloader = DesktopFileDownloader(stage, port),
    private val saver: DesktopFileSaver = DesktopFileSaver(stage),
    private val browser: BrowserLauncher = BrowserLauncher(),
    private val preferences: DesktopPreferences = DesktopPreferences(),
    private val quitCoordinator: DesktopQuitCoordinator = DesktopQuitCoordinator(stage, backend),
) {
    fun downloadTool(
        filename: String,
        toolId: String,
    ) = downloader.downloadTool(filename, toolId)

    fun downloadUrl(
        relPath: String,
        filename: String,
    ) = downloader.downloadUrl(relPath, filename)

    fun openInBrowser(url: String) = browser.openInBrowser(url)

    fun quit() = quitCoordinator.quit()

    fun setPreference(
        key: String,
        value: String,
    ) = preferences.setPreference(key, value)

    fun getPreference(key: String): String = preferences.getPreference(key)

    fun saveFile(
        filename: String,
        base64Data: String,
    ) = saver.saveFile(filename, base64Data)
}
