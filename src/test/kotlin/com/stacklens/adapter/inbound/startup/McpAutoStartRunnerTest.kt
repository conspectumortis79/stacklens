package com.stacklens.adapter.inbound.startup

import com.stacklens.application.port.inbound.mcp.McpStartupUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class McpAutoStartRunnerTest {
    private val startup: McpStartupUseCase = mock()

    @Test
    fun `run delegates to the auto-start use case`() {
        McpAutoStartRunner(startup).run()

        verify(startup).autoStartManagedServerInBackground()
        verifyNoMoreInteractions(startup)
    }

    @Test
    fun `command line arguments are ignored by the trigger`() {
        McpAutoStartRunner(startup).run("arg1", "arg2")

        verify(startup).autoStartManagedServerInBackground()
    }
}
