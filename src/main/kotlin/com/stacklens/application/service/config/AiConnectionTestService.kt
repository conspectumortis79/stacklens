package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.config.AiConnectionTestUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class AiConnectionTestService(
    private val connectionTest: AiConnectionTestPort,
    private val aiConfigPort: AiConfigPort,
    private val aiDefaults: AiDefaultsPort,
    private val resolvers: List<AiConnectionTargetSpecResolver> = defaultAiConnectionTargetSpecResolvers(aiDefaults),
) : AiConnectionTestUseCase {
    private val resolversByTarget = resolvers.associateBy { it.target }

    override fun testConnection(
        target: AiConnectionTestTarget,
        request: AiConnectionTestRequest,
    ): Map<String, Any> {
        val resolver = resolversByTarget.getValue(target)
        return connectionTest.testConnection(resolver.resolve(request, aiConfigPort.findAiConfig())).payload
    }
}
