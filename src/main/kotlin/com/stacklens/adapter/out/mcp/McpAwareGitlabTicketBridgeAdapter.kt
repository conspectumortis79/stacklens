
package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GitlabTicketBridgePort
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraMonorepoIdentification
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraRelatedMr
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.nio.file.Path
import java.util.function.Function

@Primary
@Component("mcpAwareGitlabTicketBridgePort")
open class McpAwareGitlabTicketBridgeAdapter(
    mcpRepo: McpServerConfigPort,
    gateway: McpGatewayPort,
    objectMapper: ObjectMapper,
    @Qualifier("gitlabTicketBridgePort")
    private val delegate: GitlabTicketBridgePort,
    @Qualifier("gitlabTicketBridgePortFactory")
    private val portFactory: Function<WebClient.Builder, GitlabTicketBridgePort>,
    connectTimeoutMs: Int = 30_000,
    readTimeoutMs: Int = 60_000,
) : GitlabTicketBridgePort {
    private val routed: GitlabTicketBridgePort =
        portFactory.apply(
            mcpRoutedWebClientBuilder(
                mcpRepo,
                gateway,
                objectMapper,
                "gitlab_api_proxy",
                { McpRouting.gitlabReady(it) },
                connectTimeoutMs,
                readTimeoutMs,
            ),
        )

    override fun fetchRelatedMrs(
        gitlabConfigs: List<GitlabConfig>,
        similarTickets: List<JiraSimilarTicket>,
        problem: JiraProblemAnalysis,
        originalTicket: JiraTicketData,
        selectedProjects: Map<Long, Set<Long>>,
    ): List<JiraRelatedMr> = routed.fetchRelatedMrs(gitlabConfigs, similarTickets, problem, originalTicket, selectedProjects)

    override fun findProjectById(
        config: GitlabConfig,
        gitlabProjectId: Long,
    ): GitlabProjectInfo? = routed.findProjectById(config, gitlabProjectId)

    override fun fetchRepoContextForCodeGen(
        gitlabConfigs: List<GitlabConfig>,
        ticket: JiraTicketData,
        problem: JiraProblemAnalysis,
        relatedMrs: List<JiraRelatedMr>,
        selectedProjects: Map<Long, Set<Long>>,
    ): List<JiraRepoContext> = routed.fetchRepoContextForCodeGen(gitlabConfigs, ticket, problem, relatedMrs, selectedProjects)

    override fun createMergeRequest(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long?,
        monorepoInfo: JiraMonorepoIdentification?,
    ): String = routed.createMergeRequest(config, repoName, ticket, codeImpl, gitlabProjectId, monorepoInfo)

    override fun commitDirectly(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long?,
        monorepoInfo: JiraMonorepoIdentification?,
    ): String = routed.commitDirectly(config, repoName, ticket, codeImpl, gitlabProjectId, monorepoInfo)

    override fun detectGitlabRemote(
        localPath: Path,
        gitlabConfigs: List<GitlabConfig>,
    ): Pair<GitlabConfig, Long?>? = delegate.detectGitlabRemote(localPath, gitlabConfigs)

    override fun getGitRemotePathWithNamespace(localPath: Path): String? = delegate.getGitRemotePathWithNamespace(localPath)
}
