package com.stacklens.application.service

import com.stacklens.application.port.out.ai.AiTokenUsagePort
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class KiChatUsageServiceTest {
    private val usagePort: AiTokenUsagePort = mock()
    private val service = KiChatUsageService(usagePort)

    @Test
    fun `clearUsage deletes only the KI chat aggregate`() {
        service.clearUsage(42L)

        verify(usagePort).deleteAll("kichat", 42L)
    }

    @Test
    fun `clearUsage ignores the zero boundary`() {
        service.clearUsage(0L)

        verify(usagePort, never()).deleteAll(any(), any())
    }

    @Test
    fun `clearUsage ignores negative ids`() {
        service.clearUsage(-1L)

        verify(usagePort, never()).deleteAll(any(), any())
    }
}
