
package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.StageStatus

internal object StageStatusHelper {
    fun markCurrentRunningAsFailed(run: AgentPipelineRun) {
        val stage = run.stageByName(run.currentStage) ?: return
        if (stage.status == StageStatus.RUNNING) {
            stage.status = StageStatus.ERROR
        }
    }
}
