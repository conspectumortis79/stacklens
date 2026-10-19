package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.config.DemoUrlRefreshUseCase
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(15)
class DemoUrlRefreshRunner(
    private val demoUrlRefresh: DemoUrlRefreshUseCase,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        demoUrlRefresh.refreshDemoUrlsInBackground()
    }
}
