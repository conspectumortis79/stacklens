package com.stacklens.application.service
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.filesystem.ClasspathResourceAdapter
import com.stacklens.adapter.out.filesystem.DirectRepoReader
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.filesystem.JavaPathPolicyAdapter
import com.stacklens.adapter.out.filesystem.ShellRepoReaderFactoryAdapter
import com.stacklens.adapter.out.filesystem.StageOutputFileWriter
import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.adapter.out.persistence.AgentRunFileStore
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.shared.ProcessAdapter
import com.stacklens.application.port.inbound.rex.RexTestStages
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.PullRequestDiffPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.application.port.out.process.ProcessPort
import com.stacklens.application.service.AgentPipelineRunner
import com.stacklens.application.service.AgentRunArchiveService
import com.stacklens.application.service.I18nService
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.application.service.pipeline.loopback.FayeFinnLoopback
import com.stacklens.application.service.pipeline.loopback.QuinnLoopback
import com.stacklens.application.service.pipeline.loopback.SageTessLoopback
import com.stacklens.application.service.pipeline.loopback.SydFinnLoopback
import com.stacklens.application.service.pipeline.loopback.TessFinnLoopback
import com.stacklens.testsupport.FakePlatformPort
import com.stacklens.testsupport.FakeStackLensConfigPort
import com.stacklens.testsupport.TestExecutors
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class CopilotAgentPipelineServiceTestBase {
    @Mock lateinit var copilot: CopilotStreamingPort

    @Mock lateinit var localRepoConfigPort: LocalRepoConfigPort

    @Mock lateinit var dbConfigPort: DatabaseConfigPort

    @Mock lateinit var dbScanPort: DatabaseScanPort

    @Mock lateinit var pullRequestDiffPort: PullRequestDiffPort

    val aiUsageContext = FakeAiUsageContextPort()

    val deMessages: Map<String, String> by lazy {
        val stream =
            javaClass.classLoader.getResourceAsStream("static/i18n/de.json")
                ?: return@lazy emptyMap()
        val root = jacksonObjectMapper().readTree(stream)
        val flat = mutableMapOf<String, String>()

        fun walk(
            node: com.fasterxml.jackson.databind.JsonNode,
            prefix: String,
        ) {
            val it = node.properties().iterator()
            while (it.hasNext()) {
                val entry = it.next()
                val k = entry.key
                val v = entry.value
                val full = if (prefix.isEmpty()) k else "$prefix.$k"
                if (v.isObject) {
                    walk(v, full)
                } else if (v.isTextual) {
                    flat[full] = v.asText()
                }
            }
        }
        walk(root, "")
        flat
    }

    val i18n: I18nService =
        org.mockito.kotlin.mock(
            defaultAnswer =
                org.mockito.stubbing.Answer { invocation ->
                    val name = invocation.method.name
                    if (name == "t") {
                        val key = invocation.arguments.getOrNull(0) as? String ?: ""
                        val args =
                            invocation.arguments.drop(1).flatMap {
                                when (it) {
                                    is Array<*> -> it.toList()
                                    else -> listOf(it)
                                }
                            }
                        val template = deMessages[key] ?: key
                        if (args.isEmpty() || !template.contains('{')) {
                            template
                        } else {
                            Regex("""\{(\d+)\}""").replace(template) { m ->
                                val idx = m.groupValues[1].toInt()
                                if (idx in args.indices) args[idx].toString() else m.value
                            }
                        }
                    } else if (name == "translateError") {
                        val e = invocation.arguments.getOrNull(0) as? Exception
                        e?.message ?: e?.javaClass?.simpleName ?: ""
                    } else {
                        null
                    }
                },
        )

    @Mock lateinit var aiConfigPort: AiConfigPort

    @Mock lateinit var processPort: ProcessPort

    @Mock lateinit var filePort: FileSystemPort

    @BeforeEach
    fun setUpDbBridge() {
        whenever(dbConfigPort.findAllDatabaseConfigsByIds(any())).thenAnswer { inv ->
            (inv.getArgument<Iterable<Long>>(0)).mapNotNull { id -> dbConfigPort.findDatabaseConfigById(id) }
        }

        whenever(copilot.resolveLanguage()).thenReturn("en")
    }

    fun makeService(
        dataDir: String = "./data",
        overrideProcessPort: ProcessPort? = null,
        overrideRepoScannerProcessPort: ProcessPort? = null,
        overrideFilePort: FileSystemPort? = null,
    ): CopilotAgentPipelineService {
        val processPort: ProcessPort = overrideProcessPort ?: ProcessAdapter()
        val copilotConfig =
            object : CopilotConfigPort {
                override val pipelineTimeoutSeconds: Long = 10L
                override val loadTestAnalysisTimeoutSeconds: Long = 600L
                override val searchAnalysisTimeoutSeconds: Long = 600L
                override val reviewAnalysisTimeoutSeconds: Long = 600L
            }
        val stackLensConfig = FakeStackLensConfigPort(dataDir = dataDir)
        val filePort = overrideFilePort ?: JavaFileSystemAdapter()
        val classpathResource = ClasspathResourceAdapter()
        val jsonCodec = JacksonJsonCodecAdapter(jacksonObjectMapper())
        val deps =
            CopilotAgentPipelineDeps(
                copilot = copilot,
                localRepoConfigRepo = localRepoConfigPort,
                dbConfigRepo = dbConfigPort,
                dbScanPort = dbScanPort,
                i18n = i18n,
                aiConfigRepo = aiConfigPort,
                outputPersister = AgentOutputPersister(filePort, JavaPathPolicyAdapter(filePort)),
                jsonCodec = jsonCodec,
                copilotConfig = copilotConfig,
                stackLensConfig = stackLensConfig,
                processPort = processPort,
                platformPort = FakePlatformPort(),
                filePort = filePort,
                classpathResource = classpathResource,
                pullRequestDiffPort = pullRequestDiffPort,
                repoScanner =
                    RepoScanner(
                        overrideRepoScannerProcessPort ?: ProcessAdapter(),
                        DirectRepoReader(),
                        JavaFileSystemAdapter(),
                        ShellRepoReaderFactoryAdapter(),
                    ),
                aiUsageContext = aiUsageContext,
                ivanStageRunner = IvanStageRunner(processPort, i18n, filePort, mock()),
                rexStageRunner =
                    run {
                        val aiFixer =
                            RexAiFixService(
                                copilot = copilot,
                                copilotConfig = copilotConfig,
                                processPort = processPort,
                                filePort = filePort,
                                loadPrompt = { agent, vars -> AgentPromptLoader.load(stackLensConfig.dataDir, agent, vars, classpathResource, filePort, filePort) },
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
                ticketDetector = AgentTicketNumberDetector(copilot, stackLensConfig, classpathResource, filePort, filePort),
            )
        val registry = CopilotPipelineRegistry(TestExecutors.daemon())
        val snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort)
        val archiveService =
            AgentRunArchiveService(
                store = AgentRunFileStore(stackLensConfig, jsonCodec, filePort, snapshotCodec),
                runs = registry.rawMap(),
                maxRetainedRuns = 200,
                seedRunId = registry::seed,
            )
        val pipelineRunner =
            AgentPipelineRunner(
                copilot = deps.copilot,
                copilotConfig = deps.copilotConfig,
                stackLensConfig = deps.stackLensConfig,
                dbConfigRepo = deps.dbConfigRepo,
                dbSchemaPort = deps.dbScanPort,
                aiConfigRepo = deps.aiConfigRepo,
                outputPersister = deps.outputPersister,
                i18n = deps.i18n,
                filePort = deps.filePort,
                classpathResource = deps.classpathResource,
                processPort = deps.processPort,
                platformPort = deps.platformPort,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
                stageOutputWriter = StageOutputFileWriter(filePort),
                repoScanner = deps.repoScanner,
                ivanStageRunner = deps.ivanStageRunner,
                rexStageRunner = deps.rexStageRunner,
                ticketDetector = deps.ticketDetector,
                onRunComplete = { run -> archiveService.persistAndEvict(run) },
                injectedLoopbacks =
                    listOf(
                        FayeFinnLoopback(),
                        TessFinnLoopback(),
                        SydFinnLoopback(),
                        QuinnLoopback(),
                        SageTessLoopback(),
                    ),
            )
        return CopilotAgentPipelineService(
            deps = deps,
            registry = registry,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
            archiveService = archiveService,
            pipelineRunner = pipelineRunner,
            chatService = AgentPipelineChatService(registry, copilot, i18n),
            pullRequestService = AgentPipelinePullRequestService(registry, pullRequestDiffPort),
        )
    }

    fun awaitRunDone(
        svc: CopilotAgentPipelineService,
        id: Long,
        timeoutMs: Long = 8_000,
    ): AgentPipelineRun {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val run = svc.get(id)
            if (run != null &&
                run.status != PipelineStatus.PENDING &&
                run.status != PipelineStatus.RUNNING
            ) {
                return run
            }
            Thread.sleep(10)
        }
        val run = svc.get(id)
        throw AssertionError(
            "Run $id did not complete in ${timeoutMs}ms, status=${run?.status}, stage=${run?.currentStage}",
        )
    }

    fun runCancelAtCall(
        callNumber: Int,
        startFn: (CopilotAgentPipelineService) -> Long,
    ): AgentPipelineRun {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            if (callCount.incrementAndGet() == callNumber) {
                reached.countDown()
                release.await(3, TimeUnit.SECONDS)
            }
            "ok"
        }
        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = startFn(svc)
        reached.await(3, TimeUnit.SECONDS)

        svc.get(id)!!.cancelled = true
        release.countDown()
        return awaitRunDone(svc, id)
    }
}
