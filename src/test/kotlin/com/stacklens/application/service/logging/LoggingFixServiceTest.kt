package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.LoggingFixStageColumn
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class LoggingFixServiceTest {
    private val repository: StubRepository = StubRepository()
    private val partialUpdater: StubPartialUpdater = StubPartialUpdater(repository.store)
    private val localRepoConfigRepo: LocalRepoConfigPort = StubLocalRepoConfigRepo()
    private val aiUsageContext: AiUsageContextPort = RecordingAiUsageContext()
    private val diagnoseStage = DiagnoseStageSpy()
    private val planStage = PlanStageSpy()
    private val codeStage = CodeStageSpy()
    private val testsStage = TestsStageSpy()
    private val tx: TransactionPort = PassthroughTx()

    private val service =
        LoggingFixService(
            repository = repository,
            partialUpdater = partialUpdater,
            localRepoConfigRepo = localRepoConfigRepo,
            aiUsageContext = aiUsageContext,
            diagnoseStage = diagnoseStage,
            planStage = planStage,
            codeStage = codeStage,
            testsStage = testsStage,
            tx = tx,
            markers = catalogBackedLoggingFixMarkers(),
            fileWriter = JavaFileSystemAdapter(),
            workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `startFix without repo creates DONE analysis with all 4 stages run`() {
        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)

        val analysis = service.getAnalysis(id)
        assertNotNull(analysis)
        assertEquals(LoggingFixAnalysis.STATUS_DONE, analysis!!.status)
        assertEquals("diagnose-output", analysis.stageDiagnose)
        assertTrue(analysis.stagePlan!!.contains("übersprungen"))
        assertTrue(analysis.stageCode!!.contains("übersprungen"))
        assertTrue(analysis.stageTests!!.contains("übersprungen"))
    }

    @Test
    fun `getStageOutputs exposes the currently-running stage so the frontend can show a progress label`() {
        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)

        val out = service.getStageOutputs(id)
        assertNotNull(out)
        assertEquals(id, out!!.id)
        assertEquals("", out.currentStage, "currentStage must be cleared after a successful terminal status")
        assertEquals(LoggingFixAnalysis.STATUS_DONE, out.status)

        val stages = out.stages
        assertEquals("DONE", stages.getValue("diagnose").status)
        assertEquals("SKIPPED", stages.getValue("plan").status)
        assertEquals("SKIPPED", stages.getValue("code").status)
        assertEquals("SKIPPED", stages.getValue("tests").status)
        stages.values.forEach { st ->
            assertTrue(st.output.isNotBlank(), "stage ${st.name} must have output persisted")
        }
    }

    @Test
    fun `runPipeline opens an AiUsageContext scope around all 4 stages`() {
        val recorder = aiUsageContext as RecordingAiUsageContext
        recorder.scopes.clear()
        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)

        assertEquals(1, recorder.scopes.size, "exactly one withContext scope per pipeline run")
        assertEquals(LoggingFixService.AI_USAGE_KIND, recorder.scopes.single().kind)
        assertEquals(id, recorder.scopes.single().analysisId)
    }

    @Test
    fun `startFix with repo resolves path and writes proposed files to it`() {
        val tmp = Files.createTempDirectory("logging-fix-svc").toFile()
        try {
            (localRepoConfigRepo as StubLocalRepoConfigRepo).put(
                LocalRepoConfig(id = 99L, name = "x", path = tmp.absolutePath, scope = "LOGGING_ANALYSIS", active = true),
            )
            val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = 99L, language = "de")
            waitForCompletion(id)
            assertEquals(LoggingFixAnalysis.STATUS_DONE, service.getAnalysis(id)!!.status)
            val writeResult = service.writeFiles(id)
            assertTrue(writeResult.success)
            assertTrue(writeResult.writtenFiles.isNotEmpty())
            assertTrue(File(tmp, "src/main/kotlin/X.kt").exists())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `cancel during slow stage finishes as CANCELLED`() {
        diagnoseStage.delayMillis = 5_000
        try {
            val id = service.startFix("ERROR foo", emptyList(), localRepoConfigId = null, language = "de")
            Thread.sleep(300)
            val cancelled = service.cancel(id)
            waitForCompletion(id, timeoutMs = 6_000)
            assertEquals(
                com.stacklens.application.port.inbound.logging.CancelOutcome.Cancelled,
                cancelled,
            )
            assertEquals(LoggingFixAnalysis.STATUS_CANCELLED, service.getAnalysis(id)!!.status)
        } finally {
            diagnoseStage.delayMillis = 0
        }
    }

    @Test
    fun `cancel during blocking IO stage (no interrupt-check) still marks the analysis as CANCELLED`() {
        val blockingDiagnose = BlockingIOStageSpy()
        val blockingService =
            LoggingFixService(
                repository = repository,
                partialUpdater = partialUpdater,
                localRepoConfigRepo = localRepoConfigRepo,
                aiUsageContext = aiUsageContext,
                diagnoseStage = blockingDiagnose,
                planStage = planStage,
                codeStage = codeStage,
                testsStage = testsStage,
                tx = tx,
                markers = catalogBackedLoggingFixMarkers(),
                fileWriter = JavaFileSystemAdapter(),
                workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        blockingDiagnose.beginRelease.countDown()
        try {
            val id = blockingService.startFix("ERROR foo", emptyList(), localRepoConfigId = null, language = "de")
            Thread.sleep(300)
            val outcome = blockingService.cancel(id)
            assertEquals(
                com.stacklens.application.port.inbound.logging.CancelOutcome.Cancelled,
                outcome,
            )
            waitForCompletion(id, timeoutMs = 8_000)
            val analysis = blockingService.getAnalysis(id)!!
            assertEquals(
                LoggingFixAnalysis.STATUS_CANCELLED,
                analysis.status,
                "cancel must persist STATUS_CANCELLED even when the worker thread is stuck in blocking IO (regression for Abbrechen-does-nothing bug) — the isCancelled callback is checked between read attempts so the stage aborts without relying on Thread.interrupt() against a blocking BufferedReader.readLine()",
            )
        } finally {
            blockingDiagnose.mayFinish.countDown()
            blockingDiagnose.beginRelease.countDown()
        }
    }

    @Test
    fun `cancel aborts a streaming stage via the isCancelled callback (production-style stage)`() {
        val streamingStage =
            object : LoggingFixStage {
                @Volatile var receivedCancelSignal = false

                override fun execute(ctx: LoggingFixStageContext) {
                    try {
                        var i = 0
                        while (!ctx.isCancelled()) {
                            ctx.onPartial("diagnose", "chunk-$i\n")
                            i++
                            if (i % 1_000 == 0) Thread.sleep(20)
                        }
                    } finally {
                        receivedCancelSignal = ctx.isCancelled()
                    }
                }
            }
        val customService =
            LoggingFixService(
                repository = repository,
                partialUpdater = partialUpdater,
                localRepoConfigRepo = localRepoConfigRepo,
                aiUsageContext = aiUsageContext,
                diagnoseStage = streamingStage,
                planStage = planStage,
                codeStage = codeStage,
                testsStage = testsStage,
                tx = tx,
                markers = catalogBackedLoggingFixMarkers(),
                fileWriter = JavaFileSystemAdapter(),
                workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val id = customService.startFix("ERROR foo", emptyList(), localRepoConfigId = null, language = "de")
        Thread.sleep(500)
        val outcome = customService.cancel(id)
        assertEquals(
            com.stacklens.application.port.inbound.logging.CancelOutcome.Cancelled,
            outcome,
        )
        waitForCompletion(id, timeoutMs = 4_000)
        val analysis = customService.getAnalysis(id)!!
        assertEquals(
            LoggingFixAnalysis.STATUS_CANCELLED,
            analysis.status,
            "isCancelled must short-circuit the streaming stage so cancel propagates without waiting for the AI call to complete (regression for the Abbrechen button)",
        )
        assertTrue(streamingStage.receivedCancelSignal, "stage must observe ctx.isCancelled() flipping to true before returning")
    }

    @Test
    fun `cancel returns NotFound for unknown id`() {
        assertEquals(
            com.stacklens.application.port.inbound.logging.CancelOutcome.NotFound,
            service.cancel(99_999L),
        )
    }

    @Test
    fun `delete refuses running or pending analysis`() {
        val analysis =
            LoggingFixAnalysis(
                id = 7L,
                featureSlug = "x",
                logText = "x",
                selectedCategories = "",
                status = LoggingFixAnalysis.STATUS_RUNNING,
            )
        repository.put(analysis)
        assertEquals(
            com.stacklens.application.port.inbound.logging.DeleteOutcome.StillRunning,
            service.delete(7L),
        )
    }

    @Test
    fun `delete returns NotFound for unknown id`() {
        assertEquals(
            com.stacklens.application.port.inbound.logging.DeleteOutcome.NotFound,
            service.delete(99_999L),
        )
    }

    @Test
    fun `delete succeeds for finished analysis`() {
        val analysis =
            LoggingFixAnalysis(
                id = 8L,
                featureSlug = "x",
                logText = "x",
                selectedCategories = "",
                status = LoggingFixAnalysis.STATUS_DONE,
            )
        repository.put(analysis)
        assertEquals(
            com.stacklens.application.port.inbound.logging.DeleteOutcome.Deleted,
            service.delete(8L),
        )
    }

    @Test
    fun `getStageOutputs returns 4 stages with labels and outputs`() {
        val analysis =
            LoggingFixAnalysis(
                id = 9L,
                featureSlug = "x",
                logText = "x",
                selectedCategories = "",
                status = LoggingFixAnalysis.STATUS_DONE,
                stageDiagnose = "diag",
                stagePlan = "plan",
                stageCode = "code",
                stageTests = "tests",
                localRepoConfigId = null,
            )
        repository.put(analysis)
        val out = service.getStageOutputs(9L)!!
        assertEquals(4, out.stages.size)
        assertEquals("① Diagnose — Root Cause", out.stages["diagnose"]!!.label)
        assertEquals("diag", out.stages["diagnose"]!!.output)
        assertEquals("DONE", out.stages["diagnose"]!!.status)
    }

    @Test
    fun `writeFiles rejects unknown id`() {
        val result = service.writeFiles(999L)
        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `startFix with diagnosisIncomplete-short-circuit produces DONE with empty proposedFiles`() {
        diagnoseStage.injectDiagnosisIncomplete = true

        val id =
            service.startFix(
                logText = "INFO a\nINFO b\nINFO c\nINFO d\nERROR e",
                selectedCategories = listOf("error"),
                localRepoConfigId = null,
                language = "de",
            )
        waitForCompletion(id)

        val analysis = service.getAnalysis(id)
        assertNotNull(analysis)
        assertEquals(LoggingFixAnalysis.STATUS_DONE, analysis!!.status)
        assertTrue(
            analysis.stageDiagnose!!.contains("DIAGNOSIS_NOT_POSSIBLE"),
            "diagnose output must contain the marker; got=${analysis.stageDiagnose}",
        )
        assertTrue(analysis.stagePlan!!.contains("übersprungen"))
        assertTrue(analysis.stageCode!!.contains("übersprungen"))
        assertTrue(analysis.stageTests!!.contains("übersprungen"))
        val writeResult = service.writeFiles(id)
        assertFalse(
            writeResult.success,
            "writeFiles must refuse when no proposed files were produced by a short-circuited pipeline",
        )

        diagnoseStage.injectDiagnosisIncomplete = false
    }

    @Test
    fun `startFix preserves happy-path behaviour when diagnose does NOT flag incomplete`() {
        diagnoseStage.injectDiagnosisIncomplete = false

        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)
        val analysis = service.getAnalysis(id)!!
        assertEquals(LoggingFixAnalysis.STATUS_DONE, analysis.status)
        assertEquals("diagnose-output", analysis.stageDiagnose)
        assertTrue(analysis.stagePlan!!.contains("übersprungen"))
        assertTrue(analysis.stageCode!!.contains("übersprungen"))
        assertTrue(analysis.stageTests!!.contains("übersprungen"))
    }

    @Test
    fun `startFix routes every status update through the partial updater (no entity save on transitions)`() {
        partialUpdater.reset()
        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)

        val calls = partialUpdater.calls
        assertTrue(
            calls.any { it is StubPartialUpdater.UpdateStatus && it.id == id && it.status == LoggingFixAnalysis.STATUS_RUNNING },
            "expected a updateStatus(RUNNING) call, got=$calls",
        )
        assertTrue(
            calls.any {
                it is StubPartialUpdater.UpdateStatusAndCompletedAt &&
                    it.id == id &&
                    it.status == LoggingFixAnalysis.STATUS_DONE
            },
            "expected a updateStatusAndCompletedAt(DONE) call, got=$calls",
        )
        val savesForStatusTransitions =
            repository.saveCalls.count { it.id == id && it.status == LoggingFixAnalysis.STATUS_RUNNING }
        assertEquals(
            0,
            savesForStatusTransitions,
            "RUNNING transition must use the partial updater, not saveLoggingFixAnalysis",
        )
        val savesForDoneTransitions =
            repository.saveCalls.count { it.id == id && it.status == LoggingFixAnalysis.STATUS_DONE }
        assertEquals(
            0,
            savesForDoneTransitions,
            "DONE transition must use the partial updater, not saveLoggingFixAnalysis",
        )
    }

    @Test
    fun `startFix routes every streamed stage chunk through appendStageOutput and never through save`() {
        partialUpdater.reset()
        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)

        val appendCalls = partialUpdater.calls.filterIsInstance<StubPartialUpdater.AppendStageOutput>()
        assertTrue(
            appendCalls.isNotEmpty(),
            "streamed chunks must hit appendStageOutput, got=${partialUpdater.calls}",
        )
        assertTrue(
            appendCalls.any { it.id == id && it.column == LoggingFixStageColumn.DIAGNOSE },
            "expected at least one appendStageOutput(stageDiagnose) call for id=$id, got=$appendCalls",
        )
        val midPipelineSaves =
            repository.saveCalls.count { it.id == id } - 1
        assertEquals(
            0,
            midPipelineSaves,
            "mid-pipeline save calls must be 0 after the Hotspot-A fix; saw $midPipelineSaves",
        )
    }

    @Test
    fun `persistStagePartial survives a thrown partial-update and keeps the worker running`() {
        partialUpdater.failNextAppend = true

        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)

        val analysis = service.getAnalysis(id)!!
        assertEquals(
            LoggingFixAnalysis.STATUS_DONE,
            analysis.status,
            "pipeline must finish DONE even if a streaming chunk write fails",
        )
        assertTrue(
            partialUpdater.calls.any {
                it is StubPartialUpdater.UpdateStatusAndCompletedAt &&
                    it.id == id &&
                    it.status == LoggingFixAnalysis.STATUS_DONE
            },
            "DONE transition must still fire after a transient streaming write failure",
        )
        val midPipelineSaves = repository.saveCalls.count { it.id == id } - 1
        assertEquals(0, midPipelineSaves, "no mid-pipeline save calls permitted")
    }

    @Test
    fun `startFix with ERROR in any stage uses updateStatusWithErrorMessage and never save`() {
        codeStage.throwOnExecute = RuntimeException("boom from code stage")

        val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
        waitForCompletion(id)

        val analysis = service.getAnalysis(id)!!
        assertEquals(LoggingFixAnalysis.STATUS_ERROR, analysis.status)
        assertEquals("boom from code stage", analysis.errorMessage)
        assertTrue(
            partialUpdater.calls.any {
                it is StubPartialUpdater.UpdateStatusWithErrorMessage &&
                    it.id == id &&
                    it.status == LoggingFixAnalysis.STATUS_ERROR &&
                    it.errorMessage == "boom from code stage"
            },
            "ERROR transition must be persisted via updateStatusWithErrorMessage",
        )
        val errorSaveCalls =
            repository.saveCalls.count {
                it.id == id && it.status == LoggingFixAnalysis.STATUS_ERROR
            }
        assertEquals(0, errorSaveCalls, "ERROR transition must not go through saveLoggingFixAnalysis")

        codeStage.throwOnExecute = null
    }

    @Test
    fun `startFix cancellation flow uses updateStatusAndCompletedAt and never save`() {
        diagnoseStage.delayMillis = 2_000
        try {
            val id = service.startFix("ERROR foo", emptyList(), localRepoConfigId = null, language = "de")
            Thread.sleep(200)
            service.cancel(id)
            waitForCompletion(id, timeoutMs = 4_000)
            assertEquals(LoggingFixAnalysis.STATUS_CANCELLED, service.getAnalysis(id)!!.status)
            assertTrue(
                partialUpdater.calls.any {
                    it is StubPartialUpdater.UpdateStatusAndCompletedAt &&
                        it.id == id &&
                        it.status == LoggingFixAnalysis.STATUS_CANCELLED
                },
                "cancellation must be persisted via updateStatusAndCompletedAt",
            )
        } finally {
            diagnoseStage.delayMillis = 0
        }
    }

    @Test
    fun `startFix concurrent partial updates do not throw OptimisticLockException`() {
        partialUpdater.simulateSlowAppends = true
        try {
            val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
            waitForCompletion(id, timeoutMs = 5_000)
            assertEquals(LoggingFixAnalysis.STATUS_DONE, service.getAnalysis(id)!!.status)
            val appendCalls =
                partialUpdater.calls
                    .count { it is StubPartialUpdater.AppendStageOutput && it.id == id }
            assertTrue(appendCalls >= 4, "expected >=4 appendStageOutput calls, got $appendCalls")
        } finally {
            partialUpdater.simulateSlowAppends = false
        }
    }

    @Test
    fun `stage badges report per-stage progress instead of marking every stage RUNNING`() {
        val tmp = Files.createTempDirectory("logging-fix-progress").toFile()
        try {
            (localRepoConfigRepo as StubLocalRepoConfigRepo).put(
                LocalRepoConfig(id = 98L, name = "x", path = tmp.absolutePath, scope = "LOGGING_ANALYSIS", active = true),
            )
            val begun = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val gatedService =
                LoggingFixService(
                    repository = repository,
                    partialUpdater = partialUpdater,
                    localRepoConfigRepo = localRepoConfigRepo,
                    aiUsageContext = aiUsageContext,
                    diagnoseStage = diagnoseStage,
                    planStage = planStage,
                    codeStage = codeStage,
                    testsStage = GatedTestsStageSpy(begun, release),
                    tx = tx,
                    markers = catalogBackedLoggingFixMarkers(),
                    fileWriter = JavaFileSystemAdapter(),
                    workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
                    clock = com.stacklens.adapter.out.time.SystemClockPort,
                )
            val id = gatedService.startFix("ERROR foo", listOf("error"), localRepoConfigId = 98L, language = "de")
            try {
                assertTrue(begun.await(5, java.util.concurrent.TimeUnit.SECONDS), "tests stage never started")
                val stages = gatedService.getStageOutputs(id)!!.stages
                assertEquals("DONE", stages.getValue("diagnose").status, "diagnose already produced its output")
                assertEquals("DONE", stages.getValue("plan").status, "plan already produced its output")
                assertEquals("DONE", stages.getValue("code").status, "code already produced its output")
                assertEquals("RUNNING", stages.getValue("tests").status, "tests is the stage that is executing")
            } finally {
                release.countDown()
            }
            waitForCompletion(id)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `skipped stages stay SKIPPED after the pipeline reaches DONE`() {
        diagnoseStage.injectDiagnosisIncomplete = true
        try {
            val id = service.startFix("ERROR foo", listOf("error"), localRepoConfigId = null, language = "de")
            waitForCompletion(id)
            val stages = service.getStageOutputs(id)!!.stages
            assertEquals("SKIPPED", stages["diagnose"]!!.status)
            assertEquals("SKIPPED", stages["plan"]!!.status)
            assertEquals("SKIPPED", stages["code"]!!.status)
            assertEquals("SKIPPED", stages["tests"]!!.status)
        } finally {
            diagnoseStage.injectDiagnosisIncomplete = false
        }
    }

    @Test
    fun `streaming snapshots are stored once instead of being concatenated into the stage column`() {
        val snapshotService =
            LoggingFixService(
                repository = repository,
                partialUpdater = partialUpdater,
                localRepoConfigRepo = localRepoConfigRepo,
                aiUsageContext = aiUsageContext,
                diagnoseStage = SnapshotStageSpy(),
                planStage = planStage,
                codeStage = codeStage,
                testsStage = testsStage,
                tx = tx,
                markers = catalogBackedLoggingFixMarkers(),
                fileWriter = JavaFileSystemAdapter(),
                workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val id =
            snapshotService.startFix(
                "2025-01-15 ERROR boom\nWARN a\nINFO b\nERROR c\nat com.x.Y.z(X.java:1)",
                listOf("error"),
                localRepoConfigId = null,
                language = "de",
            )
        waitForCompletion(id)

        val analysis = service.getAnalysis(id)!!
        assertEquals("Hello world!", analysis.stageDiagnose)
    }

    @Test
    fun `a failed snapshot write is recovered by the next snapshot instead of losing text`() {
        partialUpdater.failNextAppend = true
        val snapshotService =
            LoggingFixService(
                repository = repository,
                partialUpdater = partialUpdater,
                localRepoConfigRepo = localRepoConfigRepo,
                aiUsageContext = aiUsageContext,
                diagnoseStage = SnapshotStageSpy(),
                planStage = planStage,
                codeStage = codeStage,
                testsStage = testsStage,
                tx = tx,
                markers = catalogBackedLoggingFixMarkers(),
                fileWriter = JavaFileSystemAdapter(),
                workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        val id =
            snapshotService.startFix(
                "2025-01-15 ERROR boom\nWARN a\nINFO b\nERROR c\nat com.x.Y.z(X.java:1)",
                listOf("error"),
                localRepoConfigId = null,
                language = "de",
            )
        waitForCompletion(id)

        assertEquals("Hello world!", service.getAnalysis(id)!!.stageDiagnose)
    }

    private fun waitForCompletion(
        id: Long,
        timeoutMs: Long = 5_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = service.getAnalysis(id)?.status
            if (s == LoggingFixAnalysis.STATUS_DONE ||
                s == LoggingFixAnalysis.STATUS_ERROR ||
                s == LoggingFixAnalysis.STATUS_CANCELLED
            ) {
                return
            }
            Thread.sleep(50)
        }
    }
}

private fun File.deleteRecursively() {
    if (isDirectory) listFiles()?.forEach { it.deleteRecursively() }
    delete()
}

private class SnapshotStageSpy : LoggingFixStage {
    override fun execute(ctx: LoggingFixStageContext) {
        ctx.onPartial("diagnose", "Hello")
        ctx.onPartial("diagnose", "Hello world")
        ctx.onPartial("diagnose", "Hello world!")
    }
}

private class GatedTestsStageSpy(
    private val begun: java.util.concurrent.CountDownLatch,
    private val release: java.util.concurrent.CountDownLatch,
) : LoggingFixStage {
    override fun execute(ctx: LoggingFixStageContext) {
        begun.countDown()
        release.await(5, java.util.concurrent.TimeUnit.SECONDS)
        ctx.testsOutput.append("tests-output")
        ctx.onPartial("tests", "tests-output")
    }
}

private open class StageSpy(
    var delayMillis: Long = 0,
) : LoggingFixStage {
    @Volatile var throwOnExecute: Exception? = null

    override fun execute(ctx: LoggingFixStageContext) {
        throwOnExecute?.let { throw it }
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedException("interrupted")
            }
        }
    }
}

