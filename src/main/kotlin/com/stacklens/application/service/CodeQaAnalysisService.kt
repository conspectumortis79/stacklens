
package com.stacklens.application.service

import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.JiraKeyUtils
import com.stacklens.domain.enums.CodeHostingPlatform
import com.stacklens.domain.model.CodeQaAnalysis
import org.slf4j.LoggerFactory

@ApplicationService
@ManuallyWired
class CodeQaAnalysisService
    constructor(
        private val deps: CodeQaAnalysisServiceDeps,
        private val clock: ClockPort,
    ) : CodeQaAnalysisUseCase,
        StaleWorkHealer {
        private val codeQaRepo get() = deps.codeQaRepo
        private val mrFetchPort get() = deps.mrFetchPort
        private val mrSuggestionPort get() = deps.mrSuggestionPort
        private val gitlabConfigRepo get() = deps.gitlabConfigRepo
        private val githubConfigRepo get() = deps.githubConfigRepo
        private val jiraTicketImplementationService get() = deps.jiraTicketImplementationService
        private val copilotPort get() = deps.copilotPort
        private val aiUsageContext get() = deps.aiUsageContext
        private val i18n get() = deps.i18n
        private val startupHealer get() = deps.startupHealer
        private val integrationTestService get() = deps.integrationTestService
        private val asyncExecutor get() = deps.asyncExecutor
        private val streamingRegistry get() = deps.streamingRegistry
        private val promptAssembler get() = deps.promptAssembler
        private val log = LoggerFactory.getLogger(CodeQaAnalysisService::class.java)

        override fun getStreamingOutput(id: Long): String? = streamingRegistry.getBuffer(id)

        override fun registerStreamingChangeListener(
            id: Long,
            listener: () -> Unit,
        ): () -> Unit = streamingRegistry.registerChangeListener(id, listener)

        override fun unregisterStreamingChangeListener(
            id: Long,
            listener: () -> Unit,
        ) = streamingRegistry.unregisterChangeListener(id, listener)

        override fun heal() {
            resetStaleAnalyses()
        }

        fun resetStaleAnalyses() =
            startupHealer.heal(
                logTag = "MR-Analyse",
                failedStatus = AnalysisStatus.CANCELLED,
                findStale = {
                    codeQaRepo
                        .findAllByOrderByCreatedAtDesc()
                        .filter { it.status == AnalysisStatus.RUNNING || it.status == AnalysisStatus.PENDING }
                },
                markFailed = {
                    it.status = AnalysisStatus.CANCELLED
                    it.errorMessage = i18n.t("mr_analysis.be_aborted_restart")
                    it.completedAt = clock.localDateTime()
                },
                save = { codeQaRepo.saveCodeQaAnalysis(it) },
            )

        override fun startAnalysis(
            mrUrl: String,
            jiraTicketKey: String?,
            jiraConfigId: Long?,
            gitConfigId: Long?,
            gitPlatform: String?,
            additionalInstructions: String?,
            integrationTestEnabled: Boolean,
        ): CodeQaAnalysis {
            val entity =
                CodeQaAnalysis(
                    mrUrl = mrUrl.trim(),
                    jiraTicketKey = jiraTicketKey?.trim()?.takeIf { it.isNotBlank() },
                    jiraConfigId = jiraConfigId,
                    gitConfigId = gitConfigId,
                    gitPlatform = gitPlatform?.let { CodeHostingPlatform.fromWire(it).id.takeIf { id -> CodeHostingPlatform.allIds.contains(id) } },
                    additionalInstructions = additionalInstructions?.trim()?.takeIf { it.isNotBlank() },
                    status = AnalysisStatus.PENDING,
                    currentStep = i18n.t("mr_analysis.be_step_starting"),
                    integrationTestEnabled = integrationTestEnabled,
                    integrationTestStatus = if (integrationTestEnabled) AnalysisStatus.PENDING else null,
                )
            val saved = codeQaRepo.saveCodeQaAnalysis(entity)
            asyncExecutor.submit("analysisTaskExecutor") { runAnalysis(saved.id) }
            return saved
        }

        override fun getIntegrationTestStreamingOutput(id: Long): String? = integrationTestService.getStreamingOutput(id)

        override fun getAnalysis(id: Long): CodeQaAnalysis? = codeQaRepo.findCodeQaAnalysisById(id)

        override fun getAllAnalyses(): List<CodeQaAnalysis> = codeQaRepo.findRecent(AnalysisConstants.MAX_RECENT_ANALYSES)

        override fun deleteAnalysis(id: Long) = codeQaRepo.deleteCodeQaAnalysisById(id)

        override fun deleteAllAnalyses() = codeQaRepo.deleteAllCodeQaAnalyses()

        override fun cancelAnalysis(id: Long) {
            val analysis = codeQaRepo.findCodeQaAnalysisById(id) ?: return
            if (analysis.status == AnalysisStatus.RUNNING || analysis.status == AnalysisStatus.PENDING) {
                analysis.status = AnalysisStatus.CANCELLED
                analysis.errorMessage = i18n.t("mr_analysis.be_cancelled")
                analysis.completedAt = clock.localDateTime()
                codeQaRepo.saveCodeQaAnalysis(analysis)
            }
        }

        override fun postSuggestion(
            analysisId: Long,
            filePath: String,
            lineNumber: Int,
            suggestedCode: String,
        ): String {
            val analysis =
                codeQaRepo.findCodeQaAnalysisById(analysisId)
                    ?: error("Analysis $analysisId not found")
            val headSha =
                analysis.diffHeadSha
                    ?: error("No diff SHA stored for analysis $analysisId – was the MR fetched successfully?")
            return mrSuggestionPort.postSuggestion(
                MrSuggestionPort.PostRequest(
                    mrUrl = analysis.mrUrl,
                    platform = analysis.gitPlatform ?: "",
                    gitConfigId = analysis.gitConfigId,
                    gitlabConfigs = gitlabConfigRepo.findByActiveTrue(),
                    githubConfigs = githubConfigRepo.findAllGithubRepoConfigs(),
                    diffBaseSha = analysis.diffBaseSha.orEmpty(),
                    diffStartSha = analysis.diffStartSha.orEmpty(),
                    diffHeadSha = headSha,
                    filePath = filePath,
                    lineNumber = lineNumber,
                    suggestedCode = suggestedCode,
                ),
            )
        }

        fun runAnalysis(analysisId: Long) {
            var analysis = codeQaRepo.findCodeQaAnalysisById(analysisId) ?: return
            analysis.status = AnalysisStatus.RUNNING
            appendLog(analysis, i18n.t("mr_analysis.be_log_started"))
            analysis = codeQaRepo.saveCodeQaAnalysis(analysis)

            var mrData: MrFetchPort.MrData? = null
            try {
                val fetched = runMrFetchPhase(analysis)
                analysis = fetched.analysis
                mrData = fetched.mrData

                analysis = runJiraKeyAutoDetectionPhase(analysis, mrData)

                val jiraResult = runJiraFetchPhase(analysis)
                analysis = jiraResult.analysis
                val jiraText = jiraResult.text

                val aiResult = runAiAnalysisPhase(analysisId, analysis, mrData, jiraText)

                analysis = runFinalisePhase(analysisId, analysis, aiResult)

                log.info("[MR-Analysis] Analysis {} completed", analysisId)

                if (analysis.integrationTestEnabled) {
                    log.info("[MR-Analysis] Starting integration tests for analysis {}", analysisId)
                    integrationTestService.runIntegrationTests(analysisId, mrData)
                }
            } catch (e: Exception) {
                streamingRegistry.clear(analysisId)
                if (e is EntityConcurrentlyModifiedException) {
                    log.warn("[MR-Analysis] Analysis {} was modified/deleted externally – discarding background result", analysisId)
                    return
                }
                handleAnalysisFailure(analysisId, analysis, e)
            } finally {
                aiUsageContext.clearContext()
            }
        }

        private data class MrFetchPhaseResult(
            val analysis: CodeQaAnalysis,
            val mrData: MrFetchPort.MrData,
        )

        private fun runMrFetchPhase(analysis: CodeQaAnalysis): MrFetchPhaseResult {
            analysis.currentStep = i18n.t("mr_analysis.be_step_fetching_mr")
            appendLog(analysis, i18n.t("mr_analysis.be_log_fetching_mr", analysis.mrUrl))
            var mutable = codeQaRepo.saveCodeQaAnalysis(analysis)

            val configs = resolveGitConfigs(mutable)
            val mrData =
                when (val fetchResult = mrFetchPort.fetchMrData(mutable.mrUrl, configs.gitlab, configs.github)) {
                    is MrFetchPort.MrFetchResult.Success -> fetchResult.data
                    is MrFetchPort.MrFetchResult.Failure -> throw IllegalStateException(i18n.t("mr_analysis.be_err_mr_not_found"))
                }

            appendLog(mutable, i18n.t("mr_analysis.be_log_mr_loaded", mrData.platform, mrData.title))
            if (mrData.diffHeadSha.isNotBlank()) {
                mutable.diffBaseSha = mrData.diffBaseSha.takeIf { it.isNotBlank() }
                mutable.diffStartSha = mrData.diffStartSha.takeIf { it.isNotBlank() }
                mutable.diffHeadSha = mrData.diffHeadSha
            }
            mutable = codeQaRepo.saveCodeQaAnalysis(mutable)
            return MrFetchPhaseResult(mutable, mrData)
        }

        private fun runJiraKeyAutoDetectionPhase(
            analysis: CodeQaAnalysis,
            mrData: MrFetchPort.MrData,
        ): CodeQaAnalysis {
            if (!analysis.jiraTicketKey.isNullOrBlank() || analysis.jiraConfigId == null) return analysis
            val detected =
                listOf(mrData.title, mrData.sourceBranch, mrData.description)
                    .firstNotNullOfOrNull { JiraKeyUtils.KEY_REGEX.find(it)?.value }
                    ?: return analysis
            log.info("[MR-Analysis] Jira key auto-detected: {}", detected)
            appendLog(analysis, i18n.t("mr_analysis.be_log_jira_auto_detected", detected))
            analysis.jiraTicketKey = detected
            return codeQaRepo.saveCodeQaAnalysis(analysis)
        }

        private data class JiraFetchPhaseResult(
            val analysis: CodeQaAnalysis,
            val text: String?,
        )

        private fun runJiraFetchPhase(analysis: CodeQaAnalysis): JiraFetchPhaseResult {
            val ticketKey = analysis.jiraTicketKey
            val configId = analysis.jiraConfigId
            if (ticketKey.isNullOrBlank() || configId == null) return JiraFetchPhaseResult(analysis, null)

            var mutable = analysis
            return try {
                mutable.currentStep = i18n.t("mr_analysis.be_step_fetching_jira")
                appendLog(mutable, i18n.t("mr_analysis.be_log_fetching_jira", ticketKey))
                mutable = codeQaRepo.saveCodeQaAnalysis(mutable)
                val text = jiraTicketImplementationService.fetchTicketAsText(ticketKey, configId)
                appendLog(mutable, i18n.t("mr_analysis.be_log_jira_loaded", ticketKey))
                JiraFetchPhaseResult(codeQaRepo.saveCodeQaAnalysis(mutable), text)
            } catch (e: Exception) {
                log.warn("[MR-Analysis] Jira ticket could not be loaded: {}", e.message)
                appendLog(mutable, i18n.t("mr_analysis.be_log_jira_failed", e.message ?: ""))
                JiraFetchPhaseResult(codeQaRepo.saveCodeQaAnalysis(mutable), null)
            }
        }

        private data class AiAnalysisPhaseResult(
            val text: String,
            val model: String,
        )

        private fun runAiAnalysisPhase(
            analysisId: Long,
            analysis: CodeQaAnalysis,
            mrData: MrFetchPort.MrData,
            jiraTicketText: String?,
        ): AiAnalysisPhaseResult {
            analysis.currentStep = i18n.t("mr_analysis.be_step_analyzing")
            appendLog(analysis, i18n.t("mr_analysis.be_log_ai_prompt"))
            codeQaRepo.saveCodeQaAnalysis(analysis)

            val prompt = promptAssembler.buildPrompt(mrData, jiraTicketText, analysis.additionalInstructions)
            val model = copilotPort.resolveModel()
            aiUsageContext.setContext("codeqa", analysisId)
            aiUsageContext.setModelOverride(model)
            streamingRegistry.setBuffer(analysisId, "")
            val text =
                copilotPort.executeWithStreamingPerLine(model, prompt, 600L, onPartialOutput = { partial ->
                    streamingRegistry.setBuffer(analysisId, partial)
                })
            streamingRegistry.clear(analysisId)
            return AiAnalysisPhaseResult(text, model)
        }

        private fun runFinalisePhase(
            analysisId: Long,
            analysis: CodeQaAnalysis,
            aiResult: AiAnalysisPhaseResult,
        ): CodeQaAnalysis {
            val currentState = codeQaRepo.findCodeQaAnalysisById(analysisId)
            if (currentState == null || currentState.status == AnalysisStatus.CANCELLED) {
                log.info("[MR-Analysis] Analysis {} was cancelled or deleted externally – discarding AI result", analysisId)
                return currentState ?: analysis
            }
            currentState.resultMarkdown = aiResult.text.trim()
            appendLog(currentState, i18n.t("mr_analysis.be_log_ai_completed", aiResult.text.length))
            currentState.status = AnalysisStatus.COMPLETED
            currentState.currentStep = i18n.t("mr_analysis.be_step_completed")
            currentState.completedAt = clock.localDateTime()
            if (analysis.diffHeadSha != null) {
                currentState.diffBaseSha = analysis.diffBaseSha
                currentState.diffStartSha = analysis.diffStartSha
                currentState.diffHeadSha = analysis.diffHeadSha
            }
            return codeQaRepo.saveCodeQaAnalysis(currentState)
        }

        private fun handleAnalysisFailure(
            analysisId: Long,
            analysis: CodeQaAnalysis,
            e: Exception,
        ) {
            log.error("[MR-Analysis] Error in analysis {}: {}", analysisId, e.message, e)
            val fresh = codeQaRepo.findCodeQaAnalysisById(analysisId) ?: return
            fresh.status = AnalysisStatus.FAILED
            fresh.errorMessage = i18n.translateError(e).take(2000)
            fresh.currentStep = i18n.t("mr_analysis.be_step_failed")
            appendLog(fresh, i18n.t("mr_analysis.be_log_error", e.message?.take(200) ?: ""))
            codeQaRepo.saveCodeQaAnalysis(fresh)
        }

        private fun resolveGitConfigs(analysis: CodeQaAnalysis): CodeHostingPlatform.GitConfigs {
            val allGitlab = gitlabConfigRepo.findByActiveTrue()
            val allGithub = githubConfigRepo.findAllGithubRepoConfigs().filter { it.active }
            val platform = CodeHostingPlatform.fromWire(analysis.gitPlatform)
            return platform.selectConfigs(analysis.gitConfigId, allGitlab, allGithub)
        }

        internal fun buildPrompt(
            mrData: MrFetchPort.MrData,
            jiraTicketText: String?,
            additionalInstructions: String? = null,
        ): String = promptAssembler.buildPrompt(mrData, jiraTicketText, additionalInstructions)

        private fun appendLog(
            analysis: CodeQaAnalysis,
            message: String,
        ) {
            val ts =
                clock
                    .localDateTime()
                    .format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("HH:mm:ss"),
                    )
            analysis.logOutput = "${analysis.logOutput}[$ts] $message\n"
        }
    }
