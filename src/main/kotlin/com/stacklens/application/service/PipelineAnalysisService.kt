package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.pipeline.PipelineAnalysisUseCase
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.pipeline.PipelineFilesPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.pipeline.PipelineAnalysisExecutor
import com.stacklens.application.service.pipeline.PipelineAnalysisMrService
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult
import org.slf4j.LoggerFactory

@ApplicationService
class PipelineAnalysisService(
    private val analysisRepo: PipelineAnalysisPort,
    private val filesPort: PipelineFilesPort,
    private val copilotCliService: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val i18n: I18nPort,
    private val aiService: PipelineAnalysisAiService,
    private val startupHealer: StartupRecovery,
    private val asyncExecutor: AsyncExecutorPort,
    private val mrService: PipelineAnalysisMrService,
    private val clock: ClockPort,
    private val executor: PipelineAnalysisExecutor,
) : PipelineAnalysisUseCase,
    StaleWorkHealer {
    private val log = LoggerFactory.getLogger(PipelineAnalysisService::class.java)

    override fun heal() {
        resetStaleAnalyses()
    }

    fun resetStaleAnalyses() =
        startupHealer.heal(
            logTag = "Pipeline-Analyse",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                analysisRepo
                    .findAllPipelineAnalysesOrderByCreatedAtDesc()
                    .filter { it.status == AnalysisStatus.RUNNING || it.status == AnalysisStatus.PENDING }
            },
            markFailed = {
                it.status = AnalysisStatus.CANCELLED
                it.errorMessage = i18n.t("pipeline.be_aborted_restart")
                it.completedAt = clock.localDateTime()
            },
            save = { analysisRepo.savePipelineAnalysis(it) },
        )

    override fun getAnalysis(analysisId: Long): PipelineAnalysis? = analysisRepo.findPipelineAnalysisById(analysisId)

    @Suppress("FunctionBodyOnly")
    override fun getLatestLocalAnalysis(repoConfigId: Long): PipelineAnalysis? = analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId)

    @Suppress("FunctionBodyOnly")
    override fun getLatestGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): PipelineAnalysis? = analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(gitlabConfigId, gitlabProjectId)

    @Suppress("FunctionBodyOnly")
    override fun getLatestGithubAnalysis(githubRepoConfigId: Long): PipelineAnalysis? = analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId)

    override fun getAllAnalyses(): List<PipelineAnalysis> = analysisRepo.findRecentPipelineAnalyses(AnalysisConstants.MAX_RECENT_ANALYSES)

    override fun deleteAnalysis(analysisId: Long) {
        analysisRepo.deletePipelineAnalysisById(analysisId)
    }

    override fun cancelAnalysis(analysisId: Long) {
        executor.markCancelled(analysisId)
        val analysis = analysisRepo.findPipelineAnalysisById(analysisId) ?: return
        if (analysis.status == AnalysisStatus.RUNNING || analysis.status == AnalysisStatus.PENDING) {
            analysis.status = AnalysisStatus.CANCELLED
            analysis.errorMessage = i18n.t("pipeline.be_cancelled")
            analysis.completedAt = clock.localDateTime()
            analysisRepo.savePipelineAnalysis(analysis)
        }
    }

    override fun cancelLatestRunningForRepo(repoConfigId: Long): Long? {
        val running = analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId) ?: return null
        if (running.status != AnalysisStatus.RUNNING && running.status != AnalysisStatus.PENDING) return null
        cancelAnalysis(running.id)
        return running.id
    }

    override fun cancelLatestRunningForGitlab(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
    ): Long? {
        val running =
            analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(
                gitlabConfigId,
                gitlabProjectId,
            ) ?: return null
        if (running.status != AnalysisStatus.RUNNING && running.status != AnalysisStatus.PENDING) return null
        cancelAnalysis(running.id)
        return running.id
    }

    override fun cancelLatestRunningForGithub(githubRepoConfigId: Long): Long? {
        val running =
            analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId) ?: return null
        if (running.status != AnalysisStatus.RUNNING && running.status != AnalysisStatus.PENDING) return null
        cancelAnalysis(running.id)
        return running.id
    }

    override fun createMrOrPr(
        analysisId: Long,
        gitlabConfigId: Long?,
        gitlabProjectId: Long?,
    ): PipelineMrResult = mrService.createMrOrPr(analysisId, gitlabConfigId, gitlabProjectId)

    private fun dispatch(target: PipelineAnalysisTarget) {
        asyncExecutor.submit("analysisTaskExecutor") {
            when (target) {
                is PipelineAnalysisTarget.Local -> runLocal(target.analysisId)
                is PipelineAnalysisTarget.Gitlab ->
                    runGitlab(target.analysisId, target.gitlabConfigId, target.projectId, target.projectPath)
                is PipelineAnalysisTarget.Github -> runGithub(target.analysisId, target.githubRepoConfigId)
            }
        }
    }

    override fun startLocalAnalysis(
        repoConfigId: Long,
        userPrompt: String?,
    ): PipelineAnalysis {
        cancelLatestRunningForRepo(repoConfigId)
        val analysis =
            analysisRepo.savePipelineAnalysis(
                PipelineAnalysis(
                    repoConfigId = repoConfigId,
                    status = AnalysisStatus.PENDING,
                    modelUsed = "copilot-cli/${copilotCliService.resolveModel()}",
                    userPromptAdditions = userPrompt?.takeIf { it.isNotBlank() }?.take(4000),
                ),
            )
        dispatch(PipelineAnalysisTarget.Local(analysis.id))
        return analysis
    }

    override fun startGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
        projectPath: String,
        userPrompt: String?,
    ): PipelineAnalysis {
        cancelLatestRunningForGitlab(gitlabConfigId, gitlabProjectId)
        val analysis =
            analysisRepo.savePipelineAnalysis(
                PipelineAnalysis(
                    gitlabConfigId = gitlabConfigId,
                    gitlabProjectId = gitlabProjectId,
                    gitlabProjectPath = projectPath,
                    status = AnalysisStatus.PENDING,
                    modelUsed = "copilot-cli/${copilotCliService.resolveModel()}",
                    userPromptAdditions = userPrompt?.takeIf { it.isNotBlank() }?.take(4000),
                ),
            )
        dispatch(
            PipelineAnalysisTarget.Gitlab(
                analysisId = analysis.id,
                gitlabConfigId = gitlabConfigId,
                projectId = gitlabProjectId,
                projectPath = projectPath,
            ),
        )
        return analysis
    }

    override fun startGithubAnalysis(
        githubRepoConfigId: Long,
        userPrompt: String?,
    ): PipelineAnalysis {
        cancelLatestRunningForGithub(githubRepoConfigId)
        val analysis =
            analysisRepo.savePipelineAnalysis(
                PipelineAnalysis(
                    githubRepoConfigId = githubRepoConfigId,
                    status = AnalysisStatus.PENDING,
                    modelUsed = "copilot-cli/${copilotCliService.resolveModel()}",
                    userPromptAdditions = userPrompt?.takeIf { it.isNotBlank() }?.take(4000),
                ),
            )
        dispatch(
            PipelineAnalysisTarget.Github(
                analysisId = analysis.id,
                githubRepoConfigId = githubRepoConfigId,
            ),
        )
        return analysis
    }

    fun runLocal(analysisId: Long) = executor.runLocal(analysisId)

    fun runGitlab(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
    ) = executor.runGitlab(analysisId, gitlabConfigId, projectId, projectPath)

    fun runGithub(
        analysisId: Long,
        githubRepoConfigId: Long,
    ) = executor.runGithub(analysisId, githubRepoConfigId)
}
