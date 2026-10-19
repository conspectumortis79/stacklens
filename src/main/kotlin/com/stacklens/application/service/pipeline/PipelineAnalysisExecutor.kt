package com.stacklens.application.service.pipeline
import com.stacklens.application.port.out.PipelineAnalysisPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.pipeline.PipelineFilesPort
import com.stacklens.application.port.out.pipeline.PipelineRepoData
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.PipelineAnalysisAiService
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@ApplicationService
class PipelineAnalysisExecutor(
    private val analysisRepo: PipelineAnalysisPort,
    private val filesPort: PipelineFilesPort,
    private val copilotCliService: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val i18n: I18nPort,
    private val aiService: PipelineAnalysisAiService,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(PipelineAnalysisExecutor::class.java)

    private val cancelledIds = ConcurrentHashMap.newKeySet<Long>()

    fun markCancelled(analysisId: Long) {
        cancelledIds.add(analysisId)
    }

    fun runLocal(analysisId: Long) {
        val analysis = analysisRepo.findPipelineAnalysisById(analysisId) ?: return
        runFor(
            analysisId = analysisId,
            dataLoader = { filesPort.loadLocal(analysis.repoConfigId ?: 0L) },
            logTag = analysis.repoConfigId?.toString() ?: "local",
        )
    }

    fun runGitlab(
        analysisId: Long,
        gitlabConfigId: Long,
        projectId: Long,
        projectPath: String,
    ) {
        runFor(
            analysisId = analysisId,
            dataLoader = { filesPort.loadGitlab(gitlabConfigId, projectId, projectPath) },
            logTag = "$projectPath (gitlab)",
        )
    }

    fun runGithub(
        analysisId: Long,
        githubRepoConfigId: Long,
    ) {
        runFor(
            analysisId = analysisId,
            dataLoader = { filesPort.loadGithub(githubRepoConfigId) },
            logTag = "github-$githubRepoConfigId",
        )
    }

    private fun runFor(
        analysisId: Long,
        dataLoader: () -> PipelineRepoData?,
        logTag: String,
    ) {
        val analysis = analysisRepo.findPipelineAnalysisById(analysisId) ?: return
        analysis.status = AnalysisStatus.RUNNING
        analysisRepo.savePipelineAnalysis(analysis)

        try {
            if (!copilotCliService.isReady()) throw IllegalStateException(i18n.t("code_analysis.be_no_github_token"))
            aiUsageContext.setContext("pipeline", analysisId)
            aiUsageContext.setModelOverride(copilotCliService.resolveModel())

            val data =
                aiService.loadRepoData(dataLoader)
                    ?: run {
                        analysis.summaryText = i18n.t("pipeline.no_files_found")
                        analysis.status = AnalysisStatus.COMPLETED
                        analysis.completedAt = clock.localDateTime()
                        analysisRepo.savePipelineAnalysis(analysis)
                        log.info("[PipelineAnalysis {}] No compose/pipeline files in {} — short-circuit", analysisId, logTag)
                        return
                    }

            val prompt =
                aiService.buildAnalysisPrompt(
                    projectName = data.appName,
                    projectPath = data.repoPath,
                    projectTree = data.tree,
                    fileContents = data.contents,
                    includeMermaid = true,
                    userPrompt = analysis.userPromptAdditions,
                )
            val output = copilotCliService.execute(copilotCliService.resolveModel(), prompt, 600L)

            if (cancelledIds.remove(analysisId)) {
                log.info("[PipelineAnalysis {}] Cancelled during AI run — skipping COMPLETED save", analysisId)
                return
            }
            if (analysisRepo.findPipelineAnalysisById(analysisId)?.status == AnalysisStatus.CANCELLED) {
                log.info("[PipelineAnalysis {}] Cancelled during AI run — skipping COMPLETED save", analysisId)
                return
            }

            aiService.applyAiOutput(analysis, output, includeMermaid = true)
            if (analysis.findingsJson == null) {
                analysis.status = AnalysisStatus.FAILED
                analysis.errorMessage = i18n.t("pipeline.be_err_incomplete_output")
                analysis.completedAt = clock.localDateTime()
                analysisRepo.savePipelineAnalysis(analysis)
                log.warn("[PipelineAnalysis {}] KI-Ausgabe ohne FINDINGS-Block — als FEHLER gewertet ({})", analysisId, logTag)
                return
            }
            analysis.status = AnalysisStatus.COMPLETED
            analysis.completedAt = clock.localDateTime()
            analysisRepo.savePipelineAnalysis(analysis)
            log.info("[PipelineAnalysis {}] Abgeschlossen ({})", analysisId, logTag)
        } catch (e: Exception) {
            log.error("[PipelineAnalysis {}] Error: {}", analysisId, e.message, e)
            val fresh = runCatching { analysisRepo.findPipelineAnalysisById(analysisId) }.getOrNull() ?: analysis
            if (fresh.status != AnalysisStatus.CANCELLED) {
                fresh.status = AnalysisStatus.FAILED
                fresh.errorMessage = i18n.translateError(e).take(1000)
                fresh.completedAt = clock.localDateTime()
                analysisRepo.savePipelineAnalysis(fresh)
            }
        } finally {
            cancelledIds.remove(analysisId)
            aiUsageContext.clearContext()
        }
    }
}
