package com.stacklens.desktop

import com.stacklens.domain.util.quietly
import com.stacklens.shared.haltImmediately
import javafx.application.Platform
import javafx.stage.Stage
import kotlin.concurrent.thread

internal class DesktopQuitCoordinator(
    private val stage: Stage,
    private val backend: BackendLifecycle,
    private val fxRunner: JavaFxRunner = DefaultJavaFxRunner,
    private val exitFx: () -> Unit = { Platform.exit() },
    private val threadFactory: (String, Boolean, () -> Unit) -> Thread =
        { name, isDaemon, block ->
            thread(name = name, isDaemon = isDaemon) { block() }
        },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val exit: () -> Unit = haltImmediately,
) {
    fun quit() {
        fxRunner.runOnFxThread {
            stage.hide()
            exitFx()
        }
        threadFactory("quit", true) {
            quietly { backend.checkpoint() }
            quietly { backend.stop() }
            exit()
        }
        threadFactory("quit-watchdog", true) {
            sleep(QUIT_WATCHDOG_MS)
            exit()
        }
    }

    companion object {
        internal const val QUIT_WATCHDOG_MS: Long = 800L
    }
}