private class BlockingIOStageSpy : LoggingFixStage {
    val beginRelease: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1)
    val mayFinish: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1)

    override fun execute(ctx: LoggingFixStageContext) {
        val piped = java.io.PipedWriter()
        val reader = java.io.BufferedReader(java.io.PipedReader(piped))
        beginRelease.countDown()
        try {
            while (true) {
                if (ctx.isCancelled()) throw InterruptedException("cancelled by isCancelled callback")
                val line = reader.readLine()
                if (line == null) return
                ctx.onPartial("diagnose", line)
                if (mayFinish.await(50, java.util.concurrent.TimeUnit.MILLISECONDS)) return
            }
        } finally {
            piped.close()
            reader.close()
        }
    }
}

private class DiagnoseStageSpy : StageSpy() {
    var injectDiagnosisIncomplete: Boolean = false

    override fun execute(ctx: LoggingFixStageContext) {
        super.execute(ctx)
        if (injectDiagnosisIncomplete) {
            val marker = "DIAGNOSIS_NOT_POSSIBLE: input contained no real log content"
            ctx.diagnoseOutput.append(marker)
            ctx.onPartial("diagnose", marker)
            ctx.diagnosisIncomplete = true
            return
        }
        ctx.diagnoseOutput.append("diagnose-output")
        ctx.onPartial("diagnose", "diagnose-output")
    }
}

