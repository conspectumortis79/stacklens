package com.stacklens.application.port.out

import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig

interface MrFetchPort {
    fun fetchMrData(
        mrUrl: String,
        gitlabConfigs: List<GitlabConfig>,
        githubConfigs: List<GithubRepoConfig>,
    ): MrFetchResult

    sealed class MrFetchResult {
        data class Success(
            val data: MrData,
        ) : MrFetchResult()

        data class Failure(
            val reason: String,
        ) : MrFetchResult()
    }

    data class MrData(
        val mrUrl: String,
        val platform: String,
        val title: String,
        val description: String,
        val sourceBranch: String,
        val targetBranch: String,
        val authorName: String,
        val state: String,
        val diffText: String,
        val changedFiles: List<String>,
        val diffBaseSha: String = "",
        val diffStartSha: String = "",
        val diffHeadSha: String = "",
    )
}
