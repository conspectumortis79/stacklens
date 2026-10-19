package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.LoggingFixStageColumn
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.HashCollisions
import com.stacklens.testsupport.PrivateInvocation
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class LoggingFixStageSlotCoverageTest {
    private val stages =
        listOf(
            LoggingFixDiagnoseStage.STAGE_NAME,
            LoggingFixPlanStage.STAGE_NAME,
            LoggingFixCodeStage.STAGE_NAME,
            LoggingFixTestsStage.STAGE_NAME,
        )

    private class Buffers {
        val diagnose = StringBuilder()
        val plan = StringBuilder()
        val code = StringBuilder()
        val tests = StringBuilder()

        fun forName(stage: String): StringBuilder =
            when (stage) {
                LoggingFixDiagnoseStage.STAGE_NAME -> diagnose
                LoggingFixPlanStage.STAGE_NAME -> plan
                LoggingFixCodeStage.STAGE_NAME -> code
                else -> tests
            }
    }

    private val stageOutputReader =
        LoggingFixStageOutputReader(mock<LoggingFixPort>(), mock<LocalRepoConfigPort>(), catalogBackedLoggingFixMarkers()) { "" }

    private fun serviceWith(updater: LoggingFixPartialUpdatePort) =
        LoggingFixService(
            repository = mock<LoggingFixPort>(),
            partialUpdater = updater,
            localRepoConfigRepo = mock<LocalRepoConfigPort>(),
            aiUsageContext = mock<AiUsageContextPort>(),
            diagnoseStage = mock<LoggingFixDiagnoseStage>(),
            planStage = mock<LoggingFixPlanStage>(),
            codeStage = mock<LoggingFixCodeStage>(),
            testsStage = mock<LoggingFixTestsStage>(),
            tx = mock<TransactionPort>(),
            markers = catalogBackedLoggingFixMarkers(),
            fileWriter = JavaFileSystemAdapter(),
            workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun runState(buffers: Buffers): Any {
        val type = Class.forName("com.stacklens.application.service.logging.LoggingFixService\$RunState")
        val constructor = type.declaredConstructors.first { it.parameterCount == 9 }
        constructor.isAccessible = true
        return constructor.newInstance(
            LoggingFixAnalysis(id = 1L, featureSlug = "slug", logText = "log", selectedCategories = "cat"),
            false,
            emptyList<ProposedFile>(),
            null,
            "",
            buffers.diagnose,
            buffers.plan,
            buffers.code,
            buffers.tests,
        )
    }

    private fun persist(
        updater: LoggingFixPartialUpdatePort,
        buffers: Buffers,
        stage: String,
        snapshot: String,
    ) {
        PrivateInvocation.invoke(serviceWith(updater), "persistStagePartial", runState(buffers), stage, snapshot)
    }

    @Test
    fun `every stage appends its delta into its own column and buffer`() {
        val columns =
            listOf(
                LoggingFixStageColumn.DIAGNOSE,
                LoggingFixStageColumn.PLAN,
                LoggingFixStageColumn.CODE,
                LoggingFixStageColumn.TESTS,
            )

        for ((index, stage) in stages.withIndex()) {
            val updater = mock<LoggingFixPartialUpdatePort>()
            val buffers = Buffers()

            persist(updater, buffers, stage, "teil-1")

            verify(updater).appendStageOutput(1L, columns[index], "teil-1")
            assertEquals("teil-1", buffers.forName(stage).toString())
            assertEquals(0, buffers.forName(stages[(index + 1) % stages.size]).length)
        }
    }

    @Test
    fun `a snapshot that is no continuation replaces the stored stage output`() {
        for (stage in stages) {
            val updater = mock<LoggingFixPartialUpdatePort>()
            val buffers = Buffers()
            buffers.forName(stage).append("altes")

            persist(updater, buffers, stage, "ganz anders")

            assertEquals("ganz anders", buffers.forName(stage).toString())
            verify(updater, never()).appendStageOutput(any(), any(), any())
            when (stage) {
                LoggingFixDiagnoseStage.STAGE_NAME -> verify(updater).updateStageDiagnose(1L, "ganz anders")
                LoggingFixPlanStage.STAGE_NAME -> verify(updater).updateStagePlan(1L, "ganz anders")
                LoggingFixCodeStage.STAGE_NAME -> verify(updater).updateStageCode(1L, "ganz anders")
                else -> verify(updater).updateStageTests(1L, "ganz anders")
            }
        }
    }

    @Test
    fun `an unchanged snapshot leaves the database alone`() {
        val updater = mock<LoggingFixPartialUpdatePort>()
        val buffers = Buffers()
        buffers.plan.append("gleich")

        persist(updater, buffers, LoggingFixPlanStage.STAGE_NAME, "gleich")

        verifyNoInteractions(updater)
        assertEquals("gleich", buffers.plan.toString())
    }

    @Test
    fun `a stage name outside the known four is dropped`() {
        val updater = mock<LoggingFixPartialUpdatePort>()
        val buffers = Buffers()

        persist(updater, buffers, "irgendwas", "text")

        verifyNoInteractions(updater)
        assertTrue(buffers.plan.isEmpty() && buffers.diagnose.isEmpty() && buffers.code.isEmpty() && buffers.tests.isEmpty())
    }

    @Test
    fun `a stage name that only collides in hash code is dropped too`() {
        for (stage in stages) {
            val collision = HashCollisions.sharingHashCodeOf(stage)
            assertNotEquals(stage, collision)
            val updater = mock<LoggingFixPartialUpdatePort>()
            val buffers = Buffers()

            persist(updater, buffers, collision, "text")

            verifyNoInteractions(updater)
            assertTrue(buffers.forName(stage).isEmpty())
        }
    }

    @Test
    fun `badges of in flight stages follow the stage order`() {
        val service = serviceWith(mock<LoggingFixPartialUpdatePort>())

        assertEquals("PENDING", status(LoggingFixTestsStage.STAGE_NAME, LoggingFixDiagnoseStage.STAGE_NAME, "x"))
        assertEquals("RUNNING", status(LoggingFixDiagnoseStage.STAGE_NAME, LoggingFixDiagnoseStage.STAGE_NAME, null))
        assertEquals("DONE", status(LoggingFixDiagnoseStage.STAGE_NAME, LoggingFixPlanStage.STAGE_NAME, "text"))
        assertEquals("RUNNING", status(LoggingFixDiagnoseStage.STAGE_NAME, LoggingFixPlanStage.STAGE_NAME, "  "))
        assertEquals("RUNNING", status("unbekannt", LoggingFixDiagnoseStage.STAGE_NAME, "x"))
        assertEquals("RUNNING", status(LoggingFixDiagnoseStage.STAGE_NAME, "unbekannt", "x"))
    }

    private fun status(
        name: String,
        currentStage: String,
        output: String?,
    ): Any? = PrivateInvocation.invoke(stageOutputReader, "inFlightStageStatus", name, currentStage, output)
}
