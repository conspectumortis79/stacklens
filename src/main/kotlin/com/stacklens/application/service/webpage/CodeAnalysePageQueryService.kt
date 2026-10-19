package com.stacklens.application.service.webpage

import com.stacklens.application.port.inbound.web.CodeAnalysePageData
import com.stacklens.application.port.inbound.web.CodeAnalysePageQueryUseCase
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class CodeAnalysePageQueryService(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val gitlabConfigRepo: GitlabConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val demoConfigFilter: DemoConfigPort,
) : CodeAnalysePageQueryUseCase {
    override fun getCodeAnalysePageData(): CodeAnalysePageData =
        CodeAnalysePageData(
            localRepoConfigs = localRepoConfigRepo.findAllByScope("CODE_ANALYSIS"),
            gitlabConfigs = demoConfigFilter.apply(gitlabConfigRepo.findByActiveTrue()) { it.name },
            githubRepoConfigs = demoConfigFilter.apply(githubRepoConfigRepo.findAllGithubRepoConfigs().filter { it.active }) { it.name },
        )
}
