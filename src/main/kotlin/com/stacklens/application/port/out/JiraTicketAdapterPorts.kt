package com.stacklens.application.port.out

import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import java.nio.file.Path

interface GithubTicketBridgePort {
    fun fetchRepoContext(config: GithubRepoConfig): JiraRepoContext?

    fun createPullRequest(
        config: GithubRepoConfig,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
    ): String

    fun detectGithubRemote(localPath: Path): GithubRepoConfig?
}

interface GitlabTicketBridgePort {
    fun fetchRelatedMrs(
        gitlabConfigs: List<GitlabConfig>,
        similarTickets: List<JiraSimilarTicket>,
        problem: JiraProblemAnalysis,
        originalTicket: JiraTicketData,
        selectedProjects: Map<Long, Set<Long>> = emptyMap(),
    ): List<JiraRelatedMr>

    fun findProjectById(
        config: GitlabConfig,
        gitlabProjectId: Long,
    ): GitlabProjectInfo?

    fun fetchRepoContextForCodeGen(
        gitlabConfigs: List<GitlabConfig>,
        ticket: JiraTicketData,
        problem: JiraProblemAnalysis,
        relatedMrs: List<JiraRelatedMr>,
        selectedProjects: Map<Long, Set<Long>> = emptyMap(),
    ): List<JiraRepoContext>

    fun createMergeRequest(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long? = null,
        monorepoInfo: JiraMonorepoIdentification? = null,
    ): String

    fun commitDirectly(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long? = null,
        monorepoInfo: JiraMonorepoIdentification? = null,
    ): String

    fun detectGitlabRemote(
        localPath: Path,
        gitlabConfigs: List<GitlabConfig>,
    ): Pair<GitlabConfig, Long?>?

    fun getGitRemotePathWithNamespace(localPath: Path): String?
}