private class PlanStageSpy : StageSpy() {
    override fun execute(ctx: LoggingFixStageContext) {
        super.execute(ctx)
        if (ctx.diagnosisIncomplete) {
            val marker = "[Plan-Stage übersprungen — Diagnose nicht möglich]"
            ctx.planOutput.append(marker)
            ctx.onPartial("plan", marker)
            return
        }
        if (ctx.repoRoot == null) {
            val marker = "[Plan-Stage übersprungen — kein Repository ausgewählt]"
            ctx.planOutput.append(marker)
            ctx.onPartial("plan", marker)
            return
        }
        ctx.planOutput.append("plan-output")
        ctx.onPartial("plan", "plan-output")
    }
}

private class CodeStageSpy : StageSpy() {
    override fun execute(ctx: LoggingFixStageContext) {
        super.execute(ctx)
        if (ctx.diagnosisIncomplete) {
            val marker = "[Code-Stage übersprungen — Diagnose nicht möglich]"
            ctx.codeOutput.append(marker)
            ctx.onPartial("code", marker)
            return
        }
        if (ctx.repoRoot == null) {
            val marker = "[Code-Stage übersprungen — kein Repository ausgewählt]"
            ctx.codeOutput.append(marker)
            ctx.onPartial("code", marker)
            return
        }
        ctx.codeOutput.append("### FILE: src/main/kotlin/X.kt\n### DESCRIPTION: x\nclass X")
        ctx.onPartial("code", "### FILE: src/main/kotlin/X.kt\n### DESCRIPTION: x\nclass X")
        ctx.proposedFiles.add(ProposedFile(path = "src/main/kotlin/X.kt", content = "class X", description = "x"))
    }
}

