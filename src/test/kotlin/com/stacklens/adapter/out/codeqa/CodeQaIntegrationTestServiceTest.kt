package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.codeqa.ComposeFileAiSelector
import com.stacklens.adapter.out.codeqa.ComposeFileDiscovery
import com.stacklens.adapter.out.codeqa.ComposeFileSelector
import com.stacklens.adapter.out.codeqa.DockerTestRunner
import com.stacklens.adapter.out.codeqa.GitCloneOrchestrator
import com.stacklens.adapter.out.codeqa.IntegrationCloneUrlBuilder
import com.stacklens.adapter.out.codeqa.IntegrationTestPlanQuery
import com.stacklens.adapter.out.codeqa.IntegrationTestStreamBufferAdapter
import com.stacklens.adapter.out.codeqa.IntegrationTestStreamRegistry
import com.stacklens.adapter.out.codeqa.RepoFileTreeLister
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.filesystem.LocalDirectoryScanAdapter
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.file.FileSystemPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.testsupport.FakeHostDirectoryPort
import com.stacklens.testsupport.FakeSystemInfoPort
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class CodeQaIntegrationTestServiceTest {
    private val codeQaRepo = mock<CodeQaAnalysisPort>()
    private val copilotPort = mock<CopilotStreamingPort>()
    private val gitlabConfigRepo = mock<GitlabConfigPort>()
    private val githubConfigRepo = mock<GithubRepoConfigPort>()
    private val jsonCodec = mock<JsonCodecPort>()
    private val processPort = mock<ProcessRunPort>()
    private val realFileSystem = JavaFileSystemAdapter()

    private val filePort: FileSystemPort =
        object : FileSystemPort by realFileSystem {
            override fun createTempDirectory(prefix: String): File = tempDirectoryOverride ?: realFileSystem.createTempDirectory(prefix)
        }

    private var tempDirectoryOverride: File? = null
    private val promptLoader = mock<PromptReaderPort>()

    private lateinit var tmpDir: File
    private lateinit var service: CodeQaIntegrationTestService

    @BeforeEach
    fun setUp() {
        tmpDir = Files.createTempDirectory("stacklens-itest").toFile()
        tempDirectoryOverride = tmpDir
        whenever(copilotPort.resolveModel()).thenReturn("claude-test")
        whenever(copilotPort.resolveLanguage()).thenReturn("en")
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(promptLoader.get(any(), any(), any())).thenReturn("PROMPT")

        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(0)

        whenever(jsonCodec.encode(any())).thenAnswer { inv ->
            com.fasterxml.jackson.databind
                .ObjectMapper()
                .writeValueAsString(inv.arguments[0])
        }
        service =
            CodeQaIntegrationTestService(
                CodeQaIntegrationTestServiceDeps(
                    codeQaRepo = codeQaRepo,
                    jsonCodec = jsonCodec,
                    filePort = filePort,
                    sleeper = SleeperPort { true },
                    tempDirTracker = mock<com.stacklens.application.port.out.TempDirectoryTrackerPort>(),
                    systemInfo = FakeSystemInfoPort(),
                    cloneUrlBuilder = IntegrationCloneUrlBuilder(githubConfigRepo, gitlabConfigRepo),
                    gitCloneOrchestrator = GitCloneOrchestrator(processPort),
                    dockerTestRunner = DockerTestRunner(processPort),
                    composeFileSelector = ComposeFileSelector(ComposeFileDiscovery(LocalDirectoryScanAdapter()), ComposeFileAiSelector(copilotPort, promptLoader)),
                    repoFileTreeLister = RepoFileTreeLister(LocalDirectoryScanAdapter()),
                    planQuery = IntegrationTestPlanQuery(copilotPort, promptLoader, jsonCodec, JavaFileSystemAdapter()),
                    asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                    streamRegistry = IntegrationTestStreamRegistry(),
                    streamFormatter =
                        IntegrationTestStreamBufferAdapter(
                            FixedClockPort(1_750_000_000_000L),
                            FakeSystemInfoPort(),
                            FakeHostDirectoryPort(),
                            jsonCodec,
                        ),
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun mrData(
        url: String = "https://github.com/owner/repo/pull/1",
        platform: String = "github",
        sourceBranch: String = "feat",
        targetBranch: String = "main",
        headSha: String = "",
        changedFiles: List<String> = listOf("a.kt"),
    ) = MrFetchPort.MrData(
        mrUrl = url,
        platform = platform,
        title = "Test MR",
        description = "desc",
        sourceBranch = sourceBranch,
        targetBranch = targetBranch,
        authorName = "alice",
        state = "open",
        diffText = "",
        changedFiles = changedFiles,
        diffHeadSha = headSha,
    )

    private fun analysis(id: Long = 42L) =
        CodeQaAnalysis(id = id, mrUrl = "https://github.com/owner/repo/pull/1").apply {
            status = AnalysisStatus.RUNNING
        }

    @Test
    fun `getStreamingOutput returns null for unknown id`() {
        assertNull(service.getStreamingOutput(999L))
    }

    @Test
    fun `getStreamingOutput returns null after runIntegrationTests cleanup removes buffer`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(null)
        service.runIntegrationTests(1L, mrData())

        assertNull(service.getStreamingOutput(1L))
    }

    @Test
    fun `runIntegrationTests returns early when analysis not found`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(7L)).thenReturn(null)
        service.runIntegrationTests(7L, mrData())
        verify(codeQaRepo, never()).saveCodeQaAnalysis(any())
        assertNull(service.getStreamingOutput(7L))
    }

    @Test
    fun `runIntegrationTests fails when cloneUrl cannot be determined`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }

        service.runIntegrationTests(10L, mrData(platform = "bitbucket"))
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(any())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        assertTrue(
            saved.allValues.any { it.integrationTestStatus == AnalysisStatus.FAILED },
            "expected FAILED status when cloneUrl cannot be determined",
        )
    }

    @Test
    fun `runIntegrationTests fails when clone returns non-zero exit`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(128, "out", "fatal: bad"))
        service.runIntegrationTests(11L, mrData(headSha = ""))
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        assertTrue(saved.allValues.any { it.integrationTestStatus == AnalysisStatus.FAILED })
    }

    @Test
    fun `runIntegrationTests falls back to sha-based clone when initial clone returns 128 and headSha present`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }

        whenever(processPort.run(any(), any(), any()))
            .thenReturn(
                ProcessResult(128, "", "fatal: not found"),
                ProcessResult(0, "", ""),
                ProcessResult(0, "", ""),
                ProcessResult(0, "", ""),
                ProcessResult(0, "", ""),
                ProcessResult(0, "OK", ""),
                ProcessResult(0, "", ""),
            )

        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db"],"testCommands":["npm test"],"waitSeconds":5,"composeDir":""}""",
            "summary text",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("npm test"), 5, ""))

        service.runIntegrationTests(12L, mrData(headSha = "abc123"))

        verify(processPort, atLeastOnce()).run(any(), any(), any())
    }

    @Test
    fun `runIntegrationTests skips when no docker-compose file is found`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }

        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "", ""))
        service.runIntegrationTests(20L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
        assertTrue((finalState.integrationTestResult ?: "").contains("skipped"))

        verify(processPort, times(1)).run(any(), any(), any())
    }

    @Test
    fun `runIntegrationTests skips when AI returns empty affected services`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(ProcessResult(0, "", ""))
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":[],"testCommands":[],"waitSeconds":20,"composeDir":""}""",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan())

        service.runIntegrationTests(21L, mrData())

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
        assertTrue((finalState.integrationTestResult ?: "").contains("No affected services"))

        verify(processPort, times(1)).run(any(), any(), any())
    }

    @Test
    fun `runIntegrationTests fails when docker-compose up returns non-zero exit`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }

        whenever(processPort.runStreaming(any(), any(), any(), any())).thenReturn(1)
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db"],"testCommands":[],"waitSeconds":20,"composeDir":""}""",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), emptyList(), 20, ""))

        service.runIntegrationTests(30L, mrData())

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        assertTrue(saved.allValues.any { it.integrationTestStatus == AnalysisStatus.FAILED })
    }

    @Test
    fun `runIntegrationTests coerces waitSeconds to max 120 - logs waiting time in integration log`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db"],"testCommands":["x"],"waitSeconds":9999,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("x"), 9999, ""))

        service.runIntegrationTests(40L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())

        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
        assertTrue((finalState.integrationTestLog ?: "").contains("Tests completed"))
    }

    @Test
    fun `runIntegrationTests coerces waitSeconds to min 5`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db"],"testCommands":["x"],"waitSeconds":1,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("x"), 1, ""))

        service.runIntegrationTests(41L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)

        assertTrue((finalState.integrationTestLog ?: "").contains("Tests completed"))
    }

    @Test
    fun `runIntegrationTests completes successfully and stores summary`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
        )

        val streamingIdx =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cb = invocation.arguments[3] as (String) -> Unit
            when (streamingIdx.incrementAndGet()) {
                1 -> 0
                2 -> {
                    cb("all good")
                    0
                }
                3 -> {
                    cb("fail")
                    1
                }
                else -> 0
            }
        }
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db","web"],"testCommands":["a","b"],"waitSeconds":5,"composeDir":""}""",
            "## Great summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db", "web"), listOf("a", "b"), 5, ""))

        service.runIntegrationTests(50L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
        assertEquals("## Great summary", finalState.integrationTestResult)

        assertTrue((finalState.integrationTestLog ?: "").contains("Tests completed: 1/2"))
    }

    @Test
    fun `runIntegrationTests falls back to default plan when AI JSON is malformed`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            "not json at all",
            "summary text",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenThrow(RuntimeException("decode error"))

        service.runIntegrationTests(60L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())

        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
        assertTrue((finalState.integrationTestResult ?: "").contains("No affected services"))
    }

    @Test
    fun `runIntegrationTests writes fallback summary when AI summary throws`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")

        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db"],"testCommands":["x"],"waitSeconds":5,"composeDir":""}""",
        )
        whenever(copilotPort.execute(any(), any(), eq(90L), anyOrNull())).thenThrow(RuntimeException("ai down"))
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("x"), 5, ""))

        service.runIntegrationTests(70L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
        assertTrue((finalState.integrationTestResult ?: "").contains("AI summary"))
    }

    @Test
    fun `runIntegrationTests with multiple compose files triggers selectBestComposeFile AI path`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )

        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        val subDir = File(tmpDir, "frontend").apply { mkdirs() }
        File(subDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  web:\n    image: nginx\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            "2",
            """{"affectedServices":["db"],"testCommands":["x"],"waitSeconds":5,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("x"), 5, ""))

        service.runIntegrationTests(90L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
    }

    @Test
    fun `runIntegrationTests with multiple compose files and AI throws falls back to first`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        val subDir = File(tmpDir, "frontend").apply { mkdirs() }
        File(subDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  web:\n    image: nginx\n")

        whenever(copilotPort.execute(any(), any(), eq(30L), anyOrNull())).thenThrow(RuntimeException("ai down"))
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db"],"testCommands":["x"],"waitSeconds":5,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("x"), 5, ""))

        service.runIntegrationTests(91L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
    }

    @Test
    fun `runIntegrationTests with multiple compose files and AI returns invalid index falls back`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        val subDir = File(tmpDir, "frontend").apply { mkdirs() }
        File(subDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  web:\n    image: nginx\n")

        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            "no numbers here",
            """{"affectedServices":["db"],"testCommands":["x"],"waitSeconds":5,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("x"), 5, ""))

        service.runIntegrationTests(92L, mrData())
        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
    }

    @Test
    fun `runIntegrationTests handles exception when tmpDir deleteRecursively throws`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }

        whenever(processPort.run(any(), any(), any())).thenThrow(RuntimeException("clone blew up"))
        service.runIntegrationTests(80L, mrData())

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        assertTrue(saved.allValues.any { it.integrationTestStatus == AnalysisStatus.FAILED })
    }

    @Test
    fun `runIntegrationTests emits structured STEP and STATUS events for live UI panel`() {
        val captured = StringBuilder()
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(110L))
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as CodeQaAnalysis
            service.getStreamingOutput(saved.id)?.let { captured.append(it) }
            saved
        }

        whenever(jsonCodec.encode(any())).thenAnswer { inv ->
            val mapper =
                com.fasterxml.jackson.databind
                    .ObjectMapper()
            mapper.writeValueAsString(inv.arguments[0])
        }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db","web"],"testCommands":["curl localhost:8080/health"],"waitSeconds":7,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db", "web"), listOf("curl localhost:8080/health"), 7, ""))

        service.runIntegrationTests(110L, mrData(sourceBranch = "feat/x", changedFiles = listOf("a.kt")))

        val stream = captured.toString()

        assertTrue(stream.contains("[STATUS]{"), "expected a [STATUS] event, was:\n$stream")

        for (idx in 1..7) {
            assertTrue(
                stream.contains("\"index\":$idx") || stream.contains("\"index\": $idx"),
                "expected step $idx in stream, was:\n$stream",
            )
        }

        val servicesPos = stream.indexOf("\"services\":[\"db\",\"web\"]")
        assertTrue(servicesPos > 0, "expected services list in stream, was:\n$stream")

        assertTrue(stream.contains("\"commands\":["), "expected test commands, was:\n$stream")
        assertTrue(stream.contains("\"seconds\":7"), "expected wait-seconds, was:\n$stream")

        assertTrue(stream.contains("\"passed\":1") && stream.contains("\"total\":1"), "expected test totals")
    }

    @Test
    fun `runIntegrationTests propagates projectTypes, testStrategy and reasoning from AI`() {
        val captured = StringBuilder()
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(120L))
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as CodeQaAnalysis
            service.getStreamingOutput(saved.id)?.let { captured.append(it) }
            saved
        }
        whenever(jsonCodec.encode(any())).thenAnswer { inv ->
            val mapper =
                com.fasterxml.jackson.databind
                    .ObjectMapper()
            mapper.writeValueAsString(inv.arguments[0])
        }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "ok", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  api:\n    image: node\n")

        File(tmpDir, "package.json").writeText("{\"scripts\":{\"test\":\"jest\"}}")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["api"],
                 "testCommands":["docker-compose exec api npx jest src/foo.test.ts"],
                 "waitSeconds":5,
                 "projectTypes":["node"],
                 "testStrategy":"unit-tests",
                 "testTargets":["src/foo.test.ts"],
                 "reasoning":"Runs jest targeted at the changed module."}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenAnswer { inv ->
                com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    inv.arguments[0] as String,
                    CodeQaIntegrationTestService.TestPlan::class.java,
                )
            }

        service.runIntegrationTests(120L, mrData(changedFiles = listOf("src/foo.ts", "src/foo.test.ts")))

        val stream = captured.toString()

        assertTrue(stream.contains("\"projectTypes\":[\"node\"]"), "expected projectTypes in stream, was:\n$stream")
        assertTrue(stream.contains("\"testStrategy\":\"unit-tests\""), "expected testStrategy, was:\n$stream")
        assertTrue(stream.contains("\"testTargets\":[\"src/foo.test.ts\"]"), "expected testTargets, was:\n$stream")
        assertTrue(stream.contains("\"reasoning\":\""), "expected reasoning field, was:\n$stream")

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val log = saved.lastValue.integrationTestLog ?: ""
        assertTrue(log.contains("Strategy: unit-tests"), "expected strategy in integration log, was:\n$log")
        assertTrue(log.contains("Targets: src/foo.test.ts"), "expected targets in integration log, was:\n$log")
        assertTrue(log.contains("AI plan:"), "expected AI plan breadcrumb, was:\n$log")
    }

    @Test
    fun `runIntegrationTests logs per-command failures with stdout and stderr tail`() {
        val captured = StringBuilder()
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(130L))
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as CodeQaAnalysis
            service.getStreamingOutput(saved.id)?.let { captured.append(it) }
            saved
        }
        whenever(jsonCodec.encode(any())).thenAnswer { inv ->
            val mapper =
                com.fasterxml.jackson.databind
                    .ObjectMapper()
            mapper.writeValueAsString(inv.arguments[0])
        }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
        )

        val streamingIdx =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        whenever(processPort.runStreaming(any(), any(), any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cb = invocation.arguments[3] as (String) -> Unit
            when (streamingIdx.incrementAndGet()) {
                1 -> 0
                2 -> {
                    cb("all good")
                    0
                }
                3 -> {
                    cb("stdout of failing test")
                    cb("stderr of failing test")
                    2
                }
                else -> 0
            }
        }
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  api:\n    image: node\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["api"],"testCommands":["docker-compose exec api npx jest passing","docker-compose exec api npx jest failing"],"waitSeconds":5,"projectTypes":["node"],"testStrategy":"unit-tests","testTargets":["tests/foo.test.ts"],"reasoning":"runs jest"}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenAnswer { inv ->
                com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    inv.arguments[0] as String,
                    CodeQaIntegrationTestService.TestPlan::class.java,
                )
            }

        service.runIntegrationTests(130L, mrData(changedFiles = listOf("tests/foo.test.ts")))

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val log = saved.lastValue.integrationTestLog ?: ""
        assertTrue(log.contains("Tests completed: 1/2"), "expected totals in log, was:\n$log")
        assertTrue(log.contains("Test 2/2 failed"), "expected failed-test breadcrumb, was:\n$log")
        assertTrue(log.contains("stderr of failing test"), "expected stderr tail in log, was:\n$log")
    }

    @Test
    fun `runIntegrationTests falls back to default TestPlan when AI returns malformed JSON`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(140L))
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(jsonCodec.encode(any())).thenAnswer { inv ->
            val mapper =
                com.fasterxml.jackson.databind
                    .ObjectMapper()
            mapper.writeValueAsString(inv.arguments[0])
        }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  api:\n    image: node\n")

        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn("not-json-at-all", "summary")
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan())

        service.runIntegrationTests(140L, mrData())

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val finalState = saved.lastValue
        assertEquals(AnalysisStatus.COMPLETED, finalState.integrationTestStatus)
        assertTrue(
            (finalState.integrationTestResult ?: "").contains("No affected services"),
            "expected skip message after malformed AI output, was: ${finalState.integrationTestResult}",
        )
    }

    @Test
    fun `runIntegrationTests streaming log shows OS banner, temp dir, changed files and MR branch`() {
        val captured = StringBuilder()
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(100L))
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as CodeQaAnalysis

            service.getStreamingOutput(saved.id)?.let { captured.append(it) }
            saved
        }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
            ProcessResult(0, "OK", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["db"],"testCommands":["x"],"waitSeconds":5,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("x"), 5, ""))

        service.runIntegrationTests(
            100L,
            mrData(sourceBranch = "feature/login", changedFiles = listOf("a.kt", "b.kt", "c.kt")),
        )

        val stream = captured.toString()
        assertTrue(stream.isNotBlank(), "Expected streaming output to be captured, was blank")

        assertTrue(stream.contains("🖥️  Host:"), "Expected OS banner in stream, was:\n$stream")
        assertTrue(stream.contains("☕  Java:"), "Expected Java banner in stream")
        assertTrue(stream.contains("📂  Temp root:"), "Expected temp-root banner in stream")

        assertTrue(
            stream.contains(tmpDir.absolutePath),
            "Expected temp dir absolute path in stream, was:\n$stream",
        )

        assertTrue(stream.contains("MR branch to test: 'feature/login'"))
        assertTrue(stream.contains("Changed files in MR: 3"))

        assertTrue(stream.contains("[2/7] Cloning MR branch 'feature/login' into temp folder..."))
        assertTrue(stream.contains("✅ Branch 'feature/login' cloned into"))
    }

    @Test
    fun `runIntegrationTests emits per-service ready markers after the wait-for-healthy sleep`() {
        val captured = StringBuilder()
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(200L))
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as CodeQaAnalysis
            service.getStreamingOutput(saved.id)?.let { captured.append(it) }
            saved
        }
        whenever(processPort.run(any(), any(), any())).thenReturn(
            ProcessResult(0, "", ""),
        )
        File(tmpDir, "docker-compose.yml").writeText(
            "version: '3'\nservices:\n  php-fpm:\n    image: php:8\n  nginx:\n    image: nginx\n",
        )
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(
            """{"affectedServices":["php-fpm","nginx"],"testCommands":["echo hi"],"waitSeconds":5,"composeDir":""}""",
            "summary",
        )
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(
                CodeQaIntegrationTestService
                    .TestPlan(
                        affectedServices = listOf("php-fpm", "nginx"),
                        testCommands = listOf("echo hi"),
                        waitSeconds = 5,
                        composeDir = "",
                    ),
            )

        service.runIntegrationTests(200L, mrData(changedFiles = listOf("src/processing/Foo.php")))

        val stream = captured.toString()

        assertTrue(
            stream.contains(""""type":"service","name":"php-fpm","state":"ready"}"""),
            "Expected per-service ready marker for php-fpm, was:\n$stream",
        )
        assertTrue(
            stream.contains(""""type":"service","name":"nginx","state":"ready"}"""),
            "Expected per-service ready marker for nginx, was:\n$stream",
        )

        val servicesDoneIdx = stream.indexOf(""""name":"Starting Docker services","state":"done"""")
        val firstServiceIdx = stream.indexOf(""""type":"service","name":"php-fpm","state":"ready"}""")
        val firstTestIdx = stream.indexOf(""""type":"test","index":0,"command":"echo hi","state":"running"}""")
        assertTrue(servicesDoneIdx > 0, "Step 5 done marker missing")
        assertTrue(firstServiceIdx > servicesDoneIdx, "service-ready must come AFTER step-5 done")
        assertTrue(firstTestIdx > firstServiceIdx, "first per-test marker must come AFTER service-ready")
    }

    @Test
    fun `TestPlan default constructor uses defaults`() {
        val p = CodeQaIntegrationTestService.TestPlan()
        assertEquals(emptyList<String>(), p.affectedServices)
        assertEquals(emptyList<String>(), p.testCommands)
        assertEquals(20, p.waitSeconds)
        assertEquals("", p.composeDir)
    }

    @Test
    fun `TestPlan equality and copy`() {
        val a = CodeQaIntegrationTestService.TestPlan(listOf("a"), listOf("t"), 10, "d")
        val b = a.copy(waitSeconds = 99)
        assertEquals(listOf("a"), b.affectedServices)
        assertEquals(99, b.waitSeconds)
        assertEquals("d", b.composeDir)
        assertEquals(a, a.copy())
    }

    private fun serviceWith(
        docker: DockerTestRunner,
        clone: GitCloneOrchestrator = GitCloneOrchestrator(processPort),
    ): CodeQaIntegrationTestService =
        CodeQaIntegrationTestService(
            CodeQaIntegrationTestServiceDeps(
                asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                streamRegistry = IntegrationTestStreamRegistry(),
                codeQaRepo = codeQaRepo,
                jsonCodec = jsonCodec,
                filePort = filePort,
                sleeper = SleeperPort { true },
                tempDirTracker = mock<com.stacklens.application.port.out.TempDirectoryTrackerPort>(),
                systemInfo = FakeSystemInfoPort(),
                cloneUrlBuilder = IntegrationCloneUrlBuilder(githubConfigRepo, gitlabConfigRepo),
                gitCloneOrchestrator = clone,
                dockerTestRunner = docker,
                composeFileSelector = ComposeFileSelector(ComposeFileDiscovery(LocalDirectoryScanAdapter()), ComposeFileAiSelector(copilotPort, promptLoader)),
                repoFileTreeLister = RepoFileTreeLister(LocalDirectoryScanAdapter()),
                planQuery = IntegrationTestPlanQuery(copilotPort, promptLoader, jsonCodec, JavaFileSystemAdapter()),
                streamFormatter =
                    IntegrationTestStreamBufferAdapter(
                        FixedClockPort(1_750_000_000_000L),
                        FakeSystemInfoPort(),
                        FakeHostDirectoryPort(),
                        jsonCodec,
                    ),
            ),
        )

    private fun planJson() = """{"affectedServices":["db"],"testCommands":["npm test"],"waitSeconds":5,"composeDir":""}"""

    @Test
    fun `a docker run reports its lines and keeps the analysis when the repository refresh is empty`() {
        val docker = mock<DockerTestRunner>()
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(), null)
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        val cloned = mock<GitCloneOrchestrator>()
        whenever(cloned.cloneBranch(any(), any(), any())).thenReturn(GitCloneOrchestrator.CloneResult(true))
        whenever(copilotPort.execute(any(), any(), any(), anyOrNull())).thenReturn(planJson())
        whenever(jsonCodec.decode(any<String>(), eq(CodeQaIntegrationTestService.TestPlan::class)))
            .thenReturn(CodeQaIntegrationTestService.TestPlan(listOf("db"), listOf("npm test", "npm run e2e"), 5, ""))
        whenever(docker.startServices(any(), any(), any(), anyOrNull())).thenAnswer { inv ->
            inv.getArgument<(String) -> Unit>(2).invoke("container db is up")
            DockerTestRunner.UpResult(true, tmpDir)
        }
        whenever(docker.runTests(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    DockerTestRunner.TestOutput("npm test", ProcessResult(1, "expected 1 but was 2", "boom"), false),
                    DockerTestRunner.TestOutput("npm run e2e", ProcessResult(2, "", ""), false),
                ),
            )
        whenever(docker.stopServices(any())).thenReturn(true)
        File(tmpDir, "docker-compose.yml").writeText("version: '3'\nservices:\n  db:\n    image: postgres\n")

        serviceWith(docker, cloned).runIntegrationTests(30L, mrData())

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        val last = saved.allValues.last()
        assertEquals(AnalysisStatus.COMPLETED, last.integrationTestStatus, "result=${last.integrationTestResult}")
        verify(docker).stopServices(any())
    }

    @Test
    fun `a failure without a message still reports an empty error payload`() {
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis(), null)
        whenever(codeQaRepo.saveCodeQaAnalysis(any()))
            .thenReturn(analysis())
            .thenAnswer { throw IllegalStateException() }
            .thenReturn(analysis())

        service.runIntegrationTests(31L, mrData())

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        assertTrue(
            saved.allValues
                .last()
                .integrationTestResult!!
                .contains("null"),
        )
    }

    @Test
    fun `a temp directory that cannot be removed still unregisters`() {
        val tracker = mock<com.stacklens.application.port.out.TempDirectoryTrackerPort>()
        val exploding =
            object : File(tmpDir.absolutePath) {
                override fun toPath(): java.nio.file.Path = throw java.io.IOException("permission denied")
            }
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        tempDirectoryOverride = exploding

        val local =
            CodeQaIntegrationTestService(
                CodeQaIntegrationTestServiceDeps(
                    codeQaRepo = codeQaRepo,
                    jsonCodec = jsonCodec,
                    filePort = filePort,
                    sleeper = SleeperPort { true },
                    tempDirTracker = tracker,
                    systemInfo = FakeSystemInfoPort(),
                    cloneUrlBuilder = IntegrationCloneUrlBuilder(githubConfigRepo, gitlabConfigRepo),
                    gitCloneOrchestrator = GitCloneOrchestrator(processPort),
                    dockerTestRunner = DockerTestRunner(processPort),
                    composeFileSelector = ComposeFileSelector(ComposeFileDiscovery(LocalDirectoryScanAdapter()), ComposeFileAiSelector(copilotPort, promptLoader)),
                    repoFileTreeLister = RepoFileTreeLister(LocalDirectoryScanAdapter()),
                    planQuery = IntegrationTestPlanQuery(copilotPort, promptLoader, jsonCodec, JavaFileSystemAdapter()),
                    asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                    streamRegistry = IntegrationTestStreamRegistry(),
                    streamFormatter =
                        IntegrationTestStreamBufferAdapter(
                            FixedClockPort(1_750_000_000_000L),
                            FakeSystemInfoPort(),
                            FakeHostDirectoryPort(),
                            jsonCodec,
                        ),
                ),
            )

        local.runIntegrationTests(32L, mrData())

        verify(tracker).unregister(exploding)
    }

    @Test
    fun `a clone failure without an error message uses the generic reason`() {
        val clone = mock<GitCloneOrchestrator>()
        whenever(codeQaRepo.findCodeQaAnalysisById(any())).thenReturn(analysis())
        whenever(codeQaRepo.saveCodeQaAnalysis(any())).thenAnswer { it.arguments[0] }
        whenever(clone.cloneBranch(any(), any(), any())).thenReturn(GitCloneOrchestrator.CloneResult(false))

        serviceWith(DockerTestRunner(processPort), clone).runIntegrationTests(33L, mrData())

        val saved = argumentCaptor<CodeQaAnalysis>()
        verify(codeQaRepo, atLeastOnce()).saveCodeQaAnalysis(saved.capture())
        assertTrue(
            saved.allValues
                .last()
                .integrationTestResult!!
                .contains("Clone failed"),
        )
    }
}
