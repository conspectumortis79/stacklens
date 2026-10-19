package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.analysis.AiAnalysisUseCase
import com.stacklens.application.port.out.AiAnalysisPort
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.analysis.AiAnalysisTriggerPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.storage.DiagramStoragePort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.analysis.AiAnalysisPromptBuilder
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.AiAnalysis
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import org.slf4j.LoggerFactory

@ApplicationService
@ManuallyWired
class AiAnalysisService
    constructor(
        private val deps: AiAnalysisServiceDeps,
        private val clock: ClockPort,
    ) : AiAnalysisUseCase,
        AiAnalysisTriggerPort,
        StaleWorkHealer {
        constructor(
            analysisRepo: AiAnalysisPort,
            resultRepo: SearchResultPort,
            sessionRepo: SearchSessionPort,
            copilotCliService: CopilotStreamingPort,
            aiUsageContext: AiUsageContextPort,
            promptBuilder: AiAnalysisPromptBuilder,
            asyncExecutor: AsyncExecutorPort,
            i18n: I18nPort,
            copilotConfig: CopilotConfigPort,
            startupHealer: StartupRecovery,
            diagramStorage: DiagramStoragePort,
            clock: ClockPort,
        ) : this(
            AiAnalysisServiceDeps(
                analysisRepo,
                resultRepo,
                sessionRepo,
                copilotCliService,
                aiUsageContext,
                promptBuilder,
                asyncExecutor,
                i18n,
                copilotConfig,
                startupHealer,
                diagramStorage,
            ),
            clock,
        )

        private val analysisRepo get() = deps.analysisRepo
        private val resultRepo get() = deps.resultRepo
        private val sessionRepo get() = deps.sessionRepo
        private val copilotCliService get() = deps.copilotCliService
        private val aiUsageContext get() = deps.aiUsageContext
        private val promptBuilder get() = deps.promptBuilder
        private val asyncExecutor get() = deps.asyncExecutor
        private val i18n get() = deps.i18n
        private val startupHealer get() = deps.startupHealer
        private val diagramStorage get() = deps.diagramStorage
        private val log = LoggerFactory.getLogger(AiAnalysisService::class.java)

        override fun heal() {
            healOrphanedAnalyses()
        }

        fun healOrphanedAnalyses() =
            startupHealer.heal(
                logTag = "Suchanalyse",
                failedStatus = AnalysisStatus.CANCELLED,
                findStale = { analysisRepo.findByStatusIn(listOf(AnalysisStatus.RUNNING, AnalysisStatus.PENDING)) },
                markFailed = {
                    it.status = AnalysisStatus.CANCELLED
                    it.errorMessage = i18n.t("suche.be_aborted_restart")
                    it.completedAt = clock.localDateTime()
                },
                save = { analysisRepo.saveAiAnalysis(it) },
            )

        override fun startAnalysis(
            sessionId: Long,
            includeMermaid: Boolean,
            includeDrawio: Boolean,
        ): AiAnalysis {
            val model = copilotCliService.resolveModel()
            val analysis =
                analysisRepo.saveAiAnalysis(
                    AiAnalysis(
                        sessionId = sessionId,
                        status = AnalysisStatus.PENDING,
                        modelUsed = "copilot-cli/$model",
                    ),
                )
            asyncExecutor.submit("analysisTaskExecutor") {
                runAnalysis(analysis.id, includeMermaid, includeDrawio)
            }
            return analysis
        }

        override fun getLatestAnalysis(sessionId: Long): AiAnalysis? = analysisRepo.findTopBySessionIdOrderByCreatedAtDesc(sessionId)

        override fun sessionExists(sessionId: Long): Boolean = sessionRepo.sessionExistsById(sessionId)

        override fun saveDiagram(
            path: String,
            filename: String,
            content: String,
        ): String = diagramStorage.saveDiagram(path, filename, content)

        fun runAnalysis(
            analysisId: Long,
            includeMermaid: Boolean = false,
            includeDrawio: Boolean = false,
        ) {
            val analysis = analysisRepo.findAiAnalysisById(analysisId) ?: return
            analysis.status = AnalysisStatus.RUNNING
            analysisRepo.saveAiAnalysis(analysis)

            try {
                if (!prepareAiUsageContext(analysisId)) {
                    markAnalysisNoToken(analysis)
                    return
                }

                val session =
                    sessionRepo.findSessionById(analysis.sessionId)
                        ?: throw IllegalStateException("session ${analysis.sessionId} not found")

                val results =
                    loadResultsForPrompt(analysis.sessionId)

                val output = runCopilotAndCollectOutput(session, results, includeMermaid, includeDrawio)

                applyOutputToAnalysis(analysis, output, includeMermaid, includeDrawio)
                log.info("[AiAnalysis {}] Abgeschlossen", analysisId)
                aiUsageContext.clearContext()
            } catch (e: Exception) {
                log.error("[AiAnalysis {}] Error: {}", analysisId, e.message, e)
                val fresh = runCatching { analysisRepo.findAiAnalysisById(analysisId) }.getOrNull()
                val target = fresh ?: analysis
                target.status = AnalysisStatus.FAILED
                target.errorMessage = i18n.translateError(e).take(1000)
                target.completedAt = clock.localDateTime()
                runCatching { analysisRepo.saveAiAnalysis(target) }
            } finally {
                aiUsageContext.clearContext()
            }
        }

        private fun prepareAiUsageContext(analysisId: Long): Boolean {
            aiUsageContext.setContext("ai", analysisId)
            aiUsageContext.setModelOverride(copilotCliService.resolveModel())
            if (!copilotCliService.isReady()) return false
            if (copilotCliService.resolveToken() == null) return false
            return true
        }

        private fun markAnalysisNoToken(analysis: AiAnalysis) {
            analysis.status = AnalysisStatus.NO_TOKEN
            analysis.completedAt = clock.localDateTime()
            analysisRepo.saveAiAnalysis(analysis)
        }

        private fun loadResultsForPrompt(sessionId: Long): List<SearchResult> =
            resultRepo
                .findCappedBySessionId(sessionId, MAX_RESULTS_PER_SOURCE)
                .values
                .flatten()

        private fun runCopilotAndCollectOutput(
            session: SearchSession,
            results: List<SearchResult>,
            includeMermaid: Boolean,
            includeDrawio: Boolean,
        ): String =
            copilotCliService.executeWithStreaming(
                model = copilotCliService.resolveModel(),
                prompt =
                    promptBuilder.buildPrompt(
                        session = session,
                        results = results,
                        includeMermaid = includeMermaid,
                        includeDrawio = includeDrawio,
                    ),
                timeoutOverride = 600L,
                onPartialOutput = { },
            )

        private fun applyOutputToAnalysis(
            analysis: AiAnalysis,
            output: String,
            includeMermaid: Boolean,
            includeDrawio: Boolean,
        ) {
            analysis.analysisText = output
            if (includeMermaid) {
                analysis.mermaid = promptBuilder.extractMermaid(output)
            }
            if (includeDrawio) {
                val raw = promptBuilder.extractDrawioXml(output)
                analysis.drawioXml = raw?.let { promptBuilder.sanitizeDrawioXml(it) }
            }
            analysis.status = AnalysisStatus.COMPLETED
            analysis.completedAt = clock.localDateTime()
            analysisRepo.saveAiAnalysis(analysis)
        }

        private companion object {
            const val MAX_RESULTS_PER_SOURCE = 60
        }
    }
