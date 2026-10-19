package com.stacklens.desktop

import javafx.application.Platform

fun interface JavaFxRunner {
    fun runOnFxThread(block: () -> Unit)

    fun exitFx() {}
}

object DefaultJavaFxRunner : JavaFxRunner {
    override fun runOnFxThread(block: () -> Unit) {
        Platform.runLater(block)
    }

    override fun exitFx() {
        Platform.exit()
    }
}
