package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.ReviewAnalysePageData
import com.stacklens.application.port.inbound.web.ReviewPageQueryUseCase
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class ReviewPageQueryService(
    private val jiraConfigRepo: JiraConfigPort,
    private val reviewAnalysisConfigRepo: ReviewAnalysisConfigPort,
    private val demoConfigFilter: DemoConfigPort,
) : ReviewPageQueryUseCase {
    override fun getReviewAnalysePageData(): ReviewAnalysePageData =
        ReviewAnalysePageData(
            jiraConfigs = demoConfigFilter.apply(jiraConfigRepo.findByActiveTrue()) { it.name },
            savedConfig = reviewAnalysisConfigRepo.findReviewAnalysisConfig(),
        )
}
