package com.stacklens.application.service.config

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class JiraDemoUrlAlignment(
    private val jiraConfigRepo: JiraConfigPort,
    private val aligner: DemoUrlAligner,
) : DemoUrlAlignmentSection {
    override val label: String = "Jira"

    override fun align(): List<String> =
        aligner.alignUrls(
            entries = jiraConfigRepo.findAllJiraConfigs(),
            urlOf = { it.baseUrl },
            urlSetter = { config, url -> config.baseUrl = url },
            persist = { jiraConfigRepo.saveJiraConfig(it) },
        )
}
