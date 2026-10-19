package com.stacklens.desktop

import com.stacklens.shared.exitJvm
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.web.WebView

internal class DesktopStartupErrorReporter(
    private val fxRunner: JavaFxRunner = DefaultJavaFxRunner,
    private val systemExit: (Int) -> Unit = exitJvm,
    private val showDuplicateDialog: () -> Unit = { showDuplicateInstanceDialog() },
) {
    fun report(
        error: Exception,
        loadingView: WebView,
    ) {
        fxRunner.runOnFxThread {
            if (error is DuplicateInstanceException) {
                showDuplicateDialog()
                fxRunner.exitFx()
                systemExit(1)
            } else {
                val msg = escapeForJs(error.message ?: "Unknown error")
                try {
                    loadingView.engine.executeScript(buildSetErrorCall(msg))
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        internal fun escapeForJs(raw: String): String =
            raw
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")

        internal fun buildSetErrorCall(escapedMessage: String): String = "if(window.setError) window.setError('$escapedMessage')"

        private fun showDuplicateInstanceDialog() {
            val alert =
                Alert(Alert.AlertType.NONE).apply {
                    title = "StackLens"
                    headerText = "StackLens läuft bereits"
                    contentText = "Schließe die laufende Instanz und starte StackLens danach neu."
                    buttonTypes.setAll(ButtonType.OK)
                }
            alert.showAndWait()
        }
    }
}
