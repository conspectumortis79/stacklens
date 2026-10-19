package com.stacklens.application.service.ai

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class CopilotAiCaller(
    private val copilot: CopilotPort,
) : AiCaller {
    override fun execute(
        model: String,
        prompt: String,
        timeoutSec: Long,
    ): String = copilot.execute(model, prompt, timeoutSec)
}
