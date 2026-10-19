package com.stacklens.application.service

import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.out.PptxStoragePort
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.service.ai.AiCaller
import com.stacklens.application.service.review.PptxAnalysisOrchestrator
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ApplicationTransactional
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.ReviewAnalysisConfig
import org.slf4j.LoggerFactory

@ApplicationService
@ManuallyWired
class ReviewAnalysisService
    constructor(
        private val deps: ReviewAnalysisServiceDeps,
        private val pptxStorage: PptxStoragePort,
        private val pptxOrchestrator: PptxAnalysisOrchestrator,
        private val aiCaller: AiCaller,
        private val transactionPort: TransactionPort,
        private val clock: ClockPort,
    ) : ReviewAnalysisUseCase,
        StaleWorkHealer {
        private val analysisRepo get() = deps.analysisRepo
        private val configRepo get() = deps.configRepo
        private val jiraConfigRepo get() = deps.jiraConfigRepo
        private val sprintHttpClient get() = deps.sprintHttpClient
        private val copilotCliService get() = deps.copilotCliService
        private val aiUsageContext get() = deps.aiUsageContext
        private val promptBuilder get() = deps.promptBuilder
        private val jsonCodec get() = deps.jsonCodec
        private val i18n get() = deps.i18n
        private val asyncExecutor get() = deps.asyncExecutor
        private val copilotConfig get() = deps.copilotConfig
        private val startupHealer get() = deps.startupHealer
        private val log = LoggerFactory.getLogger(ReviewAnalysisService::class.java)

        private fun t(
            key: String,
            vararg args: Any,
        ): String = i18n.tFor(copilotCliService.resolveLanguage(), "review_analysis.$key", *args)

        override fun heal() {
            resetStaleAnalyses()
        }

        fun resetStaleAnalyses() =
            startupHealer.heal(
                logTag = "Sprint-Analyse",
                failedStatus = AnalysisStatus.CANCELLED,
                findStale = {
                    analysisRepo
                        .findAllByOrderByCreatedAtDesc()
                        .filter { it.status == AnalysisStatus.RUNNING || it.status == AnalysisStatus.PENDING }
                },
                markFailed = {
                    it.status = AnalysisStatus.CANCELLED
                    it.errorMessage = t("be_aborted_restart")
                    it.completedAt = clock.localDateTime()
                },
                save = { analysisRepo.saveReviewAnalysis(it) },
            )

        @ApplicationTransactional
        override fun startAnalysis(
            jiraSprintUrl: String,
            jiraConfigId: Long,
            analysisInstructions: String,
        ): ReviewAnalysis {
            val entity =
                ReviewAnalysis(
                    jiraSprintUrl = jiraSprintUrl.trim(),
                    jiraConfigId = jiraConfigId,
                    analysisInstructions = analysisInstructions.trim(),
                    status = AnalysisStatus.PENDING,
                    currentStep = t("be_step_starting"),
                )
            val saved = analysisRepo.saveReviewAnalysis(entity)

            val savedConfig =
                configRepo.findReviewAnalysisConfig()
                    ?: ReviewAnalysisConfig()
            savedConfig.jiraSprintUrl = jiraSprintUrl.trim()
            savedConfig.jiraConfigId = jiraConfigId
            savedConfig.analysisInstructions = analysisInstructions.trim()
            savedConfig.updatedAt = clock.localDateTime()
            configRepo.saveReviewAnalysisConfig(savedConfig)

            val id = saved.id
            transactionPort.afterCommit {
                asyncExecutor.submit("analysisTaskExecutor") { runAnalysis(id) }
            }
            return saved
        }

        @ApplicationTransactional
        override fun startAnalysisWithPptx(
            jiraSprintUrl: String,
            jiraConfigId: Long,
            analysisInstructions: String,
            pptxFileName: String,
            pptxBytes: ByteArray,
        ): ReviewAnalysis {
            val saved = startAnalysis(jiraSprintUrl, jiraConfigId, analysisInstructions)
            saved.pptxFileName = pptxFileName
            pptxStorage.saveUpload(saved.id, pptxFileName, pptxBytes)
            return analysisRepo.saveReviewAnalysis(saved)
        }

        @ApplicationTransactional
        override fun startAnalysisWithPptxPath(
            jiraSprintUrl: String,
            jiraConfigId: Long,
            analysisInstructions: String,
            pptxServerPath: String,
        ): ReviewAnalysis {
            val saved = startAnalysis(jiraSprintUrl, jiraConfigId, analysisInstructions)
            saved.pptxServerPath = pptxServerPath
            saved.pptxFileName = java.io.File(pptxServerPath).name
            val copy = pptxStorage.importFromServer(saved.id, pptxServerPath)
            saved.pptxServerPath = copy.absolutePath
            return analysisRepo.saveReviewAnalysis(saved)
        }

        override fun getAnalysis(id: Long): ReviewAnalysis? = analysisRepo.findReviewAnalysisById(id)

        override fun getAllAnalyses(): List<ReviewAnalysis> = analysisRepo.findRecentReviewAnalyses(AnalysisConstants.MAX_RECENT_ANALYSES)

        override fun deleteAnalysis(id: Long) = analysisRepo.deleteReviewAnalysisById(id)

        override fun deleteAllAnalyses() = analysisRepo.deleteAllReviewAnalyses()

        override fun jiraConfigExists(id: Long): Boolean = jiraConfigRepo.jiraConfigExistsById(id)

        override fun firstActiveJiraConfigId(): Long? = jiraConfigRepo.findByActiveTrue().firstOrNull()?.id

        override fun resolveLanguage(): String = copilotCliService.resolveLanguage()

        fun runAnalysis(analysisId: Long) {
            var analysis = analysisRepo.findReviewAnalysisById(analysisId) ?: return
            analysis.status = AnalysisStatus.RUNNING
            appendLog(analysis, t("be_log_started"))
            analysis = analysisRepo.saveReviewAnalysis(analysis)

            try {
                val jiraConfig =
                    jiraConfigRepo.findJiraConfigById(
                        analysis.jiraConfigId
                            ?: throw IllegalStateException(t("be_err_jira_config_not_found", "null")),
                    ) ?: throw IllegalStateException(t("be_err_jira_config_not_found", analysis.jiraConfigId.toString()))

                analysis.currentStep = "FETCHING_SPRINT"
                appendLog(analysis, t("be_log_fetching_sprint"))
                analysis = analysisRepo.saveReviewAnalysis(analysis)

                val (sprintInfo, issues) = sprintHttpClient.fetchSprintData(jiraConfig, analysis.jiraSprintUrl)
                analysis.sprintInfoJson = jsonCodec.encode(sprintInfo)
                appendLog(analysis, t("be_log_sprint_loaded", sprintInfo.name, sprintInfo.state))
                analysis = analysisRepo.saveReviewAnalysis(analysis)

                analysis.currentStep = "FETCHING_ISSUES"
                appendLog(analysis, t("be_log_fetching_issues"))
                analysis = analysisRepo.saveReviewAnalysis(analysis)

                analysis.sprintIssuesJson = jsonCodec.encode(issues)
                appendLog(analysis, t("be_log_issues_loaded", issues.size))
                analysis = analysisRepo.saveReviewAnalysis(analysis)

                if (issues.isEmpty()) {
                    throw IllegalStateException(t("be_err_no_tickets"))
                }

                analysis.currentStep = "ANALYZING"
                appendLog(analysis, t("be_log_ai_prompt"))
                analysis = analysisRepo.saveReviewAnalysis(analysis)

                val lang = copilotCliService.resolveLanguage()
                val model = copilotCliService.resolveModel()
                val prompt = buildPrompt(lang, sprintInfo, issues, analysis.analysisInstructions)

                aiUsageContext.setContext("review", analysisId)
                aiUsageContext.setModelOverride(model)

                log.debug("[SprintAnalyse] Sending prompt to Copilot CLI ({} chars)", prompt.length)

                val result =
                    copilotCliService.execute(model, prompt, copilotConfig.reviewAnalysisTimeoutSeconds)

                analysis.resultMarkdown = result.trim()
                appendLog(analysis, t("be_log_ai_completed", result.length))

                if (!analysis.pptxFileName.isNullOrBlank() || !analysis.pptxServerPath.isNullOrBlank()) {
                    analysis.currentStep = "RENDERING_PPTX_HTML"
                    analysis = analysisRepo.saveReviewAnalysis(analysis)
                    runCatching {
                        pptxOrchestrator.runPptxAnalysis(
                            analysis = analysis,
                            sprintInfo = sprintInfo,
                            issues = issues,
                            lang = lang,
                            model = model,
                            aiCaller = aiCaller,
                        )
                    }.onFailure { log.warn("PPTX analysis failed for {}: {}", analysisId, it.message) }
                    analysis = analysisRepo.saveReviewAnalysis(analysis)
                }

                analysis.status = AnalysisStatus.COMPLETED
                analysis.currentStep = t("be_step_completed")
                analysis.completedAt = clock.localDateTime()
                analysisRepo.saveReviewAnalysis(analysis)

                runCatching {
                    deps.knowledgeMapper.fromReviewAnalysis(analysis)?.let { candidate ->
                        deps.knowledgePublication.publish(candidate)
                    }
                }.onFailure { log.warn("Knowledge publication failed for review {}: {}", analysisId, it.message) }

                log.info("[SprintAnalyse] Analysis {} completed", analysisId)
                aiUsageContext.clearContext()
            } catch (e: Exception) {
                handleRunFailure(analysisId, e)
                aiUsageContext.clearContext()
            }
        }

        private fun handleRunFailure(
            analysisId: Long,
            cause: Exception,
        ) {
            val survivor = runCatching { analysisRepo.findReviewAnalysisById(analysisId) }.getOrNull()
            if (survivor == null) {
                log.warn(
                    "[SprintAnalyse] Analysis {} was deleted while the run was active – result dropped ({}: {})",
                    analysisId,
                    cause.javaClass.simpleName,
                    cause.message ?: "",
                )
                return
            }
            log.error("[SprintAnalyse] Error in analysis {}: {}", analysisId, cause.message, cause)
            markAnalysisFailed(analysisId, survivor, cause)
        }

        private fun markAnalysisFailed(
            analysisId: Long,
            reloaded: ReviewAnalysis,
            cause: Exception,
        ) {
            val updated = reloaded
            updated.status = AnalysisStatus.FAILED
            updated.errorMessage = i18n.translateError(cause).take(2000)
            updated.currentStep = t("be_step_failed")
            appendLog(updated, t("be_log_error", cause.message?.take(200) ?: ""))

            if (!updated.pptxFileName.isNullOrBlank() || !updated.pptxServerPath.isNullOrBlank()) {
                runCatching {
                    pptxOrchestrator.runPptxAnalysis(
                        analysis = updated,
                        sprintInfo = null,
                        issues = emptyList(),
                        lang = copilotCliService.resolveLanguage(),
                        model = copilotCliService.resolveModel(),
                        aiCaller = aiCaller,
                    )
                }.onFailure { log.warn("PPTX HTML render (fallback) failed for {}: {}", analysisId, it.message) }
            }
            runCatching { analysisRepo.saveReviewAnalysis(updated) }
                .onFailure { log.warn("SprintAnalyse save failed for {}: {}", analysisId, it.message) }
        }

        internal fun buildPrompt(
            lang: String,
            sprintInfo: SprintInfo,
            issues: List<SprintIssue>,
            analysisInstructions: String,
        ): String = promptBuilder.buildPrompt(lang, sprintInfo, issues, analysisInstructions)

        private fun appendLog(
            analysis: ReviewAnalysis,
            text: String,
        ) {
            val entries: MutableList<String> =
                try {
                    analysis.processLogJson?.let {
                        jsonCodec.decodeList(it, String::class).toMutableList()
                    } ?: mutableListOf()
                } catch (_: Exception) {
                    mutableListOf()
                }
            entries.add(text)
            analysis.processLogJson = jsonCodec.encode(entries)
        }
    }
