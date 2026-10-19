package com.stacklens.application.port.out

import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig

interface MrSuggestionPort {
    data class PostRequest(
        val mrUrl: String,
        val platform: String,
        val gitConfigId: Long?,
        val gitlabConfigs: List<GitlabConfig>,
        val githubConfigs: List<GithubRepoConfig>,
        val diffBaseSha: String,
        val diffStartSha: String,
        val diffHeadSha: String,
        val filePath: String,
        val lineNumber: Int,
        val suggestedCode: String,
    )

    fun postSuggestion(request: PostRequest): String
}
