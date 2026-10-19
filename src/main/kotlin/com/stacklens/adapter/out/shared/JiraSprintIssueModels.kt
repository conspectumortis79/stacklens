package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraSprintIssueItem(
    val key: String = "",
    val fields: JiraSprintIssueFields = JiraSprintIssueFields(),
    val changelog: JiraChangelog? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraChangelog(
    val histories: List<JiraChangelogHistory> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraChangelogHistory(
    val created: String? = null,
    val items: List<JiraChangelogItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraChangelogItem(
    val field: String? = null,
    val fromString: String? = null,
    val toString: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraIssueLinkType(
    val name: String? = null,
    val inward: String? = null,
    val outward: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraLinkedIssueFields(
    val summary: String? = null,
    val status: JiraNamedField? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraLinkedIssueRef(
    val key: String? = null,
    val fields: JiraLinkedIssueFields? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraIssueLink(
    val type: JiraIssueLinkType? = null,
    val inwardIssue: JiraLinkedIssueRef? = null,
    val outwardIssue: JiraLinkedIssueRef? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraSprintIssueFields(
    val summary: String? = null,
    val description: JsonNode? = null,
    val issuetype: JiraNamedField? = null,
    val status: JiraNamedField? = null,
    val priority: JiraNamedField? = null,
    val assignee: JiraAssigneeField? = null,
    val labels: List<String>? = null,
    val comment: JiraCommentContainer? = null,
    val created: String? = null,
    val resolutiondate: String? = null,
    val statuscategorychangedate: String? = null,
    val issuelinks: List<JiraIssueLink>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraNamedField(
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraAssigneeField(
    val displayName: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraCommentContainer(
    val comments: List<JiraComment> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraComment(
    val body: JsonNode? = null,
    val author: JiraAuthor? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraAuthor(
    val displayName: String? = null,
)
