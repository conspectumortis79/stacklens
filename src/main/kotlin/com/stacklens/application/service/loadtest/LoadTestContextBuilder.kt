package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalDirectoryScanPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import org.slf4j.LoggerFactory
import java.util.Collections

data class LoadTestAnalysisContext(
    val projectName: String,
    val projectPath: String,
    val tree: String,
    val contents: String,
    val dockerContents: String,
    val routeContents: String,
)

class LoadTestContextBuilder(
    private val gitlabConfigRepo: GitlabConfigPort,
    private val gitlabService: GitlabPort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val scanService: LocalRepoScanPort,
    private val localDirectoryScan: LocalDirectoryScanPort,
    private val githubRepoConfigRepo: GithubRepoConfigPort,
    private val githubBridge: GithubTicketBridgePort,
    private val copilotPort: CopilotPort,
    private val i18n: I18nPort,
) {
    private val log = LoggerFactory.getLogger(LoadTestContextBuilder::class.java)

    private val gitlabFilesCache: MutableMap<Pair<Long, Long>, List<Pair<String, String>>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<Pair<Long, Long>, List<Pair<String, String>>>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Long>, List<Pair<String, String>>>?): Boolean = size > 64
            },
        )

    fun buildGitlabContext(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
    ): LoadTestAnalysisContext {
        if (!copilotPort.isReady()) throw IllegalStateException(i18n.t("load_test.be_no_github_token"))

        val config =
            gitlabConfigRepo.findGitlabConfigById(gitlabConfigId)
                ?: throw IllegalStateException(i18n.t("load_test.be_gitlab_config_not_found", gitlabConfigId))

        val cacheKey = Pair(gitlabConfigId, projectId)
        val files =
            gitlabFilesCache.computeIfAbsent(cacheKey) {
                gitlabService.fetchProjectFiles(config, projectId, maxFiles = 30, maxPerFileSizeBytes = 3_072)
            }
        val tree = files.joinToString("\n") { it.first }
        val contents = files.joinToString("\n\n") { "=== ${it.first} ===\n${it.second}" }
        val projectName = projectPath.substringAfterLast("/")

        val (dockerContents, routeContents) = splitGitlabFiles(files, i18n)
        log.debug("[LoadTestAnalysis {}] Gitlab context built ({} files)", analysisId, files.size)
        return LoadTestAnalysisContext(
            projectName = projectName,
            projectPath = projectPath,
            tree = tree,
            contents = contents,
            dockerContents = dockerContents,
            routeContents = routeContents,
        )
    }

    fun buildLocalContext(
        analysisId: Long,
        repoConfigId: Long,
    ): LoadTestAnalysisContext {
        if (!copilotPort.isReady()) throw IllegalStateException(i18n.t("load_test.be_no_github_token"))

        val config =
            localRepoConfigRepo.findLocalRepoConfigById(repoConfigId)
                ?: throw IllegalStateException(i18n.t("load_test.be_local_config_not_found", repoConfigId))

        val summary = scanService.buildCodeSummary(config.path, maxFiles = 40, maxPerFileSizeKb = 4)
        val tree = summary.projectTree
        val contents = summary.fileContents
        val sourceFiles = localSourceFiles(config.path)
        val dockerContents = LoadTestFileClassifier.dockerContents(sourceFiles, i18n)
        val routeContents = LoadTestFileClassifier.routeContentsByName(sourceFiles, i18n)
        log.debug("[LoadTestAnalysis {}] Local context built ({} files)", analysisId, sourceFiles.size)
        return LoadTestAnalysisContext(
            projectName = config.name,
            projectPath = config.path,
            tree = tree,
            contents = contents,
            dockerContents = dockerContents,
            routeContents = routeContents,
        )
    }

    fun localSourceFiles(repoPath: String): List<LoadTestSourceFile> =
        localDirectoryScan
            .textFiles(
                root = java.io.File(repoPath),
                accept = { scanned ->
                    !scanned.file.path.contains("node_modules") && !scanned.file.path.contains("/.git/")
                },
                maxContentChars = LOCAL_FILE_CONTENT_CHARS,
                limit = LOCAL_FILE_LIMIT,
            ).map { LoadTestSourceFile(matchPath = it.path.file.path, displayPath = it.path.relativePath, content = it.content) }

    fun buildGithubContext(
        analysisId: Long,
        githubRepoConfigId: Long,
    ): LoadTestAnalysisContext {
        if (!copilotPort.isReady()) throw IllegalStateException(i18n.t("load_test.be_no_github_token"))

        val config =
            githubRepoConfigRepo.findGithubRepoConfigById(githubRepoConfigId)
                ?: throw IllegalStateException("GitHub repository configuration not found (id=$githubRepoConfigId)")

        val bridgeCtx =
            githubBridge.fetchRepoContext(config)
                ?: throw IllegalStateException("GitHub repository ${config.owner}/${config.repo} is not accessible.")

        val files = bridgeCtx.files
        val tree = files.joinToString("\n") { it.path }
        val contents = files.joinToString("\n\n") { "=== ${it.path} ===\n${it.content.take(2_048)}" }
        val projectName = "${config.owner}/${config.repo}"

        val sourceFiles = files.map { LoadTestSourceFile.of(it.path, it.content) }
        val dockerContents = LoadTestFileClassifier.dockerContents(sourceFiles, i18n)
        val routeContents = LoadTestFileClassifier.routeContents(sourceFiles, i18n)
        log.debug("[LoadTestAnalysis {}] GitHub context built ({} files)", analysisId, files.size)
        return LoadTestAnalysisContext(
            projectName = projectName,
            projectPath = projectName,
            tree = tree,
            contents = contents,
            dockerContents = dockerContents,
            routeContents = routeContents,
        )
    }

    fun splitGitlabFiles(
        files: List<Pair<String, String>>,
        i18n: I18nPort,
    ): Pair<String, String> {
        val sourceFiles = files.map { LoadTestSourceFile.of(it.first, it.second) }
        val docker = LoadTestFileClassifier.dockerContents(sourceFiles, i18n)
        val route = LoadTestFileClassifier.routeContents(sourceFiles, i18n)
        return docker to route
    }

    private companion object {
        const val LOCAL_FILE_CONTENT_CHARS: Int = 3_072
        const val LOCAL_FILE_LIMIT: Int = Int.MAX_VALUE
    }
}
