package com.stacklens.application.service.config

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class GithubRepoDemoUrlAlignment(
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val aligner: DemoUrlAligner,
) : DemoUrlAlignmentSection {
    override val label: String = "GitHub"

    override fun align(): List<String> =
        aligner.alignUrls(
            entries = githubRepoConfigRepo.findAllGithubRepoConfigs(),
            urlOf = { it.apiBase },
            urlSetter = { config, url -> config.apiBase = url },
            persist = { githubRepoConfigRepo.saveGithubRepoConfig(it) },
        )
}
