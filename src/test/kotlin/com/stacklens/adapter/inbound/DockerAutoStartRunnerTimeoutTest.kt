package com.stacklens.adapter.inbound

import com.stacklens.application.port.inbound.platform.DockerEnvironmentStartupUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DockerAutoStartRunnerTimeoutTest {
    private val startup: DockerEnvironmentStartupUseCase = mock()

    @Test
    fun `the runner delegates the timeout budget decision to the use case`() {
        DockerAutoStartRunner(startup).run()

        verify(startup).startDemoEnvironmentIfEnabledInBackground()
    }
}
