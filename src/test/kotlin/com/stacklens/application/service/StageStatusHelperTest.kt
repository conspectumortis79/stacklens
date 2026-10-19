package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StageStatusHelperTest {
    private fun newRun(jira: Boolean = false): AgentPipelineRun =
        AgentPipelineRun(
            id = 1L,
            rawText = "x",
            repoPath = null,
            featureSlug = "x",
            jiraTicketData = if (jira) "ticket" else null,
            createdAt = System.currentTimeMillis(),
        )

    @Test
    fun `flips a running stage to ERROR`() {
        val run = newRun(jira = true)
        run.currentStage = "nora"
        run.nora.status = StageStatus.RUNNING

        StageStatusHelper.markCurrentRunningAsFailed(run)

        assertEquals(StageStatus.ERROR, run.nora.status)
    }

    @Test
    fun `does nothing when stage is not RUNNING`() {
        val run = newRun()
        run.currentStage = "cole"
        run.cole.status = StageStatus.DONE

        StageStatusHelper.markCurrentRunningAsFailed(run)

        assertEquals(StageStatus.DONE, run.cole.status)
    }

    @Test
    fun `does nothing when currentStage is null`() {
        val run = newRun()
        run.currentStage = null
        run.nora.status = StageStatus.RUNNING

        StageStatusHelper.markCurrentRunningAsFailed(run)

        assertEquals(StageStatus.RUNNING, run.nora.status)
    }

    @Test
    fun `does nothing for an unknown stage name`() {
        val run = newRun()
        run.currentStage = "zoe"
        run.nora.status = StageStatus.RUNNING

        StageStatusHelper.markCurrentRunningAsFailed(run)

        assertEquals(StageStatus.RUNNING, run.nora.status)
    }

    @Test
    fun `flips every known stage`() {
        val run = newRun(jira = true)
        val all =
            listOf(
                "lena" to run.lena,
                "nora" to run.nora,
                "ivan" to run.ivan,
                "cole" to run.cole,
                "max" to run.max,
                "finn" to run.finn,
                "faye" to run.faye,
                "vera" to run.vera,
                "tess" to run.tess,
                "syd" to run.syd,
                "quinn" to run.quinn,
                "sage" to run.sage,
                "rex" to run.rex,
            )
        for ((name, stage) in all) {
            stage.status = StageStatus.RUNNING
            run.currentStage = name
            StageStatusHelper.markCurrentRunningAsFailed(run)
            assertEquals(StageStatus.ERROR, stage.status, "stage $name must be ERROR")
        }
    }
}
