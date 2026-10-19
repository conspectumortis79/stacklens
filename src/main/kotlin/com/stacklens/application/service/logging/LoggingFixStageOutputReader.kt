package com.stacklens.application.service.logging

import com.stacklens.application.port.inbound.logging.StageOutputs
import com.stacklens.application.port.inbound.logging.StageState
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.application.service.logging.LoggingFixCodeStage.Companion.STAGE_NAME as CODE_STAGE
import com.stacklens.application.service.logging.LoggingFixDiagnoseStage.Companion.STAGE_NAME as DIAGNOSE_STAGE
import com.stacklens.application.service.logging.LoggingFixPlanStage.Companion.STAGE_NAME as PLAN_STAGE
import com.stacklens.application.service.logging.LoggingFixTestsStage.Companion.STAGE_NAME as TESTS_STAGE

class LoggingFixStageOutputReader(
    private val repository: LoggingFixPort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val markers: LoggingFixMarkers,
    private val currentStageOf: (Long) -> String,
) {
    fun getStageOutputs(id: Long): StageOutputs? {
        val analysis = repository.findLoggingFixAnalysisById(id) ?: return null
        val currentStage = currentStageOf(id)
        return StageOutputs(
            id = analysis.id,
            status = analysis.status,
            currentStage = currentStage,
            repoPath = analysis.localRepoConfigId?.let { localRepoConfigRepo.findLocalRepoConfigById(it)?.path },
            errorMessage = analysis.errorMessage,
            stages =
                mapOf(
                    DIAGNOSE_STAGE to stageState(DIAGNOSE_STAGE, "① Diagnose — Root Cause", analysis.stageDiagnose, analysis, currentStage),
                    PLAN_STAGE to stageState(PLAN_STAGE, "② Architektur & Coding-Guidelines", analysis.stagePlan, analysis, currentStage),
                    CODE_STAGE to stageState(CODE_STAGE, "③ Code-Fix", analysis.stageCode, analysis, currentStage),
                    TESTS_STAGE to stageState(TESTS_STAGE, "④ Unit- & Systemtests", analysis.stageTests, analysis, currentStage),
                ),
        )
    }

    private fun stageState(
        name: String,
        label: String,
        output: String?,
        analysis: LoggingFixAnalysis,
        currentStage: String,
    ): StageState {
        val status =
            when {
                output != null && output.isNotBlank() && isSkipMarker(output) -> "SKIPPED"
                analysis.status == LoggingFixAnalysis.STATUS_RUNNING && currentStage.isNotBlank() ->
                    inFlightStageStatus(name, currentStage, output)
                output.isNullOrBlank() && analysis.status == LoggingFixAnalysis.STATUS_PENDING -> "PENDING"
                output.isNullOrBlank() && analysis.status == LoggingFixAnalysis.STATUS_RUNNING -> "RUNNING"
                output.isNullOrBlank() && analysis.status == LoggingFixAnalysis.STATUS_ERROR -> "ERROR"
                output.isNullOrBlank() -> "PENDING"
                analysis.status == LoggingFixAnalysis.STATUS_RUNNING -> "RUNNING"
                analysis.status == LoggingFixAnalysis.STATUS_DONE -> "DONE"
                analysis.status == LoggingFixAnalysis.STATUS_CANCELLED -> "CANCELLED"
                analysis.status == LoggingFixAnalysis.STATUS_ERROR -> "ERROR"
                else -> "PENDING"
            }
        return StageState(name = name, label = label, status = status, output = output ?: "")
    }

    private fun inFlightStageStatus(
        name: String,
        currentStage: String,
        output: String?,
    ): String {
        val index = STAGE_ORDER.indexOf(name)
        val running = STAGE_ORDER.indexOf(currentStage)
        if (index < 0 || running < 0) {
            return "RUNNING"
        }
        return when {
            index < running -> if (output.isNullOrBlank()) "RUNNING" else "DONE"
            index == running -> "RUNNING"
            else -> "PENDING"
        }
    }

    private fun isSkipMarker(output: String): Boolean = markers.isSkipMarker(output)

    companion object {
        private val STAGE_ORDER: List<String> = listOf(DIAGNOSE_STAGE, PLAN_STAGE, CODE_STAGE, TESTS_STAGE)
    }
}
