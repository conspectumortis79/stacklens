package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.security.SecurityAnalysisUseCase
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.securityanalysis.SecurityAiOutputParser
import com.stacklens.application.service.securityanalysis.SecurityAnalysisPromptBuilder
import com.stacklens.application.service.securityanalysis.SecurityFinding
import com.stacklens.application.service.securityanalysis.SecurityFindingMerger
import com.stacklens.application.service.securityanalysis.SecurityFixService
import com.stacklens.application.service.securityanalysis.SecurityPentestExecutor
import com.stacklens.application.service.securityanalysis.SecurityRepoStrategy
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.SecurityAnalysis
import com.stacklens.domain.model.security.SecurityFixResult
import org.slf4j.LoggerFactory

@ApplicationService
class SecurityAnalysisService(
    private val analysisRepo: SecurityAnalysisPort,
    private val copilotPort: CopilotStreamingPort,
    private val aiUsageContext: AiUsageContextPort,
    private val jsonCodec: JsonCodecPort,
    private val i18n: I18nPort,
    private val repoStrategies: List<SecurityRepoStrategy>,
    private val asyncExecutor: AsyncExecutorPort,
    private val startupHealer: StartupRecovery,
    private val promptBuilder: SecurityAnalysisPromptBuilder,
    private val findingMerger: SecurityFindingMerger,
    private val aiOutputParser: SecurityAiOutputParser,
    private val clock: ClockPort,
    private val fixService: SecurityFixService,
    private val pentestExecutor: SecurityPentestExecutor,
) : SecurityAnalysisUseCase,
    StaleWorkHealer {
    private val log = LoggerFactory.getLogger(SecurityAnalysisService::class.java)

    override fun heal() {
        resetStaleAnalyses()
    }

    fun resetStaleAnalyses() =
        startupHealer.heal(
            logTag = "Security-Analyse",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                analysisRepo.findAllSecurityAnalysesOrderByCreatedAtDesc().filter {
                    it.status == AnalysisStatus.RUNNING ||
                        it.status == AnalysisStatus.PENDING
                }
            },
            markFailed = {
                it.status = AnalysisStatus.CANCELLED
                it.errorMessage = i18n.t("bug_analysis.be_aborted_restart")
                it.completedAt =
                    clock.localDateTime()
            },
            save = { analysisRepo.saveSecurityAnalysis(it) },
        )

    override fun getAnalysis(analysisId: Long): SecurityAnalysis? = analysisRepo.findSecurityAnalysisById(analysisId)

    override fun getAllAnalyses(): List<SecurityAnalysis> = analysisRepo.findRecentSecurityAnalyses(AnalysisConstants.MAX_RECENT_ANALYSES)

    override fun deleteAnalysis(analysisId: Long) = analysisRepo.deleteSecurityAnalysisById(analysisId)

    override fun cancelAnalysis(analysisId: Long) {
        val a = analysisRepo.findSecurityAnalysisById(analysisId) ?: return
        if (a.status == AnalysisStatus.RUNNING ||
            a.status == AnalysisStatus.PENDING
        ) {
            a.status = AnalysisStatus.CANCELLED
            a.errorMessage = i18n.t("bug_analysis.be_cancelled")
            a.completedAt =
                clock.localDateTime()
            analysisRepo.saveSecurityAnalysis(a)
        }
    }

    override fun generateFix(
        analysisId: Long,
        findingId: String,
    ): SecurityFixResult = fixService.generateFix(analysisId, findingId)

    override fun startLocalAnalysis(
        repoConfigId: Long,
        iterations: Int,
    ): SecurityAnalysis =
        saveAndRun(
            SecurityAnalysis(
                repoConfigId = repoConfigId,
                status = AnalysisStatus.PENDING,
                totalIterations = iterations.coerceIn(1, 3),
                modelUsed = "copilot-cli/${copilotPort.resolveModel()}",
            ),
        )

    override fun startGitlabAnalysis(
        gitlabConfigId: Long,
        gitlabProjectId: Long,
        projectPath: String,
        iterations: Int,
    ): SecurityAnalysis =
        saveAndRun(
            SecurityAnalysis(
                gitlabConfigId = gitlabConfigId,
                gitlabProjectId = gitlabProjectId,
                gitlabProjectPath = projectPath,
                status = AnalysisStatus.PENDING,
                totalIterations = iterations.coerceIn(1, 3),
                modelUsed = "copilot-cli/${copilotPort.resolveModel()}",
            ),
        )

    override fun startGithubAnalysis(
        githubRepoConfigId: Long,
        iterations: Int,
    ): SecurityAnalysis =
        saveAndRun(
            SecurityAnalysis(
                githubRepoConfigId = githubRepoConfigId,
                status = AnalysisStatus.PENDING,
                totalIterations = iterations.coerceIn(1, 3),
                modelUsed = "copilot-cli/${copilotPort.resolveModel()}",
            ),
        )

    private fun saveAndRun(analysis: SecurityAnalysis): SecurityAnalysis {
        val saved = analysisRepo.saveSecurityAnalysis(analysis)
        asyncExecutor.submit("analysisTaskExecutor") { runAnalysis(saved.id) }
        return saved
    }

    fun runAnalysis(analysisId: Long) = pentestExecutor.runAnalysis(analysisId)

    internal fun parseFindings(
        rawOutput: String,
        iteration: Int,
    ): List<SecurityFinding> = findingMerger.parseFindings(rawOutput, iteration)

    internal fun buildIterationPrompt(
        iteration: Int,
        totalIterations: Int,
        projectName: String,
        projectPath: String,
        projectTree: String,
        fileContents: String,
        previousFindingsJson: String,
    ): String =
        promptBuilder.buildIterationPrompt(
            iteration,
            totalIterations,
            projectName,
            projectPath,
            projectTree,
            fileContents,
            previousFindingsJson,
        )

    internal fun applyAiOutput(
        analysis: SecurityAnalysis,
        output: String,
    ) {
        analysis.summaryText = aiOutputParser.extractSummary(output)
        analysis.status = AnalysisStatus.COMPLETED
    }

    internal fun parseFixOutput(rawOutput: String): com.stacklens.application.service.securityanalysis.ParsedFix = aiOutputParser.parseFixOutput(rawOutput)

    companion object {
        const val FIX_AI_BUDGET_SECONDS: Long = 600L
    }
}
