package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.config.DemoUrlRefreshUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class DemoUrlRefreshRunnerTest {
    private val refresh: DemoUrlRefreshUseCase = mock()

    @Test
    fun `run delegates the refresh to the use case`() {
        DemoUrlRefreshRunner(refresh).run()

        verify(refresh).refreshDemoUrlsInBackground()
        verifyNoMoreInteractions(refresh)
    }
}
