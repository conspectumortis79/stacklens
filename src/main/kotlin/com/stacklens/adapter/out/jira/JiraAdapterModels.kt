package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraCommentContainer

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraIssueResponse(
    val key: String = "",
    val fields: JiraIssueFields = JiraIssueFields(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraIssueFields(
    val summary: String? = null,
    val description: JsonNode? = null,
    val issuetype: JiraNamedValue? = null,
    val status: JiraNamedValue? = null,
    val priority: JiraNamedValue? = null,
    val labels: List<String>? = null,
    val components: List<JiraComponentDto>? = null,
    val comment: JiraCommentContainer? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraComponentDto(
    val name: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraNamedValue(
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraAdfNode(
    val type: String = "",
    val text: String? = null,
    val content: List<JiraAdfNode> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraSearchResponse(
    val issues: List<JiraIssueRef> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraIssueRef(
    val key: String = "",
    val fields: JiraIssueFields = JiraIssueFields(),
)
