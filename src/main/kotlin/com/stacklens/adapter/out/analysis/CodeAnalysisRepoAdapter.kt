
package com.stacklens.adapter.out.analysis

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.analysis.CodeAnalysisRepoData
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.domain.analysis.sourceFilePriority
import com.stacklens.domain.model.GitlabConfig
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class CodeAnalysisRepoAdapter(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val gitlabConfigRepo: GitlabConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val scanService: LocalRepoScanPort,
    private val gitlabService: GitlabPort,
    private val githubBridge: GithubTicketBridgePort,
) : CodeAnalysisRepoApi {
    private val localSummaryCache = ConcurrentHashMap<String, Pair<String, String>>()
    private val gitlabFilesCache = ConcurrentHashMap<Triple<Long, Long, String>, List<Pair<String, String>>>()

    override fun loadLocal(repoConfigId: Long): CodeAnalysisRepoData? {
        val config = localRepoConfigRepo.findLocalRepoConfigById(repoConfigId) ?: return null
        val (tree, contents) =
            localSummaryCache.computeIfAbsent(config.path) {
                val summary = scanService.buildCodeSummary(config.path, maxFiles = 30, maxPerFileSizeKb = 2)
                summary.projectTree to summary.fileContents
            }
        return CodeAnalysisRepoData(config.name, config.path, tree, contents)
    }

    override fun listGitlabFilePaths(
        gitlabConfigId: Long,
        projectId: Long,
    ): List<String> {
        val config = gitlabConfigRepo.findGitlabConfigById(gitlabConfigId) ?: return emptyList()
        return gitlabService.listProjectFilePaths(config, projectId)
    }

    override fun loadGitlab(
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
        subPath: String,
    ): CodeAnalysisRepoData? {
        val config = gitlabConfigRepo.findGitlabConfigById(gitlabConfigId) ?: return null
        val cacheKey = Triple(gitlabConfigId, projectId, subPath)
        val files =
            gitlabFilesCache.computeIfAbsent(cacheKey) {
                if (subPath.isBlank()) {
                    gitlabService.fetchProjectFiles(config, projectId, maxFiles = 25, maxPerFileSizeBytes = 2048)
                } else {
                    gitlabService.fetchProjectFilesForPath(config, projectId, subPath, maxFiles = 25, maxPerFileSizeBytes = 2048)
                }
            }
        val tree = files.joinToString("\n") { it.first }
        val contents = files.joinToString("\n\n") { "=== ${it.first} ===\n${it.second}" }
        val appName = if (subPath.isNotBlank()) subPath.substringAfterLast('/') else projectPath.substringAfterLast("/")
        val displayPath = if (subPath.isNotBlank()) "$projectPath/$subPath" else projectPath
        return CodeAnalysisRepoData(appName, displayPath, tree, contents)
    }

    override fun loadGithub(githubRepoConfigId: Long): CodeAnalysisRepoData {
        val config =
            githubRepoConfigRepo.findAllGithubRepoConfigs().find { it.id == githubRepoConfigId }
                ?: throw IllegalStateException("GitHub repository configuration not found (id=$githubRepoConfigId)")
        val ctx =
            githubBridge.fetchRepoContext(config)
                ?: throw IllegalStateException("GitHub repository ${config.owner}/${config.repo} is not accessible.")
        val sorted = ctx.files.sortedBy { codeFilePriority(it.path) }
        val tree = sorted.joinToString("\n") { it.path }
        val contents = sorted.joinToString("\n\n") { "=== ${it.path} ===\n${it.content.take(2048)}" }
        val appName = "${config.owner}/${config.repo}"
        return CodeAnalysisRepoData(appName, appName, tree, contents)
    }

    override fun findGitlabConfig(gitlabConfigId: Long): GitlabConfig? = gitlabConfigRepo.findGitlabConfigById(gitlabConfigId)

    internal fun codeFilePriority(path: String): Int = sourceFilePriority(path)
}
