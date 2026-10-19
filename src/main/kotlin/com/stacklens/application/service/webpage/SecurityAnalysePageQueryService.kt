package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.SecurityAnalysePageData
import com.stacklens.application.port.inbound.web.SecurityAnalysePageQueryUseCase
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class SecurityAnalysePageQueryService(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val gitlabConfigRepo: GitlabConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val demoConfigFilter: DemoConfigPort,
) : SecurityAnalysePageQueryUseCase {
    override fun getSecurityAnalysePageData(): SecurityAnalysePageData =
        SecurityAnalysePageData(
            localRepoConfigs = localRepoConfigRepo.findAllByScope("BUG_ANALYSIS"),
            gitlabConfigs = demoConfigFilter.apply(gitlabConfigRepo.findByActiveTrue()) { it.name },
            githubRepoConfigs = demoConfigFilter.apply(githubRepoConfigRepo.findAllGithubRepoConfigs()) { it.name },
        )
}
