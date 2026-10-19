package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.LoggingAnalysePageData
import com.stacklens.application.port.inbound.web.LoggingAnalysePageQueryUseCase
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class LoggingAnalysePageQueryService(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val loggingFixPort: LoggingFixPort,
) : LoggingAnalysePageQueryUseCase {
    override fun getLoggingAnalysePageData(): LoggingAnalysePageData =
        LoggingAnalysePageData(
            localRepoConfigs = localRepoConfigRepo.findAllByScope("LOGGING_ANALYSIS").filter { it.active },
            recentAnalyses = loggingFixPort.findRecentLoggingFixAnalyses(10),
        )
}
