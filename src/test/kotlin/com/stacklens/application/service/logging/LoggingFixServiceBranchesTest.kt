package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.logging.LoggingFixStartRequest
import com.stacklens.application.port.inbound.logging.StageOutputs
import com.stacklens.application.port.inbound.logging.StageState
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.LoggingFixStageColumn
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.service.FakeAiUsageContextPort
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LoggingFixServiceBranchesTest {
    private val repository: LoggingFixPort = mock()
    private val partialUpdater: LoggingFixPartialUpdatePort = mock()
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()
    private val aiUsageContext = FakeAiUsageContextPort()
    private val transaction =
        object : TransactionPort {
            override fun <T> inTransaction(block: () -> T): T = block()

            override fun afterCommit(block: () -> Unit) {
                block()
            }
        }
    private val clock = FixedClockPort(Instant.parse("2026-08-24T10:00:00Z"))

    private val diagnoseStage: LoggingFixStage = mock()
    private val planStage: LoggingFixStage = mock()
    private val codeStage: LoggingFixStage = mock()
    private val testsStage: LoggingFixStage = mock()

    private fun service(): LoggingFixService =
        LoggingFixService(
            repository = repository,
            partialUpdater = partialUpdater,
            localRepoConfigRepo = localRepoConfigRepo,
            aiUsageContext = aiUsageContext,
            diagnoseStage = diagnoseStage,
            planStage = planStage,
            codeStage = codeStage,
            testsStage = testsStage,
            tx = transaction,
            clock = clock,
            markers = catalogBackedLoggingFixMarkers(),
            fileWriter = JavaFileSystemAdapter(),
            workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
        )

    private fun analysis(
        status: String,
        output: String?,
        localRepoConfigId: Long? = null,
    ): LoggingFixAnalysis =
        LoggingFixAnalysis(
            id = 17L,
            featureSlug = "slug",
            logText = "log",
            selectedCategories = "",
            localRepoConfigId = localRepoConfigId,
            status = status,
            stageDiagnose = output,
        )

    private val stageOutputReader =
        LoggingFixStageOutputReader(repository, localRepoConfigRepo, catalogBackedLoggingFixMarkers()) { "" }

    private fun stageState(
        output: String?,
        status: String,
        currentStage: String = "",
    ): StageState =
        ReflectionTestUtils.invokeMethod(
            stageOutputReader,
            "stageState",
            "diagnose",
            "Diagnose",
            output,
            analysis(status, output),
            currentStage,
        )!!

    @Test fun `stage status mirrors the analysis state for empty and filled outputs`() {
        assertEquals("PENDING", stageState(null, LoggingFixAnalysis.STATUS_PENDING).status)
        assertEquals("RUNNING", stageState("", LoggingFixAnalysis.STATUS_RUNNING).status)
        assertEquals("ERROR", stageState("   ", LoggingFixAnalysis.STATUS_ERROR).status)
        assertEquals("PENDING", stageState(null, LoggingFixAnalysis.STATUS_DONE).status)
        assertEquals("", stageState(null, LoggingFixAnalysis.STATUS_DONE).output)
        assertEquals("RUNNING", stageState("text", LoggingFixAnalysis.STATUS_RUNNING).status)
        assertEquals("DONE", stageState("text", LoggingFixAnalysis.STATUS_DONE).status)
        assertEquals("CANCELLED", stageState("text", LoggingFixAnalysis.STATUS_CANCELLED).status)
        assertEquals("ERROR", stageState("text", LoggingFixAnalysis.STATUS_ERROR).status)
        assertEquals("PENDING", stageState("text", LoggingFixAnalysis.STATUS_PENDING).status)
    }

    private fun generateSlug(text: String): String =
        LoggingFixFileWriter(
            repository = repository,
            localRepoConfigRepo = localRepoConfigRepo,
            fileWriter = JavaFileSystemAdapter(),
            clock = clock,
            stateOf = { LoggingFixFileState(null, emptyList()) },
        ).generateSlug(text)

    @Test fun `feature slugs fall back to the timestamp for unusable log text`() {
        assertEquals("login-fails-with-500", generateSlug("  Login fails with 500!!!\nstack trace"))
        assertEquals("log-fix-${clock.epochMillis()}", generateSlug("\n   \n\t"))
        assertEquals("a".repeat(60), generateSlug("a".repeat(90)))
        assertEquals("rand", generateSlug("--- rand ---"))
    }

    @Test fun `start requests are validated against the referenced repository`() {
        val tempDirectory = Files.createTempDirectory("logging-fix-repo").toFile()
        val fileInsteadOfDirectory = tempDirectory.resolve("datei.txt").also { it.writeText("x") }
        whenever(localRepoConfigRepo.findLocalRepoConfigById(1L)).thenReturn(null)
        whenever(localRepoConfigRepo.findLocalRepoConfigById(2L)).thenReturn(LocalRepoConfig(id = 2L, name = "leer", path = "  "))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(3L))
            .thenReturn(LocalRepoConfig(id = 3L, name = "datei", path = fileInsteadOfDirectory.absolutePath))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(4L))
            .thenReturn(LocalRepoConfig(id = 4L, name = "fehlend", path = tempDirectory.resolve("weg").absolutePath))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(5L))
            .thenReturn(LocalRepoConfig(id = 5L, name = "ok", path = tempDirectory.absolutePath))
        val service = service()

        assertEquals(listOf("logText is blank"), service.validateStartRequest(LoggingFixStartRequest(logText = " ", localRepoConfigId = null)))
        assertEquals(emptyList<String>(), service.validateStartRequest(LoggingFixStartRequest(logText = "text", localRepoConfigId = null)))
        assertEquals(
            listOf("repository not found: id=1"),
            service.validateStartRequest(LoggingFixStartRequest(logText = "text", localRepoConfigId = 1L)),
        )
        assertEquals(
            listOf("repository path is empty"),
            service.validateStartRequest(LoggingFixStartRequest(logText = "text", localRepoConfigId = 2L)),
        )
        assertEquals(
            listOf("repository path does not exist: ${fileInsteadOfDirectory.absolutePath}"),
            service.validateStartRequest(LoggingFixStartRequest(logText = "text", localRepoConfigId = 3L)),
        )
        assertEquals(
            listOf("repository path does not exist: ${tempDirectory.resolve("weg").absolutePath}"),
            service.validateStartRequest(LoggingFixStartRequest(logText = "text", localRepoConfigId = 4L)),
        )
        assertEquals(emptyList<String>(), service.validateStartRequest(LoggingFixStartRequest(logText = "text", localRepoConfigId = 5L)))
    }

    @Test fun `write previews fall back to the stored analysis`() {
        whenever(repository.findLoggingFixAnalysisById(1L)).thenReturn(null)
        whenever(repository.findLoggingFixAnalysisById(2L))
            .thenReturn(analysis(LoggingFixAnalysis.STATUS_DONE, null, localRepoConfigId = 7L))
        whenever(repository.findLoggingFixAnalysisById(3L))
            .thenReturn(analysis(LoggingFixAnalysis.STATUS_DONE, null))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(null)
        val service = service()

        assertNull(service.getWriteFilesPreview(1L))
        assertEquals(emptyList<com.stacklens.application.port.inbound.logging.WriteFileEntry>(), service.getWriteFilesPreview(2L)?.files)
        assertNull(service.getWriteFilesPreview(2L)?.repoPath)
        assertEquals("", service.getWriteFilesPreview(3L)?.repoPath ?: "")
    }

    @Test fun `writing files reports missing analyses repositories and empty plans`() {
        whenever(repository.findLoggingFixAnalysisById(1L)).thenReturn(null)
        whenever(repository.findLoggingFixAnalysisById(2L))
            .thenReturn(analysis(LoggingFixAnalysis.STATUS_DONE, null, localRepoConfigId = 8L))
        whenever(repository.findLoggingFixAnalysisById(3L))
            .thenReturn(analysis(LoggingFixAnalysis.STATUS_DONE, null, localRepoConfigId = 9L))
        whenever(localRepoConfigRepo.findLocalRepoConfigById(8L)).thenReturn(null)
        whenever(localRepoConfigRepo.findLocalRepoConfigById(9L))
            .thenReturn(LocalRepoConfig(id = 9L, name = "weg", path = "/tmp/definitiv-nicht-da-${System.nanoTime()}"))
        val service = service()

        assertEquals(false, service.writeFiles(1L).success)
        assertEquals("Analyse nicht gefunden", service.writeFiles(1L).error)
        assertEquals("Repository nicht verfügbar", service.writeFiles(2L).error)
        assertTrue(service.writeFiles(3L).error!!.startsWith("Repository-Pfad existiert nicht"))
    }

    @Test fun `the pipeline buffers partial output for every stage and offers a write preview`() {
        val service = service()
        var saved: LoggingFixAnalysis? = null
        whenever(repository.saveLoggingFixAnalysis(any())).thenAnswer { invocation ->
            saved = invocation.arguments[0] as LoggingFixAnalysis
            saved
        }
        val finished = CountDownLatch(1)
        whenever(partialUpdater.updateStatusAndCompletedAt(any(), any(), any())).thenAnswer {
            finished.countDown()
            1
        }
        whenever(diagnoseStage.execute(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as LoggingFixStageContext).onPartial(LoggingFixDiagnoseStage.STAGE_NAME, "diag")
            (invocation.arguments[0] as LoggingFixStageContext).onPartial("fremd", "x")
            null
        }
        whenever(planStage.execute(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as LoggingFixStageContext).onPartial(LoggingFixPlanStage.STAGE_NAME, "plan")
            null
        }
        whenever(codeStage.execute(any())).thenAnswer { invocation ->
            val ctx = invocation.arguments[0] as LoggingFixStageContext
            ctx.onPartial(LoggingFixCodeStage.STAGE_NAME, "code")
            ctx.proposedFiles += ProposedFile("src/Main.kt", "fun main() {}", "neu")
            null
        }
        whenever(testsStage.execute(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as LoggingFixStageContext).onPartial(LoggingFixTestsStage.STAGE_NAME, "tests")
            null
        }

        val id = service.startFix("Boom beim Start\nzweite Zeile", listOf("errors"), localRepoConfigId = null, language = "de")
        assertTrue(finished.await(20, TimeUnit.SECONDS))
        whenever(repository.findLoggingFixAnalysisById(any())).thenAnswer { saved }

        val preview = service.getWriteFilesPreview(id)
        assertEquals(listOf("src/Main.kt"), preview?.files?.map { it.path })
        assertNull(preview?.repoPath)
        assertEquals("boom-beim-start", saved!!.featureSlug)

        verify(partialUpdater).appendStageOutput(any(), eq(LoggingFixStageColumn.DIAGNOSE), eq("diag"))
        verify(partialUpdater).appendStageOutput(any(), eq(LoggingFixStageColumn.PLAN), eq("plan"))
        verify(partialUpdater).appendStageOutput(any(), eq(LoggingFixStageColumn.CODE), eq("code"))
        verify(partialUpdater).appendStageOutput(any(), eq(LoggingFixStageColumn.TESTS), eq("tests"))

        val outputs: StageOutputs? = service.getStageOutputs(id)
        assertEquals(4, outputs?.stages?.size)
        assertEquals("", outputs?.stages?.get("diagnose")?.output)
        assertEquals("", outputs?.currentStage)
    }
}
