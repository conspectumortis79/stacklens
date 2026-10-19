package com.stacklens.adapter.inbound

import com.stacklens.application.port.inbound.platform.DockerEnvironmentStartupUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class DockerAutoStartRunnerTest {
    private val startup: DockerEnvironmentStartupUseCase = mock()

    @Test
    fun `startup triggers the demo environment use case`() {
        DockerAutoStartRunner(startup).run()

        verify(startup).startDemoEnvironmentIfEnabledInBackground()
    }

    @Test
    fun `extra command line arguments do not change the trigger`() {
        DockerAutoStartRunner(startup).run("--demo", "--reset")

        verify(startup).startDemoEnvironmentIfEnabledInBackground()
    }

    @Test
    fun `the runner stays a pure trigger without additional work`() {
        val untouched: DockerEnvironmentStartupUseCase = mock()

        DockerAutoStartRunner(untouched)

        verifyNoInteractions(untouched)
    }
}
