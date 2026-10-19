package com.stacklens.adapter.inbound

import com.stacklens.application.port.inbound.platform.DockerEnvironmentStartupUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DockerAutoStartRunnerCoverageTest {
    private val startup: DockerEnvironmentStartupUseCase = mock()

    @Test
    fun `a failing startup use case is surfaced to the boot caller`() {
        val runner = DockerAutoStartRunner(startup)
        whenever(startup.startDemoEnvironmentIfEnabledInBackground()).thenThrow(IllegalStateException("compose unavailable"))

        val thrown = runCatching { runner.run() }.exceptionOrNull()

        assertEquals("compose unavailable", thrown?.message)
        verify(startup).startDemoEnvironmentIfEnabledInBackground()
    }
}
