package com.stacklens.adapter.inbound

import com.stacklens.application.port.inbound.platform.DockerEnvironmentStartupUseCase
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(22)
@ConditionalOnProperty(
    name = ["stacklens.docker.auto-start.runner.enabled"],
    matchIfMissing = true,
    havingValue = "true",
)
class DockerAutoStartRunner(
    private val dockerEnvironmentStartup: DockerEnvironmentStartupUseCase,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        dockerEnvironmentStartup.startDemoEnvironmentIfEnabledInBackground()
    }
}
