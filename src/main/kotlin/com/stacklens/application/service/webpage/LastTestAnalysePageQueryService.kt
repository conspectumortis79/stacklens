package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.LastTestAnalysePageData
import com.stacklens.application.port.inbound.web.LastTestAnalysePageQueryUseCase
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class LastTestAnalysePageQueryService(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val demoConfigFilter: DemoConfigPort,
) : LastTestAnalysePageQueryUseCase {
    override fun getLastTestAnalysePageData(): LastTestAnalysePageData =
        LastTestAnalysePageData(
            gitlabConfigs = demoConfigFilter.apply(gitlabConfigRepo.findByActiveTrue()) { it.name },
            localRepoConfigs = localRepoConfigRepo.findAllByScope("LOAD_TEST_ANALYSIS"),
            githubRepoConfigs = demoConfigFilter.apply(githubRepoConfigRepo.findAllGithubRepoConfigs().filter { it.active }) { it.name },
        )
}
