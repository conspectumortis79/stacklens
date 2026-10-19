package com.stacklens.desktop

import javafx.application.Platform

object JavafxTestSupport {
    private val initialised =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    fun init() {
        if (initialised.compareAndSet(false, true)) {
            try {
                Platform.startup {}
            } catch (_: IllegalStateException) {
            } catch (_: Exception) {
            }
            try {
                Class.forName("javafx.scene.web.WebEngine", true, javaClass.classLoader)
            } catch (_: Throwable) {
            }
        }
    }
}
