package com.stacklens.application.service.tooling

import com.stacklens.application.port.inbound.tooling.ConfluenceConfigSummary
import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.service.AnalysisConstants
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.ConfluenceTool

@ApplicationService
class ToolingReadService(
    private val toolRepo: ConfluenceToolPort,
    private val confluenceConfigRepo: ConfluenceConfigPort,
) : ToolingReadUseCase {
    override fun getTool(toolId: Long): ConfluenceTool? = toolRepo.findConfluenceToolById(toolId)

    override fun getAllTools(): List<ConfluenceTool> = toolRepo.findRecentConfluenceTools(AnalysisConstants.MAX_RECENT_TOOLS)

    override fun listActiveConfluenceConfigs(): List<ConfluenceConfigSummary> = confluenceConfigRepo.findByActiveTrue().map { ConfluenceConfigSummary(it.id, it.name, it.baseUrl) }
}
