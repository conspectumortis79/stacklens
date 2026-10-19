package com.stacklens.desktop

import com.stacklens.domain.util.quietly
import com.stacklens.shared.haltImmediately
import kotlin.concurrent.thread

internal class DesktopShutdownHandler(
    private val backend: BackendLifecycle,
    private val threadFactory: (String, Boolean, () -> Unit) -> Thread =
        { name, isDaemon, block ->
            thread(name = name, isDaemon = isDaemon) { block() }
        },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val exit: () -> Unit = haltImmediately,
) {
    fun requestShutdown() {
        threadFactory("shutdown", true) {
            quietly { backend.checkpoint() }
            quietly { backend.stop() }
            exit()
        }
        threadFactory("shutdown-watchdog", true) {
            sleep(WATCHDOG_TIMEOUT_MS)
            exit()
        }
    }

    companion object {
        internal const val WATCHDOG_TIMEOUT_MS: Long = 800L
    }
}
