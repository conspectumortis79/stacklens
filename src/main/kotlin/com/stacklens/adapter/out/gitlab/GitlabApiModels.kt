package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitlabProject(
    val id: Long,
    val name: String,
    @JsonProperty("path_with_namespace") val pathWithNamespace: String,
    @JsonProperty("default_branch") val defaultBranch: String? = "main",
    @JsonProperty("http_url_to_repo") val httpUrlToRepo: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitlabBlobResult(
    @JsonProperty("project_id") val projectId: Long = 0,
    val path: String = "",
    val ref: String = "",
    val startline: Int = 0,
    val data: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TreeEntry(
    val id: String = "",
    val name: String = "",
    val path: String = "",
    val type: String = "",
)

data class ParsedContext(
    val className: String?,
    val functionName: String?,
    val resultType: com.stacklens.domain.enums.ResultType,
)
