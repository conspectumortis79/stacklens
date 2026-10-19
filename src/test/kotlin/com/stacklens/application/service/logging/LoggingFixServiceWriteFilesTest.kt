package com.stacklens.application.service.logging
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.FixedClockPort
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class LoggingFixServiceWriteFilesTest {
    private val repository: LoggingFixPort = mock()

    private val localRepoConfigRepo: LocalRepoConfigPort = mock()

    private val clock = FixedClockPort(Instant.parse("2026-08-24T10:00:00Z"))

    private val fileWriter =
        LoggingFixFileWriter(
            repository = repository,
            localRepoConfigRepo = localRepoConfigRepo,
            fileWriter = JavaFileSystemAdapter(),
            clock = clock,
            stateOf = { LoggingFixFileState(null, emptyList()) },
        )

    private val service =
        LoggingFixService(
            repository = repository,
            partialUpdater = mock<LoggingFixPartialUpdatePort>(),
            localRepoConfigRepo = localRepoConfigRepo,
            aiUsageContext = mock<AiUsageContextPort>(),
            diagnoseStage = mock(),
            planStage = mock(),
            codeStage = mock(),
            testsStage = mock(),
            tx = mock<TransactionPort>(),
            clock = clock,
            markers = catalogBackedLoggingFixMarkers(),
            fileWriter = JavaFileSystemAdapter(),
            workerStarter = com.stacklens.adapter.out.process.SystemWorkerStarterPort,
        )

    private fun injectRun(
        id: Long,
        proposedFiles: List<ProposedFile>,
    ) {
        val runStateClass = Class.forName("com.stacklens.application.service.logging.LoggingFixService\$RunState")
        val ctor = runStateClass.declaredConstructors.single { it.parameterCount == 9 }
        ctor.isAccessible = true
        val analysis =
            LoggingFixAnalysis(
                id = id,
                featureSlug = "slug",
                logText = "ERROR boom",
                selectedCategories = "error",
                localRepoConfigId = 7L,
                status = "DONE",
            )
        val state = ctor.newInstance(analysis, false, proposedFiles, null, "", StringBuilder(), StringBuilder(), StringBuilder(), StringBuilder())
        val runsField = LoggingFixService::class.java.getDeclaredField("runs")
        runsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val runs = runsField.get(service) as ConcurrentHashMap<Long, Any>
        runs[id] = state
    }

    private fun slug(text: String): String = fileWriter.generateSlug(text)

    @Test
    fun `writeFiles stores every proposed file below the repository root`(
        @TempDir dir: Path,
    ) {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(id = 7L, name = "repo", path = dir.toString()))
        injectRun(
            11L,
            listOf(
                ProposedFile("src/Main.kt", "fun main() {}", "einstieg"),
                ProposedFile("src/deep/Util.kt", "object Util", "helfer"),
            ),
        )

        val result = service.writeFiles(11L)

        assertTrue(result.success)
        assertEquals(listOf("src/Main.kt", "src/deep/Util.kt"), result.writtenFiles)
        assertEquals("fun main() {}", dir.resolve("src/Main.kt").toFile().readText())
    }

    @Test
    fun `writeFiles reports the first file that cannot be written`(
        @TempDir dir: Path,
    ) {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(7L)).thenReturn(LocalRepoConfig(id = 7L, name = "repo", path = dir.toString()))
        Files.createDirectory(dir.resolve("blocked"))
        injectRun(12L, listOf(ProposedFile("blocked", "content", "kollision")))

        val result = service.writeFiles(12L)

        assertTrue(!result.success)
        assertTrue(result.error!!.startsWith("Fehler beim Schreiben von blocked"), result.error)
    }

    @Test
    fun `the slug renders the first non blank line as a kebab case anchor`() {
        assertEquals("fix-login-broken", slug("\n  Fix Login !! Broken  \nsecond line"))
    }

    @Test
    fun `the slug falls back to the timestamp when the log has no usable line`() {
        assertEquals("log-fix-1787565600000", slug("   \n\t\n"))
    }

    @Test
    fun `the slug never exceeds sixty characters`() {
        val long = "x".repeat(90)

        assertEquals(60, slug(long).length)
    }
}
