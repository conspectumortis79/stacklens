package com.stacklens.application.service.config

import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class GitlabDemoUrlAlignment(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val aligner: DemoUrlAligner,
) : DemoUrlAlignmentSection {
    override val label: String = "GitLab"

    override fun align(): List<String> =
        aligner.alignUrls(
            entries = gitlabConfigRepo.findAllGitlabConfigs(),
            urlOf = { it.baseUrl },
            urlSetter = { config, url -> config.baseUrl = url },
            persist = { gitlabConfigRepo.saveGitlabConfig(it) },
        )
}
