package com.stacklens.application.port.out

data class JiraTicketData(
    val key: String,
    val summary: String,
    val description: String,
    val comments: String,
    val issueType: String,
    val status: String,
    val priority: String?,
    val labels: List<String>,
    val components: List<String>,
    val url: String,
    val acceptanceCriteria: List<String> = emptyList(),
)

data class JiraProblemAnalysis(
    val problemDescription: String,
    val rootCause: String,
    val affectedComponents: List<String>,
    val searchKeywords: List<String>,
    val errorMessages: List<String>,
)

data class JiraSimilarTicket(
    val key: String,
    val summary: String,
    val description: String,
    val issueType: String,
    val status: String,
    val url: String,
    val foundViaKeyword: String?,
)

data class SprintInfo(
    val id: Long,
    val name: String,
    val state: String,
    val startDate: String?,
    val endDate: String?,
    val goal: String?,
)

data class LinkedIssue(
    val key: String,
    val summary: String,
    val relationship: String,
    val status: String?,
)

data class SprintIssue(
    val key: String,
    val summary: String,
    val description: String,
    val issueType: String,
    val status: String,
    val priority: String?,
    val assignee: String?,
    val labels: List<String>,
    val comments: String,
    val url: String,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val resolvedAt: String? = null,
    val qaLoopCount: Int = 0,
    val linkedIssues: List<LinkedIssue> = emptyList(),
)

internal object JiraTicketConstants {
    const val MAX_COMMENTS = 10
    const val MAX_SEARCH_KEYWORDS = 6
    const val MAX_SIMILAR_TICKETS = 20
    const val MAX_SIMILAR_TICKETS_FOR_MR = 10
    const val MAX_MRS_PER_TICKET = 3
    const val MAX_TOTAL_MRS = 15
    const val MAX_FILES_PER_MR = 5
    const val MAX_REPO_CONTEXTS = 3
    const val MAX_REPO_FILES_PER_PROJECT = 40
    const val MAX_FILE_CONTENT_CHARS = 500_000
    const val MAX_FILES_FOR_CONTEXT = 120
}