private class TestsStageSpy : StageSpy() {
    override fun execute(ctx: LoggingFixStageContext) {
        super.execute(ctx)
        if (ctx.diagnosisIncomplete) {
            val marker = "[Test-Stage übersprungen — Diagnose nicht möglich]"
            ctx.testsOutput.append(marker)
            ctx.onPartial("tests", marker)
            return
        }
        if (ctx.repoRoot == null) {
            val marker = "[Test-Stage übersprungen — kein Repository ausgewählt]"
            ctx.testsOutput.append(marker)
            ctx.onPartial("tests", marker)
            return
        }
        ctx.testsOutput.append("tests-output")
        ctx.onPartial("tests", "tests-output")
        ctx.proposedFiles.add(ProposedFile(path = "src/test/kotlin/XTest.kt", content = "class XTest", description = "test"))
    }
}

private class StubRepository : LoggingFixPort {
    val store: MutableMap<Long, LoggingFixAnalysis> = mutableMapOf()
    private var nextId = 0L
    val saveCalls: MutableList<SaveCall> = mutableListOf()

    data class SaveCall(
        val id: Long,
        val status: String,
    )

    override fun saveLoggingFixAnalysis(analysis: LoggingFixAnalysis): LoggingFixAnalysis {
        if (analysis.id == 0L) {
            nextId++
            val stored =
                LoggingFixAnalysis(
                    id = nextId,
                    featureSlug = analysis.featureSlug,
                    logText = analysis.logText,
                    selectedCategories = analysis.selectedCategories,
                    localRepoConfigId = analysis.localRepoConfigId,
                    language = analysis.language,
                    status = analysis.status,
                    errorMessage = analysis.errorMessage,
                    stageDiagnose = analysis.stageDiagnose,
                    stagePlan = analysis.stagePlan,
                    stageCode = analysis.stageCode,
                    stageTests = analysis.stageTests,
                    stageStatusJson = analysis.stageStatusJson,
                    createdAt = analysis.createdAt,
                    completedAt = analysis.completedAt,
                )
            saveCalls += SaveCall(stored.id, stored.status)
            store[stored.id] = stored
            return stored
        }
        saveCalls += SaveCall(analysis.id, analysis.status)
        store[analysis.id] = analysis
        return analysis
    }

