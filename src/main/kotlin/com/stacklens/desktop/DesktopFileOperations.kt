package com.stacklens.desktop

import com.stacklens.application.port.out.platform.OsType
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Base64
import kotlin.concurrent.thread

class DesktopFileDownloader(
    private val stage: Stage,
    private val port: Int,
    private val fxRunner: JavaFxRunner = DefaultJavaFxRunner,
    private val fileChooserFactory: () -> FileChooser = { FileChooser() },
    private val downloader: (java.net.URL, File) -> Unit = { url, file -> downloadToFile(url, file) },
) {
    private val log = LoggerFactory.getLogger(DesktopFileDownloader::class.java)

    fun downloadTool(
        filename: String,
        toolId: String,
    ) {
        fxRunner.runOnFxThread {
            stage.toFront()
            val fileChooser = fileChooserFactory()
            fileChooser.initialFileName = filename
            val file: File? = fileChooser.showSaveDialog(stage)
            downloadToolBody(filename, toolId, file)
        }
    }

    fun downloadToolBody(
        filename: String,
        toolId: String,
        targetFile: File?,
    ) {
        if (targetFile == null) return
        thread(name = "download-tool", isDaemon = true) {
            runCatching {
                downloadToFile(toolExportUrl(port, toolId), targetFile)
            }.onFailure { log.error("downloadTool failed for $toolId", it) }
        }
    }

    fun downloadUrl(
        relPath: String,
        filename: String,
    ) {
        fxRunner.runOnFxThread {
            stage.toFront()
            val fileChooser = fileChooserFactory()
            fileChooser.initialFileName = filename
            val file: File? = fileChooser.showSaveDialog(stage)
            downloadUrlBody(relPath, file)
        }
    }

    fun downloadUrlBody(
        relPath: String,
        targetFile: File?,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        if (targetFile == null) return
        thread(name = "download-url", isDaemon = true) {
            try {
                downloader(relativeUrl(port, relPath), targetFile)
                val msg = "File saved: ${targetFile.absolutePath}"
                if (onSuccess != null) onSuccess(msg) else fxRunner.runOnFxThread { showInfo("Saved", msg) }
            } catch (e: Exception) {
                if (onError != null) {
                    onError(e.message ?: "Unknown error")
                } else {
                    fxRunner.runOnFxThread { showError("Download failed", e.message ?: "Unknown error") }
                }
            }
        }
    }

    companion object {
        internal fun toolExportUrl(
            port: Int,
            toolId: String,
        ): java.net.URL = java.net.URI("http://127.0.0.1:$port/api/tooling/$toolId/export").toURL()

        internal fun relativeUrl(
            port: Int,
            relPath: String,
        ): java.net.URL = java.net.URI("http://127.0.0.1:$port$relPath").toURL()

        internal fun downloadToFile(
            url: java.net.URL,
            file: File,
        ) {
            url.openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        internal fun showInfo(
            title: String,
            content: String,
        ) {
            val alert = Alert(AlertType.INFORMATION)
            alert.title = title
            alert.headerText = null
            alert.contentText = content
            alert.showAndWait()
        }

        internal fun showError(
            title: String,
            content: String?,
        ) {
            val alert = Alert(AlertType.ERROR)
            alert.title = title
            alert.headerText = null
            alert.contentText = content
            alert.showAndWait()
        }
    }
}

class DesktopFileSaver(
    private val stage: Stage,
    private val fxRunner: JavaFxRunner = DefaultJavaFxRunner,
    private val fileChooserFactory: () -> FileChooser = { FileChooser() },
    private val infoAlert: (String, String) -> Unit = { title, content ->
        DesktopFileDownloader.showInfo(title, content)
    },
    private val errorAlert: (String, String?) -> Unit = { title, content ->
        DesktopFileDownloader.showError(title, content)
    },
    private val decoder: (String) -> ByteArray = Companion::decodeBase64,
    private val writer: (File, ByteArray) -> Unit = Companion::writeBytes,
) {
    fun saveFile(
        filename: String,
        base64Data: String,
    ) {
        fxRunner.runOnFxThread {
            stage.toFront()
            val fileChooser = fileChooserFactory()
            fileChooser.initialFileName = filename
            val file: File? = fileChooser.showSaveDialog(stage)
            saveFileBody(filename, base64Data, file)
        }
    }

    fun saveFileBody(
        filename: String,
        base64Data: String,
        targetFile: File?,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        if (targetFile == null) return
        try {
            val bytes = decoder(base64Data)
            writer(targetFile, bytes)
            val msg = "File saved at ${targetFile.absolutePath}."
            if (onSuccess != null) {
                onSuccess(msg)
            } else {
                infoAlert("Saved successfully", msg)
            }
        } catch (e: Exception) {
            if (onError != null) {
                onError(e.message ?: "Unknown error")
            } else {
                errorAlert("Error saving", e.message)
            }
        }
    }

    companion object {
        internal fun decodeBase64(base64Data: String): ByteArray = Base64.getDecoder().decode(base64Data)

        internal fun writeBytes(
            file: File,
            bytes: ByteArray,
        ) {
            file.writeBytes(bytes)
        }
    }
}

class BrowserLauncher(
    private val fxRunner: JavaFxRunner = DefaultJavaFxRunner,
    private val processRunner: (List<String>) -> Boolean = Companion::runProcess,
    private val desktopBrowserProvider: () -> java.awt.Desktop = { java.awt.Desktop.getDesktop() },
    private val osNameProvider: () -> String = { System.getProperty("os.name", "").lowercase() },
) {
    private val log = LoggerFactory.getLogger(BrowserLauncher::class.java)

    fun openInBrowser(url: String) {
        openInBrowserInternal(url, processRunner, desktopBrowserProvider()::browse, osNameProvider)
    }

    internal fun openInBrowserInternal(
        url: String,
        processRunner: (List<String>) -> Boolean,
        desktopBrowser: (java.net.URI) -> Unit,
        osNameProvider: () -> String = { System.getProperty("os.name", "").lowercase() },
    ) {
        try {
            val os = osNameProvider()
            val cmd = buildCommand(os, url)
            processRunner(cmd)
        } catch (_: Exception) {
            try {
                desktopBrowser(java.net.URI(url))
            } catch (e: Exception) {
                log.error("openInBrowser fallback failed for $url", e)
            }
        }
    }

    companion object {
        internal fun buildCommand(
            os: String,
            url: String,
        ): List<String> =
            when (OsType.fromOsName(os)) {
                OsType.MACOS -> listOf("open", url)
                OsType.WINDOWS -> listOf("cmd", "/c", "start", "", url)
                else -> listOf("xdg-open", url)
            }

        internal fun runProcess(cmd: List<String>): Boolean {
            ProcessBuilder(cmd).inheritIO().start()
            return true
        }
    }
}

class DesktopPreferences(
    private val preferencesNode: java.util.prefs.Preferences = defaultPreferences(),
) {
    fun setPreference(
        key: String,
        value: String,
    ) {
        preferencesNode.put(key, value)
    }

    fun getPreference(key: String): String = preferencesNode.get(key, "")

    companion object {
        internal fun defaultPreferences(): java.util.prefs.Preferences =
            java.util.prefs.Preferences
                .userRoot()
                .node("com/stacklens/ui")
    }
}
