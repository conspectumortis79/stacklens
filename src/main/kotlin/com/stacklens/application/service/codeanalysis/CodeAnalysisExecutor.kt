package com.stacklens.application.service.codeanalysis

import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.codeanalysis.CodeAnalysisAi
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import org.slf4j.LoggerFactory

@ApplicationService
class CodeAnalysisExecutor(
    private val analysisRepo: CodeAnalysisPort,
    private val copilotCliService: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val i18n: I18nPort,
    private val aiService: CodeAnalysisAi,
    private val localStrategy: LocalCodeAnalysisStrategy,
    private val gitlabStrategy: GitlabCodeAnalysisStrategy,
    private val githubStrategy: GithubCodeAnalysisStrategy,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(CodeAnalysisExecutor::class.java)

    fun runAnalysis(
        analysisId: Long,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ) {
        val params = localStrategy.params(analysisRepo.findCodeAnalysisById(analysisId))
        runFor(
            analysisId = analysisId,
            includeMermaid = includeMermaid,
            includeDrawio = includeDrawio,
            strategy = localStrategy,
            params = params,
            logTag = params.appName,
        )
    }

    fun runGitlabAnalysis(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
        subPath: String,
    ) {
        val params =
            gitlabStrategy.paramsFor(
                gitlabConfigId = gitlabConfigId,
                projectId = projectId,
                projectPath = projectPath,
                subPath = subPath,
            )
        runFor(
            analysisId = analysisId,
            includeMermaid = includeMermaid,
            includeDrawio = includeDrawio,
            strategy = gitlabStrategy,
            params = params,
            logTag = "$projectPath/$subPath",
        )
    }

    fun runGithubAnalysis(
        analysisId: Long,
        githubRepoConfigId: Long,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
    ) {
        val params = githubStrategy.paramsFor(githubRepoConfigId)
        runFor(
            analysisId = analysisId,
            includeMermaid = includeMermaid,
            includeDrawio = includeDrawio,
            strategy = githubStrategy,
            params = params,
            logTag = params.appName,
        )
    }

    private fun runFor(
        analysisId: Long,
        includeMermaid: Boolean,
        includeDrawio: Boolean,
        strategy: CodeAnalysisStrategy,
        params: CodeAnalysisLoadParams,
        logTag: String,
    ) {
        val analysis = analysisRepo.findCodeAnalysisById(analysisId) ?: return
        analysis.status = AnalysisStatus.RUNNING
        analysisRepo.saveCodeAnalysis(analysis)

        try {
            if (!copilotCliService.isReady()) throw IllegalStateException(i18n.t("code_analysis.be_no_github_token"))

            aiUsageContext.setContext("code", analysisId)
            aiUsageContext.setModelOverride(copilotCliService.resolveModel())

            val data =
                strategy.load(params) ?: throw IllegalStateException(i18n.t("code_analysis.be_repo_config_not_found", params.appName))
            val prompt = aiService.buildAnalysisPrompt(data.appName, data.repoPath, data.tree, data.contents, includeMermaid, includeDrawio)
            val output = copilotCliService.execute(copilotCliService.resolveModel(), prompt, 600L)

            aiService.applyAiOutput(analysis, output, includeMermaid, includeDrawio)
            if (analysisRepo.findCodeAnalysisById(analysisId)?.status == AnalysisStatus.FAILED) {
                log.info("[CodeAnalysis {}] Cancelled during AI run — skipping COMPLETED save", analysisId)
                return
            }
            analysisRepo.saveCodeAnalysis(analysis)
            log.info("[CodeAnalysis {}] Abgeschlossen ({})", analysisId, logTag)
        } catch (e: Exception) {
            log.error("[CodeAnalysis {}] Error: {}", analysisId, e.message, e)
            val fresh = runCatching { analysisRepo.findCodeAnalysisById(analysisId) }.getOrNull() ?: analysis
            fresh.status = AnalysisStatus.FAILED
            fresh.errorMessage = i18n.translateError(e).take(1000)
            fresh.completedAt = clock.localDateTime()
            analysisRepo.saveCodeAnalysis(fresh)
        } finally {
            aiUsageContext.clearContext()
        }
    }
}
