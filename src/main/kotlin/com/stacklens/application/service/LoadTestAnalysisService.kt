package com.stacklens.application.service

import com.stacklens.application.port.inbound.loadtest.K6StatusResult
import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.LoadTestAnalysis

@ApplicationService
@ManuallyWired
class LoadTestAnalysisService
    constructor(
        private val deps: LoadTestAnalysisServiceDeps,
        private val clock: ClockPort,
    ) : LoadTestAnalysisUseCase,
        StaleWorkHealer {
        private val analysisRepo get() = deps.analysisRepo
        private val copilotPort get() = deps.copilotPort
        private val i18n get() = deps.i18n
        private val startupHealer get() = deps.startupHealer
        private val aiRunner get() = deps.aiRunner
        private val k6Controller get() = deps.k6Controller

        override fun heal() {
            resetStaleAnalyses()
        }

        fun resetStaleAnalyses() =
            startupHealer.heal(
                logTag = "Lasttest-Analyse",
                failedStatus = AnalysisStatus.CANCELLED,
                findStale = {
                    analysisRepo
                        .findAllLoadTestAnalysesOrderByCreatedAtDesc()
                        .filter { it.status == AnalysisStatus.RUNNING || it.status == AnalysisStatus.PENDING }
                },
                markFailed = {
                    it.status = AnalysisStatus.CANCELLED
                    it.errorMessage = i18n.t("load_test.be_aborted_restart")
                    it.completedAt = clock.localDateTime()
                },
                save = { analysisRepo.saveLoadTestAnalysis(it) },
            )

        override fun getAnalysis(analysisId: Long): LoadTestAnalysis? = analysisRepo.findLoadTestAnalysisById(analysisId)

        override fun getLatestGitlabAnalysis(
            gitlabConfigId: Long,
            gitlabProjectId: Long,
        ): LoadTestAnalysis? = analysisRepo.findTopByGitlabConfigIdAndGitlabProjectIdOrderByCreatedAtDesc(gitlabConfigId, gitlabProjectId)

        override fun getLatestLocalAnalysis(repoConfigId: Long): LoadTestAnalysis? = analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId)

        override fun getLatestGithubAnalysis(githubRepoConfigId: Long): LoadTestAnalysis? = analysisRepo.findTopByGithubRepoConfigIdOrderByCreatedAtDesc(githubRepoConfigId)

        override fun getAllAnalyses(): List<LoadTestAnalysis> = analysisRepo.findRecentLoadTestAnalyses(AnalysisConstants.MAX_RECENT_ANALYSES)

        override fun deleteAnalysis(analysisId: Long) {
            analysisRepo.deleteLoadTestAnalysisById(analysisId)
        }

        override fun cancelAnalysis(analysisId: Long) {
            val analysis = analysisRepo.findLoadTestAnalysisById(analysisId) ?: return
            if (analysis.status != AnalysisStatus.RUNNING && analysis.status != AnalysisStatus.PENDING) return
            analysis.status = AnalysisStatus.CANCELLED
            analysis.errorMessage = i18n.t("load_test.be_cancelled")
            analysis.completedAt = clock.localDateTime()
            analysisRepo.saveLoadTestAnalysis(analysis)
            aiRunner.cancelRunningThread(analysisId)
        }

        override fun startK6Script(
            analysisId: Long,
            bearerToken: String?,
            environmentName: String?,
            customScript: String?,
        ): K6StatusResult = k6Controller.startK6Script(analysisId, bearerToken, environmentName, customScript)

        override fun getK6Status(analysisId: Long): K6StatusResult = k6Controller.getK6Status(analysisId)

        override fun stopK6Script(analysisId: Long) = k6Controller.stopK6Script(analysisId)

        fun resolveToolBin(
            tool: String,
            extraCandidates: List<String> = emptyList(),
        ): String? = k6Controller.resolveToolBin(tool, extraCandidates)

        override fun startGitlabAnalysis(
            gitlabConfigId: Long,
            gitlabProjectId: Long,
            projectPath: String,
        ): LoadTestAnalysis {
            val analysis =
                analysisRepo.saveLoadTestAnalysis(
                    LoadTestAnalysis(
                        gitlabConfigId = gitlabConfigId,
                        gitlabProjectId = gitlabProjectId,
                        gitlabProjectPath = projectPath,
                        status = AnalysisStatus.PENDING,
                        modelUsed = "copilot-cli/${copilotPort.resolveModel()}",
                    ),
                )
            aiRunner.runGitlabAnalysis(analysis.id, gitlabConfigId, gitlabProjectId, projectPath)
            return analysis
        }

        override fun startLocalAnalysis(repoConfigId: Long): LoadTestAnalysis {
            val analysis =
                analysisRepo.saveLoadTestAnalysis(
                    LoadTestAnalysis(
                        repoConfigId = repoConfigId,
                        status = AnalysisStatus.PENDING,
                        modelUsed = "copilot-cli/${copilotPort.resolveModel()}",
                    ),
                )
            aiRunner.runLocalAnalysis(analysis.id, repoConfigId)
            return analysis
        }

        override fun startGithubAnalysis(githubRepoConfigId: Long): LoadTestAnalysis {
            val analysis =
                analysisRepo.saveLoadTestAnalysis(
                    LoadTestAnalysis(
                        githubRepoConfigId = githubRepoConfigId,
                        status = AnalysisStatus.PENDING,
                        modelUsed = "copilot-cli/${copilotPort.resolveModel()}",
                    ),
                )
            aiRunner.runGithubAnalysis(analysis.id, githubRepoConfigId)
            return analysis
        }
    }
