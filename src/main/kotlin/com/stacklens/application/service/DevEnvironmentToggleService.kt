package com.stacklens.application.service

import com.stacklens.application.collaboration.DevEnvironmentToggle
import com.stacklens.application.port.inbound.platform.DevEnvironmentToggleUseCase
import com.stacklens.application.port.out.docker.DemoEnvironmentPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

@ApplicationService
@ManuallyWired
class DevEnvironmentToggleService(
    private val demoEnvironment: DemoEnvironmentPort,
    private val executor: Executor,
) : DevEnvironmentToggleUseCase,
    DevEnvironmentToggle {
    private val log = LoggerFactory.getLogger(DevEnvironmentToggleService::class.java)

    override fun reconcileDockerTestEnv(enabled: Boolean) {
        executor.execute {
            try {
                if (enabled) {
                    val outcome = demoEnvironment.bringUp(LONG_RUN_TIMEOUT_SECONDS)
                    if (outcome != null) demoEnvironment.ensureMysqlSchema()
                } else {
                    demoEnvironment.bringDown()
                }
            } catch (e: Exception) {
                log.warn("[DevEnvironmentToggle] failed: {}", e.message)
            }
        }
    }

    private companion object {
        const val LONG_RUN_TIMEOUT_SECONDS: Long = 120L
    }
}
