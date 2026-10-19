package com.stacklens.application.service.logging

import com.stacklens.application.port.inbound.logging.LoggingFixStartRequest
import com.stacklens.application.port.inbound.logging.LoggingFixUseCase
import com.stacklens.application.port.inbound.logging.StageOutputs
import com.stacklens.application.port.inbound.logging.WriteFilesPreview
import com.stacklens.application.port.inbound.logging.WriteFilesResult
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.LoggingFixStageColumn
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.process.WorkerStarterPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.domain.pipeline.CancellationSignal
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.stacklens.application.service.logging.LoggingFixCodeStage.Companion.STAGE_NAME as CODE_STAGE
import com.stacklens.application.service.logging.LoggingFixDiagnoseStage.Companion.STAGE_NAME as DIAGNOSE_STAGE
import com.stacklens.application.service.logging.LoggingFixPlanStage.Companion.STAGE_NAME as PLAN_STAGE
import com.stacklens.application.service.logging.LoggingFixTestsStage.Companion.STAGE_NAME as TESTS_STAGE

@ApplicationService
@ManuallyWired
class LoggingFixService(
    private val repository: LoggingFixPort,
    private val partialUpdater: LoggingFixPartialUpdatePort,
    private val localRepoConfigRepo: LocalRepoConfigPort,
    private val aiUsageContext: AiUsageContextPort,
    private val diagnoseStage: LoggingFixStage,
    private val planStage: LoggingFixStage,
    private val codeStage: LoggingFixStage,
    private val testsStage: LoggingFixStage,
    private val markers: LoggingFixMarkers,
    private val fileWriter: FileReadWritePort,
    private val tx: TransactionPort,
    private val clock: ClockPort,
    private val workerStarter: WorkerStarterPort,
) : LoggingFixUseCase {
    private val log = LoggerFactory.getLogger(LoggingFixService::class.java)

    private val runs = ConcurrentHashMap<Long, RunState>()

    private val stageOutputReader =
        LoggingFixStageOutputReader(repository, localRepoConfigRepo, markers) { id -> runs[id]?.currentStage ?: "" }

    private val fileWriterService =
        LoggingFixFileWriter(repository, localRepoConfigRepo, fileWriter, clock) { id ->
            val state = runs[id]
            LoggingFixFileState(
                analysis = state?.analysis ?: repository.findLoggingFixAnalysisById(id),
                proposedFiles = state?.proposedFiles ?: emptyList(),
            )
        }

    private data class RunState(
        val analysis: LoggingFixAnalysis,
        @Volatile var cancelled: Boolean = false,
        @Volatile var proposedFiles: List<ProposedFile> = emptyList(),
        @Volatile var cancellation: CancellationSignal? = null,
        @Volatile var currentStage: String = "",
        val stageDiagnoseBuffer: StringBuilder = StringBuilder(),
        val stagePlanBuffer: StringBuilder = StringBuilder(),
        val stageCodeBuffer: StringBuilder = StringBuilder(),
        val stageTestsBuffer: StringBuilder = StringBuilder(),
    )

    override fun startFix(
        logText: String,
        selectedCategories: List<String>,
        localRepoConfigId: Long?,
        language: String,
    ): Long {
        val repoPath = localRepoConfigId?.let { localRepoConfigRepo.findLocalRepoConfigById(it)?.path }
        val analysis =
            LoggingFixAnalysis(
                featureSlug = fileWriterService.generateSlug(logText),
                logText = logText,
                selectedCategories = selectedCategories.joinToString(","),
                localRepoConfigId = localRepoConfigId,
                language = language,
                status = LoggingFixAnalysis.STATUS_PENDING,
            )
        val saved = repository.saveLoggingFixAnalysis(analysis)
        val dbId = saved.id
        val state = RunState(analysis = saved)
        runs[dbId] = state
        val worker = workerStarter.start("logging-fix-$dbId") { runPipeline(state, repoPath) }
        state.cancellation = CancellationSignal { worker.interrupt() }
        return dbId
    }

    private fun runPipeline(
        state: RunState,
        repoPath: String?,
    ) {
        val analysis = state.analysis
        partialUpdater.updateStatus(analysis.id, LoggingFixAnalysis.STATUS_RUNNING)
        val repoRoot = repoPath?.let { File(it).takeIf { path -> fileWriter.exists(path) } }
        aiUsageContext.withContext(AI_USAGE_KIND, analysis.id) {
            runPipelineStages(state, analysis, repoPath, repoRoot)
        }
    }

    private fun runPipelineStages(
        state: RunState,
        analysis: LoggingFixAnalysis,
        repoPath: String?,
        repoRoot: File?,
    ) {
        val ctx =
            LoggingFixStageContext(
                logText = analysis.logText,
                selectedCategories = analysis.selectedCategories.split(",").filter { it.isNotBlank() },
                repoPath = repoPath,
                repoRoot = repoRoot,
                language = analysis.language,
                onPartial = { stage, chunk -> persistStagePartial(state, stage, chunk) },
                isCancelled = { state.cancelled },
            )
        try {
            state.currentStage = DIAGNOSE_STAGE
            runStage(state, ctx, DIAGNOSE_STAGE) { diagnoseStage.execute(it) }
            state.currentStage = PLAN_STAGE
            runStage(state, ctx, PLAN_STAGE) { planStage.execute(it) }
            state.currentStage = CODE_STAGE
            runStage(state, ctx, CODE_STAGE) { codeStage.execute(it) }
            state.currentStage = TESTS_STAGE
            runStage(state, ctx, TESTS_STAGE) { testsStage.execute(it) }
            state.currentStage = ""
            state.proposedFiles = ctx.proposedFiles.toList()
            partialUpdater.updateStatusAndCompletedAt(
                analysis.id,
                LoggingFixAnalysis.STATUS_DONE,
                clock.localDateTime(),
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            handleCancellation(state)
        } catch (e: Exception) {
            log.error("[LoggingFix {}] pipeline failed", analysis.id, e)
            val terminalStatus = if (state.cancelled) LoggingFixAnalysis.STATUS_CANCELLED else LoggingFixAnalysis.STATUS_ERROR
            partialUpdater.updateStatusWithErrorMessage(
                analysis.id,
                terminalStatus,
                e.message,
                clock.localDateTime(),
            )
        }
    }

    private fun runStage(
        state: RunState,
        ctx: LoggingFixStageContext,
        stageName: String,
        stage: LoggingFixStage,
    ) {
        if (state.cancelled) throw InterruptedException("cancelled before $stageName")
        log.info("[LoggingFix {}] stage {} starting", state.analysis.id, stageName)
        stage.execute(ctx)
        log.info("[LoggingFix {}] stage {} done", state.analysis.id, stageName)
    }

    private fun persistStagePartial(
        state: RunState,
        stage: String,
        snapshot: String,
    ) {
        val analysisId = state.analysis.id
        val slot = stageSlot(state, stage) ?: return
        try {
            val persisted = slot.buffer.toString()
            if (snapshot == persisted) {
                return
            }
            if (snapshot.startsWith(persisted)) {
                val delta = snapshot.substring(persisted.length)
                partialUpdater.appendStageOutput(analysisId, slot.column, delta)
                slot.buffer.append(delta)
            } else {
                slot.replace(analysisId, snapshot)
                slot.buffer.setLength(0)
                slot.buffer.append(snapshot)
            }
        } catch (e: Exception) {
            log.warn("[LoggingFix {}] persistStagePartial({}) failed: {}", analysisId, stage, e.message)
        }
    }

    private class StageSlot(
        val buffer: StringBuilder,
        val column: LoggingFixStageColumn,
        val replace: (Long, String) -> Unit,
    )

    private fun stageSlot(
        state: RunState,
        stage: String,
    ): StageSlot? =
        when (stage) {
            DIAGNOSE_STAGE ->
                StageSlot(state.stageDiagnoseBuffer, LoggingFixStageColumn.DIAGNOSE) { id, snapshot ->
                    partialUpdater.updateStageDiagnose(id, snapshot)
                }

            PLAN_STAGE ->
                StageSlot(state.stagePlanBuffer, LoggingFixStageColumn.PLAN) { id, snapshot ->
                    partialUpdater.updateStagePlan(id, snapshot)
                }

            CODE_STAGE ->
                StageSlot(state.stageCodeBuffer, LoggingFixStageColumn.CODE) { id, snapshot ->
                    partialUpdater.updateStageCode(id, snapshot)
                }

            TESTS_STAGE ->
                StageSlot(state.stageTestsBuffer, LoggingFixStageColumn.TESTS) { id, snapshot ->
                    partialUpdater.updateStageTests(id, snapshot)
                }

            else -> null
        }

    private fun handleCancellation(state: RunState) {
        val analysis = state.analysis
        partialUpdater.updateStatusAndCompletedAt(
            analysis.id,
            LoggingFixAnalysis.STATUS_CANCELLED,
            clock.localDateTime(),
        )
    }

    override fun getAnalysis(id: Long): LoggingFixAnalysis? = repository.findLoggingFixAnalysisById(id)

    override fun listRecent(limit: Int): List<LoggingFixAnalysis> = repository.findRecentLoggingFixAnalyses(limit)

    override fun cancel(id: Long): com.stacklens.application.port.inbound.logging.CancelOutcome {
        val state = runs[id] ?: return com.stacklens.application.port.inbound.logging.CancelOutcome.NotFound
        if (state.analysis.status == LoggingFixAnalysis.STATUS_DONE ||
            state.analysis.status == LoggingFixAnalysis.STATUS_ERROR ||
            state.analysis.status == LoggingFixAnalysis.STATUS_CANCELLED
        ) {
            return com.stacklens.application.port.inbound.logging.CancelOutcome.AlreadyFinished
        }
        state.cancelled = true
        state.cancellation?.cancelRequest()
        return com.stacklens.application.port.inbound.logging.CancelOutcome.Cancelled
    }

    override fun delete(id: Long): com.stacklens.application.port.inbound.logging.DeleteOutcome {
        val analysis = runs[id]?.analysis ?: repository.findLoggingFixAnalysisById(id) ?: return com.stacklens.application.port.inbound.logging.DeleteOutcome.NotFound
        if (analysis.status == LoggingFixAnalysis.STATUS_RUNNING ||
            analysis.status == LoggingFixAnalysis.STATUS_PENDING
        ) {
            return com.stacklens.application.port.inbound.logging.DeleteOutcome.StillRunning
        }
        tx.inTransaction { repository.deleteLoggingFixAnalysisById(analysis.id) }
        runs.remove(id)
        return com.stacklens.application.port.inbound.logging.DeleteOutcome.Deleted
    }

    override fun deleteAllFinished(): Int {
        val removed = tx.inTransaction { repository.deleteFinishedLoggingFixAnalyses() }
        return removed
    }

    override fun getStageOutputs(id: Long): StageOutputs? = stageOutputReader.getStageOutputs(id)

    override fun getWriteFilesPreview(id: Long): WriteFilesPreview? = fileWriterService.getWriteFilesPreview(id)

    override fun validateStartRequest(request: LoggingFixStartRequest): List<String> {
        val logText = request.logText
        val localRepoConfigId = request.localRepoConfigId
        if (logText.isBlank()) return listOf("logText is blank")
        if (localRepoConfigId != null) {
            val repo = localRepoConfigRepo.findLocalRepoConfigById(localRepoConfigId)
            if (repo == null) return listOf("repository not found: id=$localRepoConfigId")
            val path = repo.path
            if (path.isBlank()) return listOf("repository path is empty")
            val dir = File(path)
            if (!fileWriter.exists(dir) || !fileWriter.isDirectory(dir)) return listOf("repository path does not exist: $path")
        }
        return emptyList()
    }

    override fun writeFiles(id: Long): WriteFilesResult = fileWriterService.writeFiles(id)

    companion object {
        const val AI_USAGE_KIND: String = "logging_fix"
    }
}
