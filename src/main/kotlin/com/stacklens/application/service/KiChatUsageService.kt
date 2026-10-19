package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.KiChatUsageUseCase
import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.AiUsageKind

@ApplicationService
class KiChatUsageService(
    private val usagePort: AiTokenUsagePort,
) : KiChatUsageUseCase {
    override fun clearUsage(chatId: Long) {
        if (chatId <= 0) return
        usagePort.deleteAll(AiUsageKind.KICHAT.id, chatId)
    }
}
