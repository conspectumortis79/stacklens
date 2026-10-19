
package com.stacklens.application.service

import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.out.async.AnalysisOptions
import com.stacklens.application.port.out.async.CodeAnalysisTarget
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.codeanalysis.CodeAnalysisExecutor
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeAnalysis
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@ApplicationService
class CodeAnalysisService(
    private val deps: CodeAnalysisServiceDeps,
    private val clock: ClockPort,
    private val executor: CodeAnalysisExecutor,
) : CodeAnalysisUseCase,
    StaleWorkHealer {
    private val analysisRepo get() = deps.analysisRepo
    private val codeRepoPort get() = deps.codeRepoPort
    private val copilotCliService get() = deps.copilotCliService
    private val i18n get() = deps.i18n
    private val startupHealer get() = deps.startupHealer
    private val monorepoDetector get() = deps.monorepoDetector
    private val mrService get() = deps.mrService
    private val asyncExecutor get() = deps.asyncExecutor
    private val cancellationService get() = deps.cancellationService

    private val log = LoggerFactory.getLogger(CodeAnalysisService::class.java)

    private val gitlabPathsCache = ConcurrentHashMap<Pair<Long, Long>, List<String>>()

    override fun heal() {
        resetStaleAnalyses()
    }

    fun resetStaleAnalyses() =
        startupHealer.heal(
            logTag = "Code-Analyse",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                analysisRepo
                    .findAllCodeAnalysesOrderByCreatedAtDesc()
                    .filter { it.status == AnalysisStatus.RUNNING || it.status == AnalysisStatus.PENDING }
            },
            markFailed = {
                it.status = AnalysisStatus.CANCELLED
                it.errorMessage = i18n.t("code_analysis.be_aborted_restart")
                it.completedAt = clock.localDateTime()
            },
            save = { analysisRepo.saveCodeAnalysis(it) },
        )

    override fun getAnalysis(analysisId: Long): CodeAnalysis? = analysisRepo.findCodeAnalysisById(analysisId)

    override fun getLatestLocalAnalysis(repoConfigId: Long): CodeAnalysis? = analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId)

    override fun getLatestGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): CodeAnalysis? = analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(gitlabConfigId, gitlabProjectId)

    override fun getLatestGithubAnalysis(githubRepoConfigId: Long): CodeAnalysis? = analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId)

    override fun getAllAnalyses(): List<CodeAnalysis> = analysisRepo.findRecentCodeAnalyses(AnalysisConstants.MAX_RECENT_ANALYSES)

    override fun deleteAnalysis(analysisId: Long) {
        analysisRepo.deleteCodeAnalysisById(analysisId)
    }

    override fun cancelAnalysis(analysisId: Long) {
        cancellationService.cancelAnalysis(analysisId)
    }

    override fun cancelLatestRunningForRepo(repoConfigId: Long): Long? = cancellationService.cancelLatestRunningForRepo(repoConfigId)

    override fun startLocalAnalysis(
        repoConfigId: Long,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ): CodeAnalysis {
        val analysis =
            analysisRepo.saveCodeAnalysis(
                CodeAnalysis(
                    repoConfigId = repoConfigId,
                    status = AnalysisStatus.PENDING,
                    modelUsed = "copilot-cli/${copilotCliService.resolveModel()}",
                ),
            )
        dispatch(
            CodeAnalysisTarget.Local(
                analysisId = analysis.id,
                options = AnalysisOptions(includeMermaid, includeDrawio),
            ),
        )
        return analysis
    }

    override fun startGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
        projectPath: String,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ): List<CodeAnalysis> {
        val subApps: List<Pair<String, String>> =
            try {
                val cacheKey = gitlabConfigId to gitlabProjectId
                val allPaths =
                    gitlabPathsCache.computeIfAbsent(cacheKey) {
                        codeRepoPort.listGitlabFilePaths(gitlabConfigId, gitlabProjectId)
                    }
                monorepoDetector.detectSubApps(allPaths)
            } catch (e: Exception) {
                log.warn("[CodeAnalysis] Monorepo detection failed (Projekt={}): {}", gitlabProjectId, e.message)
                emptyList()
            }

        return if (subApps.size >= 2) {
            log.info("[CodeAnalysis] Monorepo erkannt: {} Sub-Apps in Projekt {}: {}", subApps.size, projectPath, subApps.map { it.second })
            val analyses =
                subApps.map { (subPath, appName) ->
                    analysisRepo.saveCodeAnalysis(
                        CodeAnalysis(
                            gitlabConfigId = gitlabConfigId,
                            gitlabProjectId = gitlabProjectId,
                            gitlabProjectPath = projectPath,
                            monorepoSubPath = subPath,
                            monorepoAppName = appName,
                            status = AnalysisStatus.PENDING,
                            modelUsed = "copilot-cli/${copilotCliService.resolveModel()}",
                        ),
                    )
                }
            analyses.forEach { analysis ->
                dispatch(
                    CodeAnalysisTarget.Gitlab(
                        analysisId = analysis.id,
                        options = AnalysisOptions(includeMermaid, includeDrawio),
                        gitlabConfigId = gitlabConfigId,
                        projectId = gitlabProjectId,
                        projectPath = projectPath,
                        subPath = analysis.monorepoSubPath ?: "",
                    ),
                )
            }
            analyses
        } else {
            val analysis =
                analysisRepo.saveCodeAnalysis(
                    CodeAnalysis(
                        gitlabConfigId = gitlabConfigId,
                        gitlabProjectId = gitlabProjectId,
                        gitlabProjectPath = projectPath,
                        status = AnalysisStatus.PENDING,
                        modelUsed = "copilot-cli/${copilotCliService.resolveModel()}",
                    ),
                )
            dispatch(
                CodeAnalysisTarget.Gitlab(
                    analysisId = analysis.id,
                    options = AnalysisOptions(includeMermaid, includeDrawio),
                    gitlabConfigId = gitlabConfigId,
                    projectId = gitlabProjectId,
                    projectPath = projectPath,
                    subPath = "",
                ),
            )
            listOf(analysis)
        }
    }

    override fun startGithubAnalysis(
        githubRepoConfigId: Long,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ): CodeAnalysis {
        val analysis =
            analysisRepo.saveCodeAnalysis(
                CodeAnalysis(
                    githubRepoConfigId = githubRepoConfigId,
                    status = AnalysisStatus.PENDING,
                    modelUsed = "copilot-cli/${copilotCliService.resolveModel()}",
                ),
            )
        dispatch(
            CodeAnalysisTarget.Github(
                analysisId = analysis.id,
                options = AnalysisOptions(includeMermaid, includeDrawio),
                githubRepoConfigId = githubRepoConfigId,
            ),
        )
        return analysis
    }

    override fun createMr(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
    ): String = mrService.createMr(analysisId, gitlabConfigId, projectId)

    private fun dispatch(target: CodeAnalysisTarget) {
        when (target) {
            is CodeAnalysisTarget.Local ->
                asyncExecutor.submit("analysisTaskExecutor") {
                    runAnalysis(target.analysisId, target.options.includeMermaid, target.options.includeDrawio)
                }
            is CodeAnalysisTarget.Gitlab ->
                asyncExecutor.submit("analysisTaskExecutor") {
                    runGitlabAnalysis(
                        target.analysisId,
                        target.gitlabConfigId,
                        target.projectId,
                        target.projectPath,
                        target.options.includeMermaid,
                        target.options.includeDrawio,
                        target.subPath,
                    )
                }
            is CodeAnalysisTarget.Github ->
                asyncExecutor.submit("analysisTaskExecutor") {
                    runGithubAnalysis(target.analysisId, target.githubRepoConfigId, target.options.includeMermaid, target.options.includeDrawio)
                }
        }
    }

    fun runAnalysis(
        analysisId: Long,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    ) = executor.runAnalysis(analysisId, includeMermaid, includeDrawio)

    fun runGitlabAnalysis(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
        subPath: String = "",
    ) = executor.runGitlabAnalysis(analysisId, gitlabConfigId, projectId, projectPath, includeMermaid, includeDrawio, subPath)

    fun runGithubAnalysis(
        analysisId: Long,
        githubRepoConfigId: Long,
        includeMermaid: Boolean = false,
        includeDrawio: Boolean = false,
    ) = executor.runGithubAnalysis(analysisId, githubRepoConfigId, includeMermaid, includeDrawio)
}
