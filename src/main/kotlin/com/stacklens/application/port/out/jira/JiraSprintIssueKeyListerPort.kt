package com.stacklens.application.port.out.jira

import com.stacklens.domain.model.JiraConfig

interface JiraSprintIssueKeyListerPort {
    fun listTopLevelIssueKeys(
        config: JiraConfig,
        sprintId: Long,
        pageSize: Int = 50,
    ): List<String>
}