    override fun findLoggingFixAnalysisById(id: Long): LoggingFixAnalysis? = store[id]

    override fun findRecentLoggingFixAnalyses(limit: Int): List<LoggingFixAnalysis> = store.values.sortedByDescending { it.id }.take(limit)

    override fun findAllByOrderByCreatedAtDesc(): List<LoggingFixAnalysis> = store.values.sortedByDescending { it.createdAt }

    override fun deleteLoggingFixAnalysisById(id: Long) {
        store.remove(id)
    }

    override fun deleteAllLoggingFixAnalyses() {
        store.clear()
    }

    override fun deleteFinishedLoggingFixAnalyses(): Int {
        val finished = store.filterValues { it.status != LoggingFixAnalysis.STATUS_RUNNING && it.status != LoggingFixAnalysis.STATUS_PENDING }
        store.keys.removeAll(finished.keys)
        return finished.size
    }

    fun put(analysis: LoggingFixAnalysis) {
        store[analysis.id] = analysis
    }
}

private class StubPartialUpdater(
    val store: MutableMap<Long, LoggingFixAnalysis>,
) : LoggingFixPartialUpdatePort {
    sealed interface Call {
        val id: Long
    }

    data class UpdateStageDiagnose(
        override val id: Long,
        val value: String?,
    ) : Call

    data class UpdateStagePlan(
        override val id: Long,
        val value: String?,
    ) : Call

    data class UpdateStageCode(
        override val id: Long,
        val value: String?,
    ) : Call

    data class UpdateStageTests(
        override val id: Long,
        val value: String?,
    ) : Call

    data class AppendStageOutput(
        override val id: Long,
        val column: LoggingFixStageColumn,
        val chunk: String,
    ) : Call

    data class UpdateStatus(
        override val id: Long,
        val status: String,
    ) : Call

    data class UpdateStatusWithErrorMessage(
        override val id: Long,
        val status: String,
        val errorMessage: String?,
        val completedAt: java.time.LocalDateTime,
    ) : Call

    data class UpdateStatusAndCompletedAt(
        override val id: Long,
        val status: String,
        val completedAt: java.time.LocalDateTime,
    ) : Call

    val calls: MutableList<Call> = mutableListOf()

    var failNextAppend: Boolean = false

    var simulateSlowAppends: Boolean = false

    fun reset() {
        calls.clear()
        failNextAppend = false
        simulateSlowAppends = false
    }

    private fun mutate(
        id: Long,
        transform: (LoggingFixAnalysis) -> Unit,
    ) {
        val entity = store[id] ?: return
        transform(entity)
    }

    override fun updateStageDiagnose(
        id: Long,
        value: String?,
    ): Int {
        calls += UpdateStageDiagnose(id, value)
        mutate(id) { it.stageDiagnose = value }
        return 1
    }

    override fun updateStagePlan(
        id: Long,
        value: String?,
    ): Int {
        calls += UpdateStagePlan(id, value)
        mutate(id) { it.stagePlan = value }
        return 1
    }

    override fun updateStageCode(
        id: Long,
        value: String?,
    ): Int {
        calls += UpdateStageCode(id, value)
        mutate(id) { it.stageCode = value }
        return 1
    }

    override fun updateStageTests(
        id: Long,
        value: String?,
    ): Int {
        calls += UpdateStageTests(id, value)
        mutate(id) { it.stageTests = value }
        return 1
    }

    override fun appendStageOutput(
        id: Long,
        column: LoggingFixStageColumn,
        chunk: String,
    ): Int {
        if (simulateSlowAppends) {
            try {
                Thread.sleep(5)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedException("interrupted")
            }
        }
        if (failNextAppend) {
            failNextAppend = false
            throw RuntimeException("simulated transient DB failure on appendStageOutput")
        }
        calls += AppendStageOutput(id, column, chunk)
        mutate(id) {
            when (column) {
                LoggingFixStageColumn.DIAGNOSE ->
                    it.stageDiagnose = (it.stageDiagnose ?: "") + chunk
                LoggingFixStageColumn.PLAN ->
                    it.stagePlan = (it.stagePlan ?: "") + chunk
                LoggingFixStageColumn.CODE ->
                    it.stageCode = (it.stageCode ?: "") + chunk
                LoggingFixStageColumn.TESTS ->
                    it.stageTests = (it.stageTests ?: "") + chunk
            }
        }
        return 1
    }

    override fun updateStatus(
        id: Long,
        status: String,
    ): Int {
        calls += UpdateStatus(id, status)
        mutate(id) { it.status = status }
        return 1
    }

    override fun updateStatusWithErrorMessage(
        id: Long,
        status: String,
        errorMessage: String?,
        completedAt: java.time.LocalDateTime,
    ): Int {
        calls += UpdateStatusWithErrorMessage(id, status, errorMessage, completedAt)
        mutate(id) {
            it.status = status
            it.errorMessage = errorMessage
            it.completedAt = completedAt
        }
        return 1
    }

    override fun updateStatusAndCompletedAt(
        id: Long,
        status: String,
        completedAt: java.time.LocalDateTime,
    ): Int {
        calls += UpdateStatusAndCompletedAt(id, status, completedAt)
        mutate(id) {
            it.status = status
            it.completedAt = completedAt
        }
        return 1
    }
}

