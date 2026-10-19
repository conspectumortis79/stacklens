package com.stacklens.adapter.out.jira

import java.time.Instant

data class Sprint(
    val id: Long,
    val name: String,
    val state: String,
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val boardId: Long? = null,
    val originBoardId: Long? = null,
    val goal: String = "",
)

data class SprintIssue(
    val id: Long,
    val key: String,
    val summary: String,
    val status: String,
    val assignee: String? = null,
    val priority: String? = null,
    val storyPoints: Double? = null,
    val issueType: String? = null,
    val labels: List<String> = emptyList(),
    val fixVersions: List<String> = emptyList(),
    val epic: String? = null,
    val parentKey: String? = null,
    val linkedIssues: List<String> = emptyList(),
    val subtaskIds: List<Long> = emptyList(),
)

data class Board(
    val id: Long,
    val name: String,
    val type: String = "",
)
