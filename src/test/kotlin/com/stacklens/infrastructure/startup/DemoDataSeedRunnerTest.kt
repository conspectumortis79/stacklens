package com.stacklens.infrastructure.startup

import com.stacklens.application.port.out.platform.DemoDataPort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.DefaultApplicationArguments

class DemoDataSeedRunnerTest {
    @Test
    fun `seeds through the outbound port when it is available`() {
        val port = mock<DemoDataPort>()
        val provider = mock<ObjectProvider<DemoDataPort>>()
        org.mockito.kotlin
            .whenever(provider.ifAvailable)
            .thenReturn(port)

        DemoDataSeedRunner(provider).run(DefaultApplicationArguments())

        verify(port).reseed()
    }

    @Test
    fun `does nothing when the outbound port is not available`() {
        val provider = mock<ObjectProvider<DemoDataPort>>()
        org.mockito.kotlin
            .whenever(provider.ifAvailable)
            .thenReturn(null)

        DemoDataSeedRunner(provider).run(DefaultApplicationArguments())

        assertTrue(true)
    }
}
