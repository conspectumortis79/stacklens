package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.logging.CancelOutcome
import com.stacklens.application.port.inbound.logging.DeleteOutcome
import com.stacklens.application.port.inbound.logging.LoggingFixStartRequest
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class LoggingFixServiceBranchTest {
    private val repository: LoggingFixPort = mock()
    private val partialUpdater: LoggingFixPartialUpdatePort = mock()
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()
    private val aiUsageContext: AiUsageContextPort = mock()
    private val diagnoseStage: LoggingFixStage = mock()
    private val planStage: LoggingFixStage = mock()
    private val codeStage: LoggingFixStage = mock()
    private val testsStage: LoggingFixStage = mock()
    private val tx: TransactionPort = mock()

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

    private fun injectRun(
        id: Long,
        analysis: LoggingFixAnalysis,
        proposedFiles: List<ProposedFile> = emptyList(),
    ) {
        val runStateClass = Class.forName("com.stacklens.application.service.logging.LoggingFixService\$RunState")
        val ctor = runStateClass.declaredConstructors.single { it.parameterCount == 9 }
        ctor.isAccessible = true
        val state = ctor.newInstance(analysis, false, proposedFiles, null, "", StringBuilder(), StringBuilder(), StringBuilder(), StringBuilder())
        if (proposedFiles.isNotEmpty()) {
            val pf = runStateClass.getDeclaredField("proposedFiles")
            pf.isAccessible = true
            pf.set(state, proposedFiles)
        }
        val runsField = LoggingFixService::class.java.getDeclaredField("runs")
        runsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val runs = runsField.get(service) as ConcurrentHashMap<Long, Any>
        runs[id] = state
    }

    private fun analysis(
        status: String,
        stageOutput: String? = null,
    ): LoggingFixAnalysis =
        LoggingFixAnalysis(
            id = 5L,
            featureSlug = "be",
            logText = "ERROR foo",
            selectedCategories = "error",
            localRepoConfigId = 7L,
            status = status,
            stageDiagnose = stageOutput,
        )

    @Test
    fun `listRecent delegates to repository`() {
        whenever(repository.findRecentLoggingFixAnalyses(5)).thenReturn(listOf(analysis("DONE")))
        assertEquals(1, service.listRecent(5).size)
    }

    @Test
    fun `cancel maps outcomes`() {
        assertEquals(CancelOutcome.NotFound, service.cancel(1L))
        injectRun(5L, analysis("RUNNING"))
        assertEquals(CancelOutcome.Cancelled, service.cancel(5L))
        injectRun(6L, analysis("DONE"))
        assertEquals(CancelOutcome.AlreadyFinished, service.cancel(6L))
        injectRun(7L, analysis("ERROR"))
        assertEquals(CancelOutcome.AlreadyFinished, service.cancel(7L))
        injectRun(8L, analysis("CANCELLED"))
        assertEquals(CancelOutcome.AlreadyFinished, service.cancel(8L))
    }

    @Test
    fun `delete maps outcomes`() {
        assertEquals(DeleteOutcome.NotFound, service.delete(1L))
        injectRun(5L, analysis("RUNNING"))
        assertEquals(DeleteOutcome.StillRunning, service.delete(5L))
        injectRun(6L, analysis("PENDING"))
        assertEquals(DeleteOutcome.StillRunning, service.delete(6L))
        injectRun(7L, analysis("DONE"))
        assertEquals(DeleteOutcome.Deleted, service.delete(7L))
        whenever(repository.findLoggingFixAnalysisById(9L)).thenReturn(analysis("DONE"))
        assertEquals(DeleteOutcome.Deleted, service.delete(9L))
    }

    @Test
    fun `deleteAllFinished returns removed count`() {
        whenever(tx.inTransaction(org.mockito.kotlin.any<() -> Int>())).thenAnswer {
            (it.getArgument(0) as () -> Int)()
        }
        whenever(repository.deleteFinishedLoggingFixAnalyses()).thenReturn(3)
        assertEquals(3, service.deleteAllFinished())
    }

    @Test
    fun `getStageOutputs returns null for missing analysis`() {
        whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(null)
        assertNull(service.getStageOutputs(5L))
    }

    @Test
    fun `stageState status mapping across statuses and outputs`() {
        val cases =
            listOf(
                Triple("PENDING", null, "PENDING"),
                Triple("RUNNING", null, "RUNNING"),
                Triple("ERROR", null, "ERROR"),
                Triple("DONE", null, "PENDING"),
                Triple("CANCELLED", null, "PENDING"),
                Triple("RUNNING", "x", "RUNNING"),
                Triple("DONE", "x", "DONE"),
                Triple("CANCELLED", "x", "CANCELLED"),
                Triple("ERROR", "x", "ERROR"),
                Triple("MYSTERY", "x", "PENDING"),
            )
        cases.forEach { (status, output, expected) ->
            whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(analysis(status, output))
            val out = service.getStageOutputs(5L)
            assertNotNull(out, "status=$status output=$output")
            assertEquals(expected, out!!.stages.getValue("diagnose").status, "status=$status output=$output")
        }
    }

    @Test
    fun `getStageOutputs exposes run state currentStage`() {
        injectRun(5L, analysis("RUNNING"))
        whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(analysis("RUNNING"))
        val runStateClass = Class.forName("com.stacklens.application.service.logging.LoggingFixService\$RunState")
        val currentStageField = runStateClass.getDeclaredField("currentStage")
        currentStageField.isAccessible = true
        val runsField = LoggingFixService::class.java.getDeclaredField("runs")
        runsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val runs = runsField.get(service) as ConcurrentHashMap<Long, Any>
        currentStageField.set(runs[5L], "plan")
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = "/repo"))
        val out = service.getStageOutputs(5L)
        assertEquals("plan", out!!.currentStage)
        assertEquals("/repo", out.repoPath)
    }

    @Test
    fun `getWriteFilesPreview uses running state files`() {
        injectRun(5L, analysis("RUNNING"), proposedFiles = listOf(ProposedFile(path = "a.java", content = "code", description = "desc")))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = "/repo"))
        val preview = service.getWriteFilesPreview(5L)
        assertNotNull(preview)
        assertEquals("/repo", preview!!.repoPath)
        assertEquals("code", preview.files.single().content)
    }

    @Test
    fun `getWriteFilesPreview falls back to analysis when no run`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = "/repo"))
        whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(analysis("DONE"))
        val preview = service.getWriteFilesPreview(5L)
        assertNotNull(preview)
        assertTrue(preview!!.files.isEmpty())
        whenever(repository.findLoggingFixAnalysisById(9L)).thenReturn(null)
        assertNull(service.getWriteFilesPreview(9L))
    }

    @Test
    fun `validateStartRequest covers all branches`() {
        assertEquals(listOf("logText is blank"), service.validateStartRequest(LoggingFixStartRequest(logText = "  ")))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(null)
        assertTrue(service.validateStartRequest(LoggingFixStartRequest(logText = "ERROR", localRepoConfigId = 7L)).first().contains("repository not found"))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = "  "))
        assertTrue(service.validateStartRequest(LoggingFixStartRequest(logText = "ERROR", localRepoConfigId = 7L)).first().contains("path is empty"))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = "/does/not/exist"))
        assertTrue(service.validateStartRequest(LoggingFixStartRequest(logText = "ERROR", localRepoConfigId = 7L)).first().contains("does not exist"))
    }

    @Test
    fun `validateStartRequest accepts valid repo`(
        @TempDir tmp: Path,
    ) {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = tmp.toString()))
        assertEquals(emptyList<String>(), service.validateStartRequest(LoggingFixStartRequest(logText = "ERROR", localRepoConfigId = 7L)))
    }

    @Test
    fun `writeFiles returns error when analysis missing`() {
        whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(null)
        assertEquals("Analyse nicht gefunden", service.writeFiles(5L).error)
    }

    @Test
    fun `writeFiles returns error when repo unavailable`() {
        whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(analysis("DONE", localRepoConfigId = null as Long?))
        assertEquals("Repository nicht verfügbar", service.writeFiles(5L).error)
    }

    @Test
    fun `writeFiles returns error when repo path missing`() {
        whenever(repository.findLoggingFixAnalysisById(5L)).thenReturn(analysis("DONE"))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = "/does/not/exist"))
        assertTrue(service.writeFiles(5L).error!!.contains("Repository-Pfad existiert nicht"))
    }

    @Test
    fun `writeFiles returns error when no files proposed`(
        @TempDir tmp: Path,
    ) {
        injectRun(5L, analysis("RUNNING"))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = tmp.toString()))
        assertEquals("Keine Dateien zum Schreiben erkannt", service.writeFiles(5L).error)
    }

    @Test
    fun `writeFiles writes proposed files`(
        @TempDir tmp: Path,
    ) {
        injectRun(
            5L,
            analysis("RUNNING"),
            proposedFiles = listOf(ProposedFile(path = "sub/a.java", content = "code", description = "desc")),
        )
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = tmp.toString()))
        val result = service.writeFiles(5L)
        assertEquals(true, result.success)
        assertEquals(listOf("sub/a.java"), result.writtenFiles)
        assertEquals("code", tmp.resolve("sub/a.java").toFile().readText())
    }

    @Test
    fun `writeFiles wraps write failure`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.resolve("blocked").toFile()
        dir.mkdirs()
        injectRun(
            5L,
            analysis("RUNNING"),
            proposedFiles = listOf(ProposedFile(path = "blocked", content = "code", description = "desc")),
        )
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = tmp.toString()))
        val result = service.writeFiles(5L)
        assertEquals(false, result.success)
        assertTrue(result.error!!.contains("Fehler beim Schreiben von blocked"))
    }

    @Test
    fun `writeFiles commits after successful writes`(
        @TempDir tmp: Path,
    ) {
        injectRun(
            5L,
            analysis("RUNNING"),
            proposedFiles = listOf(ProposedFile(path = "a.java", content = "code", description = "desc")),
        )
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(name = "r", path = tmp.toString()))
        val result = service.writeFiles(5L)
        assertEquals(true, result.success)
        assertEquals(listOf("a.java"), result.writtenFiles)
    }

    private fun analysis(
        status: String,
        stageOutput: String? = null,
        localRepoConfigId: Long? = 7L,
    ): LoggingFixAnalysis =
        LoggingFixAnalysis(
            id = 5L,
            featureSlug = "be",
            logText = "ERROR foo",
            selectedCategories = "error",
            localRepoConfigId = localRepoConfigId,
            status = status,
            stageDiagnose = stageOutput,
        )
}
