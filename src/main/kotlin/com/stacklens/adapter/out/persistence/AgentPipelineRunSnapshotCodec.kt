package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.AgentStage
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.time.ClockPort
import org.springframework.stereotype.Component

@Component
class AgentPipelineRunSnapshotCodec(
    private val clock: ClockPort,
) {
    fun toSnapshot(run: AgentPipelineRun): Map<String, Any?> =
        mapOf(
            "id" to run.id,
            "rawText" to run.rawText,
            "repoPath" to run.repoPath,
            "featureSlug" to run.featureSlug,
            "jiraTicketData" to run.jiraTicketData,
            "databaseConfigIds" to run.databaseConfigIds,
            "dbSchema" to run.dbSchema,
            "status" to run.status.name,
            "currentStage" to run.currentStage,
            "errorMessage" to run.errorMessage,
            "cancelled" to run.cancelled,
            "detectedTicketKey" to run.detectedTicketKey,
            "gitBranch" to run.gitBranch,
            "gitLog" to run.gitLog,
            "gitRoot" to run.gitRoot,
            "baseBranch" to run.baseBranch,
            "baseSha" to run.baseSha,
            "preExistingUntracked" to run.preExistingUntracked,
            "createdAt" to run.createdAt,
            "filesAutoWritten" to run.filesAutoWritten,
            "autoWrittenFiles" to run.autoWrittenFiles,
            "autoDeletedFiles" to run.autoDeletedFiles,
            "autoWriteErrors" to run.autoWriteErrors,
            "stages" to run.stagesByName.entries.associate { (name, stage) -> name to stageSnapshot(stage) },
        )

    @Suppress("UNCHECKED_CAST")
    fun fromSnapshot(snapshot: Map<String, Any?>): AgentPipelineRun? {
        val id = (snapshot["id"] as? Number)?.toLong() ?: return null
        val rawText = snapshot["rawText"] as? String ?: return null
        val featureSlug = snapshot["featureSlug"] as? String ?: return null

        val run =
            AgentPipelineRun(
                id = id,
                rawText = rawText,
                repoPath = snapshot["repoPath"] as? String,
                featureSlug = featureSlug,
                jiraTicketData = snapshot["jiraTicketData"] as? String,
                createdAt = (snapshot["createdAt"] as? Number)?.toLong() ?: clock.epochMillis(),
                databaseConfigIds = databaseConfigIdsFrom(snapshot),
            )
        applyRunState(run, snapshot)
        applyStages(run, snapshot["stages"] as? Map<*, *>)
        return restoreInterrupted(run)
    }

    private fun stageSnapshot(stage: AgentStage): Map<String, String> = mapOf("status" to stage.status.name, "output" to stage.output)

    private fun databaseConfigIdsFrom(snapshot: Map<String, Any?>): List<Long> {
        val current =
            (snapshot["databaseConfigIds"] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toLong() }
        if (!current.isNullOrEmpty()) return current
        val legacyId = (snapshot["databaseConfigId"] as? Number)?.toLong()
        return if (legacyId != null) listOf(legacyId) else emptyList()
    }

    private fun applyRunState(
        run: AgentPipelineRun,
        snapshot: Map<String, Any?>,
    ) {
        run.status =
            runCatching { PipelineStatus.valueOf(snapshot["status"] as String) }
                .getOrElse { PipelineStatus.ERROR }
        run.currentStage = snapshot["currentStage"] as? String
        run.errorMessage = snapshot["errorMessage"] as? String
        run.cancelled = snapshot["cancelled"] as? Boolean ?: false
        run.detectedTicketKey = snapshot["detectedTicketKey"] as? String
        run.gitBranch = snapshot["gitBranch"] as? String ?: ""
        run.gitLog = snapshot["gitLog"] as? String ?: ""
        run.gitRoot = snapshot["gitRoot"] as? String ?: ""
        run.baseBranch = snapshot["baseBranch"] as? String ?: ""
        run.baseSha = snapshot["baseSha"] as? String ?: ""
        run.preExistingUntracked = stringList(snapshot["preExistingUntracked"])
        run.dbSchema = snapshot["dbSchema"] as? String ?: ""
        run.filesAutoWritten = snapshot["filesAutoWritten"] as? Boolean ?: false
        run.autoWrittenFiles = stringList(snapshot["autoWrittenFiles"])
        run.autoDeletedFiles = stringList(snapshot["autoDeletedFiles"])
        run.autoWriteErrors = stringList(snapshot["autoWriteErrors"])
    }

    private fun stringList(value: Any?): List<String> = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun applyStages(
        run: AgentPipelineRun,
        stagesSnapshot: Map<*, *>?,
    ) {
        stagesSnapshot?.forEach { (key, value) ->
            val stage = run.stageByName(key as? String) ?: return@forEach
            val stageValues = value as? Map<*, *> ?: return@forEach
            stage.status =
                runCatching { StageStatus.valueOf(stageValues["status"] as String) }
                    .getOrElse { StageStatus.PENDING }
            stage.output = stageValues["output"] as? String ?: ""
        }
    }

    private fun restoreInterrupted(run: AgentPipelineRun): AgentPipelineRun {
        if (run.status == PipelineStatus.RUNNING || run.status == PipelineStatus.PENDING) {
            run.status = PipelineStatus.ERROR
            run.errorMessage = (run.errorMessage ?: "") + " [Pipeline beim letzten Start unterbrochen.]"
        }
        return run
    }
}
