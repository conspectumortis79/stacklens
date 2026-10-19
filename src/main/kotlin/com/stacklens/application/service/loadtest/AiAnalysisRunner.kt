package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.async.ParallelTaskPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.AiUsageKind
import com.stacklens.domain.pipeline.CancellationSignal
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ApplicationService
@ManuallyWired
class AiAnalysisRunner
    constructor(
        private val deps: AiAnalysisRunnerDeps,
        private val clock: ClockPort,
        private val parallelTasks: ParallelTaskPort,
        private val contextBuilder: LoadTestContextBuilder,
        private val runRegistry: LoadTestRunRegistry,
    ) {
        private val analysisRepo get() = deps.analysisRepo
        private val copilotPort get() = deps.copilotPort
        private val i18n get() = deps.i18n
        private val aiService get() = deps.aiService
        private val copilotConfig get() = deps.copilotConfig
        private val log = LoggerFactory.getLogger(AiAnalysisRunner::class.java)

        fun runGitlabAnalysis(
            analysisId: Long,
            gitlabConfigId: Long,
            projectId: Long,
            projectPath: String,
        ) = deps.asyncExecutor.submit("analysisTaskExecutor") {
            val ctx = contextBuilder.buildGitlabContext(analysisId, gitlabConfigId, projectId, projectPath)
            runAnalysisPipeline(analysisId, sourceLabel = "gitlab=$projectPath", ctx = ctx)
        }

        fun runLocalAnalysis(
            analysisId: Long,
            repoConfigId: Long,
        ) = deps.asyncExecutor.submit("analysisTaskExecutor") {
            val ctx = contextBuilder.buildLocalContext(analysisId, repoConfigId)
            runAnalysisPipeline(analysisId, sourceLabel = "local", ctx = ctx)
        }

        fun runGithubAnalysis(
            analysisId: Long,
            githubRepoConfigId: Long,
        ) = deps.asyncExecutor.submit("analysisTaskExecutor") {
            val ctx = contextBuilder.buildGithubContext(analysisId, githubRepoConfigId)
            runAnalysisPipeline(analysisId, sourceLabel = "github=${ctx.projectName}", ctx = ctx)
        }

        fun cancelRunningThread(analysisId: Long) {
            val cancelled = runRegistry.cancel(analysisId)
            log.info("[LoadTestAnalysis {}] Hard aborted – cancellation signalled: {}", analysisId, cancelled)
        }

        private fun runAnalysisPipeline(
            analysisId: Long,
            sourceLabel: String,
            ctx: LoadTestAnalysisContext,
        ) {
            val worker = Thread.currentThread()
            runRegistry.register(analysisId, CancellationSignal { worker.interrupt() })
            var analysis = analysisRepo.findLoadTestAnalysisById(analysisId) ?: return
            if (analysis.status == AnalysisStatus.FAILED) {
                runRegistry.unregister(analysisId)
                return
            }
            analysis.status = AnalysisStatus.RUNNING
            analysis = analysisRepo.saveLoadTestAnalysis(analysis)

            try {
                if (!copilotPort.isReady()) throw IllegalStateException(i18n.t("load_test.be_no_github_token"))

                val model = copilotPort.resolveModel()
                val lang = copilotPort.resolveLanguage()
                copilotPort.setContext(AiUsageKind.LOADTEST.id, analysisId)
                copilotPort.setModelOverride(model)

                val analysisPrompt =
                    aiService.buildAnalysisPrompt(lang, ctx.projectName, ctx.projectPath, ctx.tree, ctx.contents)
                val analysisOutput = copilotPort.execute(model, analysisPrompt, 600L)

                analysis.summaryText = aiService.extractSection(analysisOutput, "SUMMARY:", "FINDINGS:").trim()
                analysis.findingsJson = aiService.extractJsonBlock(analysisOutput, "FINDINGS:", "K6_SCRIPT:")
                analysis = analysisRepo.saveLoadTestAnalysis(analysis)

                val findingsText = analysis.findingsJson ?: "[]"
                val envPrompt = aiService.buildEnvDetectPrompt(lang, ctx.projectName, ctx.dockerContents, ctx.routeContents)
                val k6Prompt = aiService.buildK6Prompt(lang, ctx.projectName, findingsText, ctx.dockerContents, ctx.routeContents)
                val trackKind = AiUsageKind.LOADTEST.id
                val trackId = analysisId
                val envFuture =
                    parallelTasks.submit {
                        copilotPort.setContext(trackKind, trackId)
                        try {
                            copilotPort.setModelOverride(model)
                            copilotPort.execute(model, envPrompt, copilotConfig.loadTestAnalysisTimeoutSeconds)
                        } finally {
                            copilotPort.clearContext()
                        }
                    }
                val k6Future =
                    parallelTasks.submit {
                        copilotPort.setContext(trackKind, trackId)
                        try {
                            copilotPort.setModelOverride(model)
                            copilotPort.execute(model, k6Prompt, copilotConfig.loadTestAnalysisTimeoutSeconds)
                        } finally {
                            copilotPort.clearContext()
                        }
                    }
                CompletableFuture
                    .allOf(envFuture, k6Future)
                    .get(copilotConfig.loadTestAnalysisTimeoutSeconds + 30L, TimeUnit.SECONDS)

                analysis.environmentsJson = aiService.extractJsonArray(envFuture.get())
                analysis.k6Script = aiService.extractCodeBlock(k6Future.get(), "javascript", "js")

                analysis.status = AnalysisStatus.COMPLETED
                analysis.completedAt = clock.localDateTime()
                analysisRepo.saveLoadTestAnalysis(analysis)
                log.info("[LoadTestAnalysis {}] Completed ({})", analysisId, sourceLabel)
            } catch (e: Exception) {
                handleAnalysisError(analysisId, sourceLabel, e)
            } finally {
                runRegistry.unregister(analysisId)
                copilotPort.clearContext()
            }
        }

        private fun handleAnalysisError(
            analysisId: Long,
            sourceLabel: String,
            e: Exception,
        ) {
            if (e is InterruptedException || e.cause is InterruptedException) {
                log.debug("[LoadTestAnalysis {}] Cancelled ({}) – thread interrupt, no error update needed.", analysisId, sourceLabel)
                Thread.currentThread().interrupt()
                return
            }
            val rootMsg = (e.cause ?: e).message ?: e.toString()
            if (rootMsg.contains("Copilot CLI Timeout")) {
                log.warn("[LoadTestAnalysis {}] Timeout ({}): {}", analysisId, sourceLabel, rootMsg)
            } else {
                log.error("[LoadTestAnalysis {}] Error ({}): {}", analysisId, sourceLabel, e.message, e)
            }
            val failed = analysisRepo.findLoadTestAnalysisById(analysisId)
            if (failed != null && failed.status != AnalysisStatus.COMPLETED) {
                failed.status = AnalysisStatus.FAILED
                failed.errorMessage = i18n.translateError(e).take(1_000)
                failed.completedAt = clock.localDateTime()
                analysisRepo.saveLoadTestAnalysis(failed)
            }
        }

        internal fun localSourceFiles(repoPath: String): List<LoadTestSourceFile> = contextBuilder.localSourceFiles(repoPath)

        internal fun splitGitlabFiles(
            files: List<Pair<String, String>>,
            i18n: I18nPort,
        ): Pair<String, String> = contextBuilder.splitGitlabFiles(files, i18n)
    }
