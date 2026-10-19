package com.stacklens.adapter.out.pipeline

import com.stacklens.adapter.out.shared.RepoScanRules
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.pipeline.PipelineFilesPort
import com.stacklens.application.port.out.pipeline.PipelineRepoData
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class PipelineFilesAdapter(
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val gitlabConfigRepo: GitlabConfigPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val scanService: LocalRepoScanPort,
    private val gitlabService: GitlabPort,
    private val githubBridge: GithubTicketBridgePort,
) : PipelineFilesPort {
    private val localSummaryCache = ConcurrentHashMap<String, Pair<String, String>>()
    private val gitlabFilesCache = ConcurrentHashMap<Pair<Long, Long>, List<Pair<String, String>>>()
    private val scanRules = RepoScanRules()

    override fun loadLocal(repoConfigId: Long): PipelineRepoData? {
        val config = localRepoConfigRepo.findLocalRepoConfigById(repoConfigId) ?: return null
        val (tree, contents) =
            localSummaryCache.computeIfAbsent(config.path) {
                val summary = scanService.buildPipelineSummary(config.path, maxFiles = PIPELINE_MAX_FILES, maxPerFileSizeKb = PIPELINE_MAX_FILE_SIZE_KB)
                summary.projectTree to summary.fileContents
            }
        return PipelineRepoData(config.name, config.path, tree, contents)
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
    ): PipelineRepoData? {
        val config = gitlabConfigRepo.findGitlabConfigById(gitlabConfigId) ?: return null
        val cacheKey = gitlabConfigId to projectId
        val files =
            gitlabFilesCache.computeIfAbsent(cacheKey) {
                gitlabService.fetchProjectFiles(config, projectId, maxFiles = PIPELINE_MAX_FILES, maxPerFileSizeBytes = PIPELINE_MAX_FILE_SIZE_BYTES)
            }
        val pipelineFiles = files.filter { (path, _) -> looksLikePipelinePath(path) }
        val tree = pipelineFiles.joinToString("\n") { it.first }
        val contents = pipelineFiles.joinToString("\n\n") { "=== ${it.first} ===\n${it.second}" }
        val appName = projectPath.substringAfterLast("/")
        return PipelineRepoData(appName, projectPath, tree, contents)
    }

    override fun loadGithub(githubRepoConfigId: Long): PipelineRepoData {
        val config =
            githubRepoConfigRepo.findAllGithubRepoConfigs().find { it.id == githubRepoConfigId }
                ?: throw IllegalStateException("GitHub repository configuration not found (id=$githubRepoConfigId)")
        val ctx =
            githubBridge.fetchRepoContext(config)
                ?: throw IllegalStateException("GitHub repository ${config.owner}/${config.repo} is not accessible.")
        val pipelineFiles =
            ctx.files.filter { looksLikePipelinePath(it.path) }.sortedBy { it.path.lowercase() }
        val tree = pipelineFiles.joinToString("\n") { it.path }
        val contents = pipelineFiles.joinToString("\n\n") { "=== ${it.path} ===\n${it.content.take(PIPELINE_MAX_FILE_SIZE_BYTES)}" }
        val appName = "${config.owner}/${config.repo}"
        return PipelineRepoData(appName, appName, tree, contents)
    }

    internal fun looksLikePipelinePath(path: String): Boolean = scanRules.isPipelineFile(path)

    internal companion object {
        const val PIPELINE_MAX_FILES: Int = 30

        const val PIPELINE_MAX_FILE_SIZE_KB: Int = 16

        const val PIPELINE_MAX_FILE_SIZE_BYTES: Int = PIPELINE_MAX_FILE_SIZE_KB * 1024

        val PIPELINE_BASENAMES: Set<String> = RepoScanRules.DEFAULT_PIPELINE_FILE_NAMES
    }
}
