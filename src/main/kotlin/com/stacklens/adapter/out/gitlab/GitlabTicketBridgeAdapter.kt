package com.stacklens.adapter.out.gitlab

import com.stacklens.application.port.out.GitlabTicketBridgePort
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraMonorepoIdentification
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraRelatedMr
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketConstants.MAX_MRS_PER_TICKET
import com.stacklens.application.port.out.JiraTicketConstants.MAX_REPO_CONTEXTS
import com.stacklens.application.port.out.JiraTicketConstants.MAX_SIMILAR_TICKETS_FOR_MR
import com.stacklens.application.port.out.JiraTicketConstants.MAX_TOTAL_MRS
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.shared.gitRemotePathWithNamespace
import com.stacklens.shared.gitRemoteUrl
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class GitlabTicketBridgeAdapter(
    private val projectClient: GitlabProjectApiClient = GitlabProjectApiClient(),
) : GitlabTicketBridgePort {
    private val log = LoggerFactory.getLogger(GitlabTicketBridgeAdapter::class.java)

    private val mrClient = GitlabMergeRequestApiClient()
    private val repoFileClient = GitlabRepoFileApiClient()
    private val branchCommitClient = GitlabBranchCommitApiClient()

    override fun fetchRelatedMrs(
        gitlabConfigs: List<GitlabConfig>,
        similarTickets: List<JiraSimilarTicket>,
        problem: JiraProblemAnalysis,
        originalTicket: JiraTicketData,
        selectedProjects: Map<Long, Set<Long>>,
    ): List<JiraRelatedMr> {
        val results = mutableListOf<JiraRelatedMr>()
        val seenUrls = mutableSetOf<String>()

        for (config in gitlabConfigs) {
            val (apiBase, effectiveGroup) = config.resolvedUrls()
            val client = projectClient.buildClient(config, apiBase)
            val allProjects = projectClient.fetchProjects(client, effectiveGroup)
            val projects =
                selectedProjects[config.id]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { ids -> allProjects.filter { it.id in ids } }
                    ?: allProjects
            if (projects.isEmpty()) continue

            for (ticket in similarTickets.take(MAX_SIMILAR_TICKETS_FOR_MR)) {
                if (results.size >= MAX_TOTAL_MRS) break
                for (project in projects) {
                    if (results.size >= MAX_TOTAL_MRS) break
                    val mrs = mrClient.searchMrs(client, project.id, ticket.key)
                    for (mr in mrs.take(MAX_MRS_PER_TICKET)) {
                        val mrUrl = "$apiBase/${project.pathWithNamespace}/-/merge_requests/${mr.iid}"
                        if (!seenUrls.add(mrUrl)) continue
                        val changes = mrClient.fetchMrChanges(client, project.id, mr.iid)
                        results += mrClient.toRelatedMr(mr, project, apiBase, changes, foundViaTicketKey = ticket.key)
                    }
                }
            }

            if (results.size < MAX_TOTAL_MRS) {
                for (project in projects) {
                    if (results.size >= MAX_TOTAL_MRS) break
                    val mrs = mrClient.searchMrs(client, project.id, originalTicket.key)
                    for (mr in mrs.take(MAX_MRS_PER_TICKET)) {
                        val mrUrl = "$apiBase/${project.pathWithNamespace}/-/merge_requests/${mr.iid}"
                        if (!seenUrls.add(mrUrl)) continue
                        val changes = mrClient.fetchMrChanges(client, project.id, mr.iid)
                        results += mrClient.toRelatedMr(mr, project, apiBase, changes, foundViaTicketKey = originalTicket.key)
                    }
                }
            }

            for (keyword in problem.searchKeywords.take(3)) {
                if (results.size >= MAX_TOTAL_MRS) break
                for (project in projects.take(10)) {
                    if (results.size >= MAX_TOTAL_MRS) break
                    val mrs = mrClient.searchMrs(client, project.id, keyword, state = "merged")
                    for (mr in mrs.take(2)) {
                        val mrUrl = "$apiBase/${project.pathWithNamespace}/-/merge_requests/${mr.iid}"
                        if (!seenUrls.add(mrUrl)) continue
                        val changes = mrClient.fetchMrChanges(client, project.id, mr.iid)
                        results += mrClient.toRelatedMr(mr, project, apiBase, changes, foundViaKeyword = keyword)
                    }
                }
            }
        }
        return results
    }

    override fun findProjectById(
        config: GitlabConfig,
        gitlabProjectId: Long,
    ): GitlabProjectInfo? = projectClient.findProjectById(config, gitlabProjectId)

    override fun fetchRepoContextForCodeGen(
        gitlabConfigs: List<GitlabConfig>,
        ticket: JiraTicketData,
        problem: JiraProblemAnalysis,
        relatedMrs: List<JiraRelatedMr>,
        selectedProjects: Map<Long, Set<Long>>,
    ): List<JiraRepoContext> {
        if (gitlabConfigs.isEmpty()) return emptyList()

        val candidates = mutableSetOf<String>()
        ticket.components.forEach { candidates += it.trim().lowercase() }
        problem.affectedComponents.forEach { candidates += it.trim().lowercase() }
        relatedMrs.forEach { mr ->
            candidates +=
                mr.projectPath
                    .substringAfterLast('/')
                    .trim()
                    .lowercase()
        }
        val hasExplicitSelection = selectedProjects.values.any { it.isNotEmpty() }
        if (candidates.isEmpty() && !hasExplicitSelection) return emptyList()

        val result = mutableListOf<JiraRepoContext>()

        for (config in gitlabConfigs) {
            if (result.size >= MAX_REPO_CONTEXTS) break
            val (apiBase, effectiveGroup) = config.resolvedUrls()
            val client = projectClient.buildClient(config, apiBase)
            val allProjects = projectClient.fetchProjects(client, effectiveGroup)
            val explicitIds = selectedProjects[config.id]?.takeIf { it.isNotEmpty() }

            if (explicitIds != null) {
                val groupProjects = allProjects.associateBy { it.id }
                for (projectId in explicitIds) {
                    if (result.size >= MAX_REPO_CONTEXTS) break
                    if (result.any { it.projectId == projectId }) continue
                    val project =
                        groupProjects[projectId] ?: projectClient.fetchProjectDtoById(client, projectId) ?: run {
                            log.warn("[TicketImplementation] Projekt {} not reachable in config '{}'", projectId, config.name)
                            continue
                        }
                    val files = repoFileClient.fetchRepoFiles(client, project.id, mrClient::isRelevantSourceFile)
                    if (files.isNotEmpty()) {
                        result += JiraRepoContext(repoName = project.name, projectId = project.id, files = files)
                        log.info("[TicketImplementation] Repo-Kontext (explizit) loaded: {} ({} files)", project.name, files.size)
                    } else {
                        log.warn(
                            "[TicketImplementation] Projekt {} (ID {}): 0 source files detected via API tree",
                            project.name,
                            project.id,
                        )
                    }
                }
            } else {
                if (candidates.isEmpty()) continue
                for (candidate in candidates) {
                    if (result.size >= MAX_REPO_CONTEXTS) break
                    val project =
                        allProjects.firstOrNull { p ->
                            p.name.trim().lowercase() == candidate ||
                                p.pathWithNamespace
                                    .substringAfterLast('/')
                                    .trim()
                                    .lowercase() == candidate
                        } ?: continue
                    if (result.any { it.projectId == project.id }) continue
                    val files = repoFileClient.fetchRepoFiles(client, project.id, mrClient::isRelevantSourceFile)
                    if (files.isNotEmpty()) {
                        result += JiraRepoContext(repoName = project.name, projectId = project.id, files = files)
                        log.info("[TicketImplementation] Repo context loaded: {} ({} files)", project.name, files.size)
                    }
                }
            }
        }
        return result
    }

    override fun detectGitlabRemote(
        localPath: java.nio.file.Path,
        gitlabConfigs: List<GitlabConfig>,
    ): Pair<GitlabConfig, Long?>? {
        if (gitlabConfigs.isEmpty()) return null
        return try {
            val remoteUrl = gitRemoteUrl(localPath.toFile()) ?: return null

            log.debug("[GitlabBridge] Remote URL for {}: {}", localPath, remoteUrl)

            val pathWithNamespace = gitRemotePathWithNamespace(remoteUrl) ?: return null

            for (config in gitlabConfigs) {
                val configHost =
                    config.baseUrl
                        .trimEnd('/')
                        .removePrefix("https://")
                        .removePrefix("http://")
                if (remoteUrl.contains(configHost, ignoreCase = true)) {
                    val (apiBase, groupId) = config.resolvedUrls()
                    val client = projectClient.buildClient(config, apiBase)

                    var projectId =
                        runCatching {
                            val encoded = java.net.URLEncoder.encode(pathWithNamespace, "UTF-8")
                            val node =
                                client
                                    .get()
                                    .uri("/api/v4/projects/$encoded")
                                    .retrieve()
                                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode::class.java)
                                    .onErrorResume { Mono.empty() }
                                    .block()
                            node?.path("id")?.asLong()?.takeIf { it > 0 }
                        }.getOrNull()

                    if (projectId == null) {
                        val repoSlug = pathWithNamespace.substringAfterLast('/').lowercase()
                        log.debug(
                            "[GitlabBridge] Path-lookup failed for '{}', falling back to name search ('{}') in group",
                            pathWithNamespace,
                            repoSlug,
                        )
                        projectId =
                            runCatching {
                                projectClient
                                    .fetchProjects(client, groupId)
                                    .firstOrNull { p ->
                                        p.pathWithNamespace.substringAfterLast('/').lowercase() == repoSlug ||
                                            p.name.lowercase() == repoSlug
                                    }?.id
                            }.getOrNull()
                    }

                    log.debug(
                        "[GitlabBridge] GitLab remote detected for {}: config='{}' project='{}' id={}",
                        localPath,
                        config.name,
                        pathWithNamespace,
                        projectId,
                    )
                    return Pair(config, projectId)
                }
            }
            null
        } catch (e: Exception) {
            log.debug("[GitlabBridge] GitLab remote detection failed for {}: {}", localPath, e.message)
            null
        }
    }

    override fun getGitRemotePathWithNamespace(localPath: java.nio.file.Path): String? =
        gitRemoteUrl(localPath.toFile())
            ?.let { gitRemotePathWithNamespace(it) }
            .also { log.debug("[GitlabBridge] git remote path-with-namespace for {}: {}", localPath, it) }

    override fun createMergeRequest(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long?,
        monorepoInfo: JiraMonorepoIdentification?,
    ): String = branchCommitClient.createMergeRequest(config, repoName, ticket, codeImpl, gitlabProjectId, monorepoInfo)

    override fun commitDirectly(
        config: GitlabConfig,
        repoName: String,
        ticket: JiraTicketData,
        codeImpl: JiraCodeImplementation,
        gitlabProjectId: Long?,
        monorepoInfo: JiraMonorepoIdentification?,
    ): String = branchCommitClient.commitDirectly(config, repoName, ticket, codeImpl, gitlabProjectId, monorepoInfo)
}
