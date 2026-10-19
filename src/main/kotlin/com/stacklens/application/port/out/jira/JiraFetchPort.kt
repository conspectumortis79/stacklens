package com.stacklens.application.port.out.jira

import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.domain.model.JiraConfig

interface JiraTicketFetchPort {
    fun fetchTicketByKey(
        config: JiraConfig,
        key: String,
    ): JiraTicketData

    fun searchSimilarTickets(
        config: JiraConfig,
        originalTicket: JiraTicketData,
        problem: JiraProblemAnalysis,
    ): List<JiraSimilarTicket>
}

interface JiraSprintFetchPort {
    fun fetchSprintInfo(
        config: JiraConfig,
        sprintUrl: String,
    ): SprintInfo

    fun fetchSprintIssues(
        config: JiraConfig,
        sprintUrl: String,
    ): List<SprintIssue>

    fun fetchSprintData(
        config: JiraConfig,
        sprintUrl: String,
    ): Pair<SprintInfo, List<SprintIssue>>
}
