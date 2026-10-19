package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraBridgeGitlabProjectDto(
    val id: Long = 0,
    val name: String = "",
    @JsonProperty("path_with_namespace") val pathWithNamespace: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraBridgeGitlabProjectDetailDto(
    val id: Long = 0,
    @JsonProperty("default_branch") val defaultBranch: String = "main",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraBridgeGitlabMrDto(
    val id: Long = 0,
    val iid: Long = 0,
    val title: String = "",
    val description: String? = null,
    val state: String = "",
    @JsonProperty("web_url") val webUrl: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraBridgeGitlabMrChangesResponse(
    val changes: List<JiraBridgeGitlabFileChange> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitlabTreeEntry(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val path: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JiraBridgeGitlabFileChange(
    @JsonProperty("new_path") val newPath: String = "",
    @JsonProperty("old_path") val oldPath: String = "",
    val diff: String = "",
)