private class StubLocalRepoConfigRepo : LocalRepoConfigPort {
    private val store = mutableMapOf<Long, LocalRepoConfig>()

    override fun findAllByScope(scope: String): List<LocalRepoConfig> = store.values.filter { it.scope == scope }

    override fun findFirstByPathAndScope(
        path: String,
        scope: String,
    ): LocalRepoConfig? = store.values.firstOrNull { it.path == path && it.scope == scope }

    override fun findLocalRepoConfigById(id: Long): LocalRepoConfig? = store[id]

    override fun findAllLocalRepoConfigs(): List<LocalRepoConfig> = store.values.toList()

    override fun saveLocalRepoConfig(config: LocalRepoConfig): LocalRepoConfig {
        store[config.id] = config
        return config
    }

    override fun localRepoConfigExistsById(id: Long): Boolean = store.containsKey(id)

    override fun deleteLocalRepoConfigById(id: Long) {
        store.remove(id)
    }

    override fun deleteAllLocalRepoConfigs() {
        store.clear()
    }

    fun put(config: LocalRepoConfig) {
        store[config.id] = config
    }
}

private class PassthroughTx : TransactionPort {
    override fun <T> inTransaction(block: () -> T): T = block()

    override fun afterCommit(block: () -> Unit) {}
}

private class RecordingAiUsageContext : AiUsageContextPort {
    data class Scope(
        val kind: String,
        val analysisId: Long,
    )

    val scopes: MutableList<Scope> = mutableListOf()
    val active: ThreadLocal<Scope> = ThreadLocal()

    override fun setContext(
        kind: String,
        analysisId: Long,
    ) {
        active.set(Scope(kind, analysisId))
    }

    override fun clearContext() {
        active.remove()
    }

    override fun <T> withContext(
        kind: String,
        analysisId: Long,
        block: () -> T,
    ): T {
        setContext(kind, analysisId)
        scopes += Scope(kind, analysisId)
        return try {
            block()
        } finally {
            clearContext()
        }
    }

    override fun addFilterSavings(tokens: Long) {}

    override fun setModelOverride(model: String) {}
}
