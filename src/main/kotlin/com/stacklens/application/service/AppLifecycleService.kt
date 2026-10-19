package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.AppLifecycleUseCase
import com.stacklens.application.port.out.async.BoundedTaskPort
import com.stacklens.application.port.out.platform.ApplicationContextPort
import com.stacklens.application.port.out.platform.JvmTerminationPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
open class AppLifecycleService(
    private val sleeper: SleeperPort,
    private val boundedTasks: BoundedTaskPort,
    private val applicationContextPort: ApplicationContextPort,
    private val jvmTermination: JvmTerminationPort,
) : AppLifecycleUseCase {
    internal open val shutdownGracePeriodMs: Long get() = 0L

    override fun quit() {
        sleeper.sleep(shutdownGracePeriodMs)
        boundedTasks.runBounded(CLOSE_TASK_NAME, SHUTDOWN_TIMEOUT_MS) { applicationContextPort.shutdown() }
        jvmTermination.halt(EXIT_STATUS_OK)
    }

    companion object {
        const val SHUTDOWN_TIMEOUT_MS: Long = 800L
        const val EXIT_STATUS_OK: Int = 0
        private const val CLOSE_TASK_NAME: String = "stacklens-appctx-close"
    }
}
