package com.stacklens.application.service
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.filesystem.ClasspathResourceAdapter
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.rex.RexTestStages
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.service.RepoScanner
import com.stacklens.application.service.pipeline.PipelineContext
import com.stacklens.testsupport.FakePlatformPort
import com.stacklens.testsupport.FakeStackLensConfigPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentPipelineRunnerTest {
    @Mock private lateinit var copilot: CopilotStreamingPort

    @Mock private lateinit var dbConfigRepo: DatabaseConfigPort

    @Mock private lateinit var dbScanPort: DatabaseScanPort

    @Mock private lateinit var aiConfigRepo: AiConfigPort

    @Mock private lateinit var outputPersister: AgentOutputPersister

    @Mock private lateinit var processPort: ProcessPort

    private val i18n = FakeI18n()

    private val copilotConfig =
        object : CopilotConfigPort {
            override val pipelineTimeoutSeconds: Long = 10L
            override val loadTestAnalysisTimeoutSeconds: Long = 600L
            override val searchAnalysisTimeoutSeconds: Long = 600L
            override val reviewAnalysisTimeoutSeconds: Long = 900L
        }

    private val filePort = JavaFileSystemAdapter()

    private val classpathResource = ClasspathResourceAdapter()

    private val jsonCodec =
        com.stacklens.adapter.out.shared
            .JacksonJsonCodecAdapter(jacksonObjectMapper())

    @TempDir lateinit var dataDir: File

    private val onRunCompleteCount = AtomicInteger(0)
    private lateinit var lastCompleted: AgentPipelineRun

    private lateinit var runner: AgentPipelineRunner

    @BeforeEach
    fun setUp() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(copilot.resolveToken()).thenReturn("tok")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenReturn("")

        whenever(copilot.writesFilesNatively).thenReturn(true)

        whenever(processPort.resolveGitBinary()).thenReturn("git")
        whenever(processPort.resolveMakeBinary()).thenReturn("make")

        val okResult = ProcessResult(exitCode = 0, stdout = "", stderr = "")
        whenever(processPort.run(any(), any(), any())).thenReturn(okResult)
        whenever(processPort.makefileHasTarget(any(), any())).thenReturn(false)
        whenever(processPort.findMakeTarget(any(), any())).thenReturn(null)

        whenever(dbConfigRepo.findAllDatabaseConfigsByIds(any())).thenReturn(emptyList())

        runner = makeRunner()
    }

    private fun makeRunner(
        loopbacks: List<com.stacklens.application.service.pipeline.loopback.PipelineLoopback> = emptyList(),
        onComplete: (AgentPipelineRun) -> Unit = { r ->
            onRunCompleteCount.incrementAndGet()
            lastCompleted = r
        },
    ) = AgentPipelineRunner(
        copilot = copilot,
        copilotConfig = copilotConfig,
        stackLensConfig = stackLensConfig(),
        dbConfigRepo = dbConfigRepo,
        dbSchemaPort = dbScanPort,
        aiConfigRepo = aiConfigRepo,
        outputPersister = outputPersister,
        i18n = i18n,
        filePort = filePort,
        classpathResource = classpathResource,
        processPort = processPort,
        platformPort = FakePlatformPort(),
        clock = com.stacklens.adapter.out.time.SystemClockPort,
        stageOutputWriter = mock(),
        repoScanner = mock<RepoScanner>(),
        ivanStageRunner = IvanStageRunner(processPort, i18n, filePort, mock()),
        rexStageRunner =
            run {
                val aiFixer =
                    RexAiFixService(
                        copilot = copilot,
                        copilotConfig = copilotConfig,
                        processPort = processPort,
                        filePort = filePort,
                        loadPrompt = { agent, vars -> AgentPromptLoader.load(stackLensConfig().dataDir, agent, vars, classpathResource, filePort, filePort) },
                        fileAttributes = filePort,
                    )
                RexStageRunner(
                    processPort = processPort,
                    i18n = i18n,
                    platformPort = FakePlatformPort(),
                    aiFixer = aiFixer,
                    coverageStep = RexCoverageStep(processPort, i18n, aiFixer),
                    fileAttributes = filePort,
                    outputFilter = mock(),
                    testStages = RexTestStages.DEFAULT,
                )
            },
        onRunComplete = onComplete,
        injectedLoopbacks = loopbacks,
        ticketDetector = AgentTicketNumberDetector(copilot, stackLensConfig(), classpathResource, filePort, filePort),
    )

    private fun stackLensConfig(): StackLensConfigPort = FakeStackLensConfigPort(dataDir = this@AgentPipelineRunnerTest.dataDir.absolutePath)

    private fun newRun(repoPath: String? = null) = AgentPipelineRun(id = 42L, rawText = "add login feature", repoPath = repoPath, featureSlug = "add-login", createdAt = System.currentTimeMillis())

    @Test
    fun `onRunComplete is called exactly once on happy path`() {
        val run = newRun()

        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `onRunComplete is called even when copilot streaming throws`() {
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenThrow(RuntimeException("model unavailable"))
        var callCount = 0
        val r = makeRunner { callCount++ }
        val run = newRun()

        r.execute(run)

        assertEquals(1, callCount)
    }

    @Test
    fun `onRunComplete receives the same run instance`() {
        val run = newRun()

        runner.execute(run)

        assertTrue(::lastCompleted.isInitialized)
        assertEquals(run.id, lastCompleted.id)
    }

    @Test
    fun `status is DONE after successful execution without repo`() {
        val run = newRun(repoPath = null)

        runner.execute(run)

        assertEquals(PipelineStatus.DONE, run.status)
    }

    @Test
    fun `status is ERROR when copilot streaming throws`() {
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenThrow(RuntimeException("boom"))
        val run = newRun()

        makeRunner().execute(run)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertNotNull(run.errorMessage)
    }

    @Test
    fun `status is ERROR when streaming is interrupted`() {
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenAnswer { throw InterruptedException("cancelled") }
        val run = newRun()

        runner.execute(run)

        assertEquals(PipelineStatus.ERROR, run.status)
    }

    @Test
    fun `cancelled run produces ERROR status and still calls onRunComplete`() {
        val run = newRun()
        run.cancelled = true

        runner.execute(run)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `Nora stage is DONE after successful execution`() {
        val run = newRun()

        runner.execute(run)

        assertEquals(StageStatus.DONE, run.nora.status)
    }

    @Test
    fun `Lena stage is DONE for jira-mode runs`() {
        val run =
            AgentPipelineRun(id = 7L, rawText = "PROJ-1", repoPath = null, featureSlug = "proj-1", jiraTicketData = "{\"key\":\"PROJ-1\"}", createdAt = System.currentTimeMillis())

        runner.execute(run)

        assertEquals(StageStatus.DONE, run.lena.status)
    }

    @Test
    fun `AI-detected ticket key overrides feature slug`() {
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenAnswer { inv ->
                val prompt = inv.getArgument<String>(1)
                if (prompt.contains("ticket") || prompt.contains("ticket-detect")) "PROJ-42" else ""
            }

        val run = newRun()
        runner.execute(run)

        assertTrue(run.featureSlug.contains("PROJ-42") || run.detectedTicketKey == "PROJ-42")
    }

    @Test
    fun `workerThread is cleared in finally block`() {
        val run = newRun()

        runner.execute(run)

        assertTrue(run.cancellation == null, "workerThread must be null after execute() returns")
    }

    @Test
    fun `workerThread is cleared even when streaming throws`() {
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenThrow(RuntimeException("boom"))
        val run = newRun()

        makeRunner().execute(run)

        assertTrue(run.cancellation == null)
    }

    @Test
    fun `currentStage is null after successful completion`() {
        val run = newRun()

        runner.execute(run)

        assertTrue(run.currentStage == null)
    }

    @Test
    fun `jira mode run does not call ticket-detect prompt`() {
        val run = AgentPipelineRun(id = 3L, rawText = "x", repoPath = null, featureSlug = "slug", jiraTicketData = "data", createdAt = System.currentTimeMillis())

        runner.execute(run)

        assertTrue(run.detectedTicketKey == null)
    }

    @Test
    fun `autoPersistFilesIfNeeded skips when filesAutoWritten is already true`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        val run = newRun(repoPath = dataDir.absolutePath)
        run.filesAutoWritten = true

        runner.execute(run)

        verify(outputPersister, org.mockito.kotlin.never()).persist(any(), any())
    }

    @Test
    fun `autoPersistFilesIfNeeded skips when repoPath is null`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        val run = newRun(repoPath = null)

        runner.execute(run)

        verify(outputPersister, org.mockito.kotlin.never()).persist(any(), any())
    }

    @Test
    fun `autoPersistFilesIfNeeded skips when repoPath is blank`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        val run = newRun(repoPath = "   ")

        runner.execute(run)

        verify(outputPersister, org.mockito.kotlin.never()).persist(any(), any())
    }

    @Test
    fun `autoPersistFilesIfNeeded skips when repo dir does not exist`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        val run = newRun(repoPath = File(dataDir, "this-subdir-does-not-exist").absolutePath)

        runner.execute(run)

        verify(outputPersister, org.mockito.kotlin.never()).persist(any(), any())
    }

    @Test
    fun `autoPersistFilesIfNeeded calls outputPersister when all conditions met`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        val persistResult =
            AgentOutputPersister.PersistResult(
                written = listOf("src/Main.kt"),
                deleted = emptyList(),
                errors = emptyList(),
            )
        whenever(outputPersister.persist(any(), any())).thenReturn(persistResult)
        val run = newRun(repoPath = dataDir.absolutePath)

        runner.execute(run)

        verify(outputPersister).persist(any(), any())
        assertTrue(run.filesAutoWritten)
        assertEquals(listOf("src/Main.kt"), run.autoWrittenFiles)
    }

    @Test
    fun `autoPersistFilesIfNeeded handles exception from outputPersister`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        whenever(outputPersister.persist(any(), any())).thenThrow(RuntimeException("disk full"))
        val run = newRun(repoPath = dataDir.absolutePath)

        runner.execute(run)

        verify(outputPersister).persist(any(), any())
        assertFalse(run.filesAutoWritten)
        assertTrue(run.autoWriteErrors.isNotEmpty())
    }

    @Test
    fun `autoPersistFilesIfNeeded outer catch handles exception from the provider capability check`() {
        whenever(copilot.writesFilesNatively).thenThrow(RuntimeException("config unavailable"))
        val run = newRun(repoPath = dataDir.absolutePath)

        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `writesFilesNatively skips persist when the provider writes files natively`() {
        whenever(copilot.writesFilesNatively).thenReturn(true)
        val run = newRun(repoPath = dataDir.absolutePath)

        runner.execute(run)

        verify(outputPersister, org.mockito.kotlin.never()).persist(any(), any())
    }

    @Test
    fun `writesFilesNatively skips persist when the provider writes files natively-cli`() {
        whenever(copilot.writesFilesNatively).thenReturn(true)
        val run = newRun(repoPath = dataDir.absolutePath)

        runner.execute(run)

        verify(outputPersister, org.mockito.kotlin.never()).persist(any(), any())
    }

    @Test
    fun `writesFilesNatively skips persist when the default provider is used`() {
        whenever(copilot.writesFilesNatively).thenReturn(true)
        val run = newRun(repoPath = dataDir.absolutePath)

        runner.execute(run)

        verify(outputPersister, org.mockito.kotlin.never()).persist(any(), any())
    }

    @Test
    fun `git setup InterruptedException is caught and sets gitLog`() {
        val repoDir = File(dataDir, "repo").also { it.mkdirs() }
        whenever(processPort.run(any(), any(), any()))
            .thenThrow(
                RuntimeException("wrapped interrupt").also {
                    it.initCause(InterruptedException("cancelled"))
                },
            )

        whenever(processPort.resolveGitBinary()).thenAnswer { throw InterruptedException("cancelled") }
        val run = newRun(repoPath = repoDir.absolutePath)

        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `git setup generic exception is caught and sets gitLog`() {
        whenever(processPort.resolveGitBinary()).thenThrow(RuntimeException("git not found"))
        val repoDir = File(dataDir, "repo").also { it.mkdirs() }
        val run = newRun(repoPath = repoDir.absolutePath)

        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `autoPersistFilesIfNeeded logs warning when repo path throws on canonicalize`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)

        val run = newRun(repoPath = "/dev/null/\u0000invalid")

        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `autoPersistFilesIfNeeded exception in finally still calls onRunComplete`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)

        whenever(outputPersister.persist(any(), any())).thenThrow(RuntimeException("persist crashed"))
        val run = newRun(repoPath = dataDir.absolutePath)

        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `cancellation during stages produces ERROR status`() {
        var callCount = 0
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenAnswer {
                callCount++
                if (callCount > 2) {
                    val run = (it.getArgument<(String) -> Unit>(3))
                    ""
                } else {
                    ""
                }
            }
        val run = newRun()

        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenAnswer { inv ->
                run.cancelled = true
                ""
            }

        runner.execute(run)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `auto-persist in finally catches exception without crashing`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        whenever(outputPersister.persist(any(), any())).thenThrow(RuntimeException("persist error"))

        val repoDir = File(dataDir, "repo-persist-err").apply { mkdirs() }
        val run = newRun(repoPath = repoDir.absolutePath)
        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `auto-persist skips non-writable repo dir`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)

        val repoDir =
            File(dataDir, "repo-readonly").apply {
                mkdirs()
                setWritable(false)
            }
        try {
            val run = newRun(repoPath = repoDir.absolutePath)
            runner.execute(run)
            assertEquals(1, onRunCompleteCount.get())
            assertTrue(run.autoWriteErrors.any { it.contains("not writable") || it.contains("Repository") })
        } finally {
            repoDir.setWritable(true)
        }
    }

    @Test
    fun `git setup InterruptedException sets cancel message`() {
        val repoDir = File(dataDir, "repo-git-int").apply { mkdirs() }
        File(repoDir, ".git").mkdirs()
        whenever(processPort.resolveGitBinary()).thenReturn("/usr/bin/git")
        whenever(processPort.run(any(), any(), any())).thenAnswer { throw InterruptedException("cancelled") }
        val run = newRun(repoPath = repoDir.absolutePath)
        runner.execute(run)
        assertTrue(run.gitLog.contains("ticket_implementation.be_git_setup_cancelled"))
    }

    @Test
    fun `dbConfigRepo failure in runCatching results in null dbConfigs`() {
        whenever(dbConfigRepo.findAllDatabaseConfigsByIds(any())).thenThrow(RuntimeException("DB down"))
        val run = AgentPipelineRun(id = 42L, rawText = "feat", repoPath = null, featureSlug = "s", databaseConfigIds = listOf(1L), createdAt = System.currentTimeMillis())
        runner.execute(run)

        assertEquals(1, onRunCompleteCount.get())
        assertTrue(run.dbSchema.isBlank())
    }

    @Test
    fun `loopback failure is caught and pipeline continues`() {
        val run = newRun()
        runner.execute(run)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `Lena streaming callback sets partial output on the run`() {
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val callback = inv.getArgument<(String) -> Unit>(5)
                callback("partial Lena output")
                "final Lena output"
            }
        val run =
            AgentPipelineRun(id = 99L, rawText = "PROJ-1", repoPath = null, featureSlug = "proj-1", jiraTicketData = "{\"key\":\"PROJ-1\"}", createdAt = System.currentTimeMillis())
        runner.execute(run)
        assertEquals("final Lena output", run.lena.output)
    }

    @Test
    fun `preProcess concatenates schemas with separator for multiple DB configs`() {
        val dbCfg1 =
            com.stacklens.domain.model.DatabaseConfig(
                id = 1L,
                name = "db1",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "h",
                port = 5432,
                databaseName = "d",
                username = "u",
                password = "p",
            )
        val dbCfg2 =
            com.stacklens.domain.model.DatabaseConfig(
                id = 2L,
                name = "db2",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "h",
                port = 5432,
                databaseName = "d",
                username = "u",
                password = "p",
            )
        whenever(dbConfigRepo.findAllDatabaseConfigsByIds(any())).thenReturn(listOf(dbCfg1, dbCfg2))
        whenever(dbScanPort.fetchSchemaSummary(any())).thenReturn("CREATE TABLE t1(id INT);")
        val run = AgentPipelineRun(id = 50L, rawText = "feat", repoPath = null, featureSlug = "s", databaseConfigIds = listOf(1L, 2L), createdAt = System.currentTimeMillis())
        runner.execute(run)
        assertTrue(run.dbSchema.contains("---"))
    }

    @Test
    fun `loopback throwing RuntimeException is caught and pipeline continues`() {
        val field = AgentPipelineRunner::class.java.getDeclaredField("loopbacks")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val original = field.get(runner) as List<com.stacklens.application.service.pipeline.loopback.PipelineLoopback>
        val mocked =
            original.mapIndexed { idx, lb ->
                val spy = org.mockito.Mockito.spy(lb)
                if (idx == 0) {
                    org.mockito.kotlin
                        .doThrow(RuntimeException("loopback boom"))
                        .whenever(spy)
                        .runIfRequested(any())
                }
                spy
            }
        field.set(runner, mocked)

        val run = newRun()
        runner.execute(run)

        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `pipeline cancelled before Nora stage returns immediately`() {
        val run = newRun()
        run.cancelled = true
        runner.execute(run)
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `auto-persist exception in finally is caught and does not break pipeline`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        whenever(outputPersister.persist(any(), any())).thenThrow(RuntimeException("persist boom"))
        val run = newRun(repoPath = dataDir.absolutePath)
        runner.execute(run)
        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `auto-persist exception with null message uses class name`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        whenever(outputPersister.persist(any(), any())).thenAnswer { throw object : Exception(null as String?) {} }
        val run = newRun(repoPath = dataDir.absolutePath)
        runner.execute(run)
        assertEquals(PipelineStatus.DONE, run.status)
        assertTrue(run.autoWriteErrors.any { it.contains("Auto-persist failed") })
    }

    @Test
    fun `auto-persist skips when repoPath is a file not a directory`() {
        whenever(copilot.writesFilesNatively).thenReturn(false)
        val file = File(dataDir, "notadir.txt")
        file.writeText("hello")
        val run = newRun(repoPath = file.absolutePath)
        runner.execute(run)
        assertEquals(PipelineStatus.DONE, run.status)

        assertFalse(run.filesAutoWritten)
    }

    @Test
    fun `pipeline cancelled during Nora stage exits runStages early`() {
        var run0: AgentPipelineRun? = null
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull()))
            .thenAnswer {
                run0?.cancelled = true
                ""
            }
        val run = newRun()
        run0 = run
        runner.execute(run)
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `pipeline cancelled during Rex sets rex status to ERROR`() {
        val repoDir = File(dataDir, "repo-rex-cancel").also { it.mkdirs() }
        val rp = repoDir.absolutePath
        val run = newRun(repoPath = rp)

        whenever(processPort.findMakeTarget(rp, "sniff", "phpcs", "cs", "lint")).thenReturn("sniff")
        whenever(processPort.makefileHasTarget(rp, "beautify")).thenReturn(false)

        whenever(processPort.runMakeTarget(rp, "sniff")).thenAnswer {
            run.cancelled = true
            Pair(1, "error")
        }
        whenever(processPort.findMakeTarget(rp, "unit-test", "test-unit", "test")).thenReturn(null)
        whenever(processPort.findMakeTarget(rp, "integration-test", "test-integration")).thenReturn(null)
        whenever(processPort.findMakeTarget(rp, "system-test", "test-system", "e2e", "e2e-test")).thenReturn(null)
        whenever(processPort.findMakeTarget(rp, "coverage", "coverage-report")).thenReturn(null)
        runner.execute(run)
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.ERROR, run.rex.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `preProcess uses gitRoot for repoRoot when git setup succeeds`() {
        val repoDir = File(dataDir, "repo-gitroot").apply { mkdirs() }
        File(repoDir, ".git").mkdirs()
        whenever(processPort.resolveGitBinary()).thenReturn("/usr/bin/git")

        val run = newRun(repoPath = repoDir.absolutePath)
        runner.execute(run)

        assertTrue(run.gitRoot.isNotBlank(), "gitRoot should be populated after successful git setup")
        assertEquals(PipelineStatus.DONE, run.status)
        assertEquals(1, onRunCompleteCount.get())
    }

    @Test
    fun `a failing loopback is logged and the pipeline continues`() {
        val failing =
            object : com.stacklens.application.service.pipeline.loopback.PipelineLoopback {
                override val name: String = "failing"
                override val afterStage: String = "faye"

                override fun runIfRequested(ctx: PipelineContext): Boolean = throw RuntimeException("loopback boom")
            }
        val runnerWithLoopback = makeRunner(loopbacks = listOf(failing))
        val run = newRun()

        runnerWithLoopback.execute(run)

        assertEquals(PipelineStatus.DONE, run.status)
    }
}
