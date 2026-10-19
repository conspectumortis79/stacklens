package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.PipelineAnalysePageData
import com.stacklens.application.port.inbound.web.PipelineAnalysePageQueryUseCase
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class PipelineAnalysePageQueryService(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val gitlabConfigRepo: GitlabConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val demoConfigFilter: DemoConfigPort,
) : PipelineAnalysePageQueryUseCase {
    override fun getPipelineAnalysePageData(): PipelineAnalysePageData =
        PipelineAnalysePageData(
            localRepoConfigs = localRepoConfigRepo.findAllByScope("PIPELINE_ANALYSIS"),
            gitlabConfigs = demoConfigFilter.apply(gitlabConfigRepo.findByActiveTrue()) { it.name },
            githubRepoConfigs = demoConfigFilter.apply(githubRepoConfigRepo.findAllGithubRepoConfigs().filter { it.active }) { it.name },
        )
}
