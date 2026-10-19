package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.DemoUrlRefreshUseCase
import com.stacklens.application.port.out.async.StartupTaskExecutor
import com.stacklens.application.port.out.config.DemoUrlAlignmentPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class DemoUrlRefreshService(
    private val demoUrlAlignment: DemoUrlAlignmentPort,
    private val startupTasks: StartupTaskExecutor,
) : DemoUrlRefreshUseCase {
    private val log = LoggerFactory.getLogger(DemoUrlRefreshService::class.java)

    override fun refreshDemoUrlsInBackground() {
        startupTasks.submit {
            val refreshed = demoUrlAlignment.alignAll()
            if (refreshed.isEmpty()) {
                log.debug("[DemoUrlRefresh] all demo URLs already match the current runtime")
            } else {
                log.info(
                    "[DemoUrlRefresh] rewrote {} stale demo URL(s) to match the current runtime: {}",
                    refreshed.size,
                    refreshed.joinToString(),
                )
            }
        }
    }
}
