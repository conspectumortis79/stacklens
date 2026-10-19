package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.service.FakeAiUsageContextPort
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class LoggingFixServiceResidualCoverageTest {
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

    private fun awaitPipelineEnd(timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val workerAlive = Thread.getAllStackTraces().keys.any { it.name.startsWith("logging-fix-") && it.isAlive }
            if (!workerAlive) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `a start request with an unknown repository config runs without a repo root`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(42L)).thenReturn(null)
        whenever(repository.saveLoggingFixAnalysis(any())).thenAnswer {
            val a = it.arguments[0] as LoggingFixAnalysis
            if (a.id == 0L) LoggingFixAnalysis(id = 500L, featureSlug = a.featureSlug, logText = a.logText, selectedCategories = a.selectedCategories, language = a.language, status = a.status) else a
        }
        val service = service()

        val id = service.startFix("boom", emptyList(), localRepoConfigId = 42L, language = "de")

        assertEquals(500L, id)
        awaitPipelineEnd()
    }

    @Test
    fun `a start request whose repository path no longer exists runs without a repo root`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(43L))
            .thenReturn(LocalRepoConfig(id = 43L, name = "gone", path = "/nonexistent/stacklens-repo-${System.nanoTime()}"))
        whenever(repository.saveLoggingFixAnalysis(any())).thenAnswer {
            val a = it.arguments[0] as LoggingFixAnalysis
            if (a.id == 0L) LoggingFixAnalysis(id = 501L, featureSlug = a.featureSlug, logText = a.logText, selectedCategories = a.selectedCategories, language = a.language, status = a.status) else a
        }
        val service = service()

        val id = service.startFix("boom", emptyList(), localRepoConfigId = 43L, language = "de")

        assertEquals(501L, id)
        awaitPipelineEnd()
    }

    @Test
    fun `a cancelled run refuses to start the next stage`() {
        whenever(repository.saveLoggingFixAnalysis(any())).thenAnswer { it.arguments[0] }
        val service = service()
        val id = service.startFix("boom", emptyList(), localRepoConfigId = null, language = "de")

        awaitPipelineEnd()

        val runs = ReflectionTestUtils.getField(service, "runs") as java.util.concurrent.ConcurrentHashMap<*, *>
        val state = runs[id]!!
        ReflectionTestUtils.setField(state, "cancelled", true)
        val ctx =
            LoggingFixStageContext(
                logText = "boom",
                selectedCategories = emptyList(),
                repoPath = null,
                repoRoot = null,
                language = "de",
                onPartial = { _, _ -> },
            )

        val error =
            assertThrows(Exception::class.java) {
                ReflectionTestUtils.invokeMethod<Any>(service, "runStage", state, ctx, "plan", planStage)
            }
        val messages = generateSequence(error as Throwable?) { it.cause }.map { it.javaClass.simpleName }.toList()
        assertTrue(messages.any { it.contains("Interrupted") } || error.message?.contains("cancelled") == true)
    }
}
