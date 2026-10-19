package com.stacklens.application.service.loadtest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.loadtest.K6ScriptTransformerAdapter
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LoadTestEnvironment
import com.stacklens.application.port.out.process.ExecutableLookupPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.testsupport.FakeExecutableLookupPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.ByteArrayInputStream
import java.io.InputStream

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class K6ProcessControllerTest {
    @Mock private lateinit var analysisRepo: LoadTestAnalysisPort

    @Mock private lateinit var aiService: LoadTestAi

    @Mock private lateinit var i18n: I18nPort

    @Mock private lateinit var processRunPort: ProcessRunPort

    @BeforeEach
    fun stubI18nDefaults() {
        whenever(i18n.t(any<String>())).thenAnswer { it.arguments[0] as String }
    }

    private fun controller(executables: ExecutableLookupPort = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env"))): K6ProcessController {
        val baseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
                .also {
                    org.mockito.Mockito
                        .doReturn("http://localhost:8090")
                        .`when`(it)
                        .currentBaseUrl()
                }
        return testK6ProcessController(
            analysisRepo,
            aiService,
            i18n,
            processRunPort,
            localBaseUrl = baseUrl,
            scriptTransformer = K6ScriptTransformerAdapter(baseUrl),
            executables = executables,
            fileSystem = JavaFileSystemAdapter(),
        )
    }

    private fun fakeProcess(
        stdout: String = "",
        stderr: String = "",
        exitCode: Int = 0,
    ): com.stacklens.application.port.out.process.ManagedProcess {
        val outStream: InputStream = ByteArrayInputStream(stdout.toByteArray())
        val errStream: InputStream = ByteArrayInputStream(stderr.toByteArray())
        return com.stacklens.adapter.out.shared.JvmManagedProcess(
            object : Process() {
                override fun getOutputStream() = java.io.OutputStream.nullOutputStream()

                override fun getInputStream() = outStream

                override fun getErrorStream() = errStream

                override fun waitFor() = exitCode

                override fun exitValue() = exitCode

                override fun destroy() {}

                override fun isAlive() = false
            },
        )
    }

    private fun blockingFakeProcess(
        stdout: String = "",
        stderr: String = "",
        exitCode: Int = 0,
    ): Pair<com.stacklens.application.port.out.process.ManagedProcess, java.util.concurrent.CountDownLatch> {
        val outStream: InputStream = ByteArrayInputStream(stdout.toByteArray())
        val errStream: InputStream = ByteArrayInputStream(stderr.toByteArray())
        val latch = java.util.concurrent.CountDownLatch(1)
        val process =
            com.stacklens.adapter.out.shared.JvmManagedProcess(
                object : Process() {
                    override fun getOutputStream() = java.io.OutputStream.nullOutputStream()

                    override fun getInputStream() = outStream

                    override fun getErrorStream() = errStream

                    override fun waitFor(): Int {
                        latch.await()
                        return exitCode
                    }

                    override fun waitFor(
                        timeout: Long,
                        unit: java.util.concurrent.TimeUnit,
                    ): Boolean {
                        val signalled = latch.await(timeout, unit)
                        if (signalled) {
                            return true
                        }
                        return false
                    }

                    override fun exitValue() = exitCode

                    override fun destroy() {}

                    override fun isAlive() = latch.count > 0
                },
            )
        return process to latch
    }

    @Test
    fun `startK6Script omits --log-output=none so k6 prints warnings to stderr`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "COMPLETED", k6Script = "export default function(){}")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        val captor = argumentCaptor<List<String>>()
        controller().startK6Script(1L, null, null, null)
        verify(processRunPort).launch(captor.capture())

        val args = captor.firstValue
        assertFalse(args.contains("--log-output=none"), "k6 should NOT be invoked with --log-output=none (it suppresses stderr warnings); got: $args")
        assertTrue(args.contains("--no-color"), "Expected --no-color in args: $args")
        assertTrue(args.contains("--summary-export"), "Expected --summary-export in args: $args")
    }

    @Test
    fun `getK6Status caps stdout at MAX_STDOUT and stderr at MAX_STDERR after process exits`() {
        val maxStdout = 500_000
        val maxStderr = 100_000
        val bigStdout = "A".repeat(maxStdout + 10_000)
        val bigStderr = "B".repeat(maxStderr + 5_000)

        val analysis = LoadTestAnalysis(id = 2L, status = "COMPLETED", k6Script = "export default function(){}")
        whenever(analysisRepo.findLoadTestAnalysisById(2L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(stdout = bigStdout, stderr = bigStderr))
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenReturn(analysis)

        val ctrl = controller()
        ctrl.startK6Script(2L, null, null, null)

        Thread.sleep(500)

        val status = ctrl.getK6Status(2L)

        assertFalse(status.running)
        assertTrue(status.output.length <= maxStdout, "stdout length ${status.output.length} exceeds $maxStdout")
        assertTrue(status.error.length <= maxStderr, "stderr length ${status.error.length} exceeds $maxStderr")
    }

    @Test
    fun `getK6Status returns db-persisted result when process already cleaned up`() {
        whenever(analysisRepo.findLoadTestAnalysisById(99L)).thenReturn(
            LoadTestAnalysis(id = 99L, status = "COMPLETED", k6ExitCode = 0, k6Output = "ok", k6Error = ""),
        )

        val status = controller().getK6Status(99L)

        assertFalse(status.running)
        assertEquals(0, status.exitCode)
        assertEquals("ok", status.output)
    }

    @Test
    fun `getK6Status returns empty result when no run and no db entry`() {
        whenever(analysisRepo.findLoadTestAnalysisById(42L)).thenReturn(null)

        val status = controller().getK6Status(42L)

        assertFalse(status.running)
        assertEquals(null, status.exitCode)
        assertEquals("", status.output)
    }

    @Test
    fun `resolveToolBin forwards tool and extra candidates to the executable lookup`() {
        val lookup = FakeExecutableLookupPort(resolved = mapOf("k6" to "/custom/bin/k6"))
        val resolved = controller(executables = lookup).resolveToolBin("k6", listOf("/custom/bin/k6"))
        assertEquals("/custom/bin/k6", resolved)
        assertEquals(listOf("k6" to listOf("/custom/bin/k6")), lookup.resolveCalls)
    }

    @Test
    fun `resolveToolBin returns null when the executable lookup resolves nothing`() {
        assertEquals(null, controller(executables = FakeExecutableLookupPort()).resolveToolBin("definitely-no-such-tool"))
    }

    @Test
    fun `startK6Script removes dead process entry before starting new run`() {
        val analysis = LoadTestAnalysis(id = 11L, status = "COMPLETED", k6Script = "export default function(){}")
        whenever(analysisRepo.findLoadTestAnalysisById(11L)).thenReturn(analysis)

        val deadProcess =
            object : Process() {
                override fun getOutputStream() = java.io.OutputStream.nullOutputStream()

                override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

                override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

                override fun waitFor() = 0

                override fun exitValue() = 0

                override fun destroy() {}

                override fun isAlive() = false
            }
        whenever(processRunPort.launch(any())).thenReturn(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(deadProcess),
        )
        val ctrl = controller()
        ctrl.startK6Script(11L, null, null, null)
        Thread.sleep(100)
        ctrl.startK6Script(11L, null, null, null)
    }

    @Test
    fun `startK6Script uses customScript when non-blank`() {
        val analysis = LoadTestAnalysis(id = 12L, status = "COMPLETED", k6Script = "export default function(){}")
        whenever(analysisRepo.findLoadTestAnalysisById(12L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())
        controller().startK6Script(12L, null, null, customScript = "// custom")
        verify(processRunPort).launch(any())
    }

    @Test
    fun `startK6Script adds JWT_TOKEN env when bearerToken non-blank`() {
        val analysis = LoadTestAnalysis(id = 14L, status = "COMPLETED", k6Script = "export default function(){}")
        whenever(analysisRepo.findLoadTestAnalysisById(14L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        val captor = argumentCaptor<List<String>>()
        controller().startK6Script(14L, "MY_JWT", null, null)
        verify(processRunPort).launch(captor.capture())
        val args = captor.firstValue
        assertTrue(args.contains("--env"))
        assertTrue(args.any { it.startsWith("JWT_TOKEN=") })
    }

    @Test
    fun `startK6Script patches BASE_URL when environmentName matches`() {
        val analysis =
            LoadTestAnalysis(
                id = 15L,
                status = "COMPLETED",
                k6Script = "export default function(){http.get(__ENV.BASE_URL)}",
                environmentsJson = """[{"name":"prod","baseUrl":"https://prod.com"}]""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(15L)).thenReturn(analysis)
        whenever(aiService.parseEnvironments(analysis.environmentsJson)).thenReturn(
            listOf(
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment(name = "prod", host = "prod.com", port = "443", scheme = "https"),
            ),
        )
        whenever(aiService.patchK6BaseUrl(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as String).replace("BASE_URL", "https://prod.com")
        }
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(15L, null, "prod", null)
        verify(aiService).patchK6BaseUrl(any(), any())
    }

    @Test
    fun `startK6Script uses rawScript unchanged when environmentName not found`() {
        val analysis =
            LoadTestAnalysis(
                id = 16L,
                status = "COMPLETED",
                k6Script = "export default function(){}",
                environmentsJson = """[{"name":"staging","baseUrl":"https://staging.com"}]""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(16L)).thenReturn(analysis)
        whenever(aiService.parseEnvironments(analysis.environmentsJson)).thenReturn(
            listOf(
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment(name = "staging", host = "staging.com", port = "443", scheme = "https"),
            ),
        )
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(16L, null, "nonexistent-env", null)
        org.mockito.kotlin
            .verify(aiService, org.mockito.kotlin.never())
            .patchK6BaseUrl(any(), any())
    }

    @Test
    fun `startK6Script catches Exception and returns i18n error`() {
        whenever(analysisRepo.findLoadTestAnalysisById(17L)).thenThrow(RuntimeException("boom"))
        whenever(i18n.t("load_test.be_k6_start_error", "boom")).thenReturn("start failed: boom")
        val result = controller().startK6Script(17L, null, null, null)
        assertFalse(result.running)
        assertTrue(result.error.contains("start failed"))
    }

    @Test
    fun `startK6Script returns analysis-not-found when repo returns null`() {
        whenever(analysisRepo.findLoadTestAnalysisById(18L)).thenReturn(null)
        whenever(i18n.t("load_test.be_analysis_not_found")).thenReturn("not found")
        val result = controller().startK6Script(18L, null, null, null)
        assertFalse(result.running)
        assertTrue(result.error.contains("not found"))
    }

    @Test
    fun `startK6Script returns no-k6-script when both scripts are null`() {
        val analysis = LoadTestAnalysis(id = 19L, status = "COMPLETED", k6Script = null)
        whenever(analysisRepo.findLoadTestAnalysisById(19L)).thenReturn(analysis)
        whenever(i18n.t("load_test.be_no_k6_script")).thenReturn("no script")
        val result = controller().startK6Script(19L, null, null, null)
        assertFalse(result.running)
        assertTrue(result.error.contains("no script"))
    }

    @Test
    fun `getK6Status returns DB-persisted result when analysis has k6ExitCode set`() {
        val analysis =
            LoadTestAnalysis(
                id = 20L,
                status = "COMPLETED",
                k6Script = "x",
                k6ExitCode = 7,
                k6Output = "out",
                k6Error = "err",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(20L)).thenReturn(analysis)
        val status = controller().getK6Status(20L)
        assertFalse(status.running)
        assertEquals(7, status.exitCode)
        assertEquals("out", status.output)
        assertEquals("err", status.error)
    }

    @Test
    fun `getK6Status returns empty result when analysis has no k6ExitCode`() {
        val analysis =
            LoadTestAnalysis(
                id = 21L,
                status = "COMPLETED",
                k6Script = "x",
                k6ExitCode = null,
            )
        whenever(analysisRepo.findLoadTestAnalysisById(21L)).thenReturn(analysis)
        val status = controller().getK6Status(21L)
        assertFalse(status.running)
        assertEquals(null, status.exitCode)
        assertEquals("", status.output)
        assertEquals("", status.error)
    }

    @Test
    fun `getK6Status returns empty strings for null k6Output and k6Error`() {
        val analysis =
            LoadTestAnalysis(
                id = 22L,
                status = "COMPLETED",
                k6Script = "x",
                k6ExitCode = 0,
                k6Output = null,
                k6Error = null,
            )
        whenever(analysisRepo.findLoadTestAnalysisById(22L)).thenReturn(analysis)
        val status = controller().getK6Status(22L)
        assertFalse(status.running)
        assertEquals(0, status.exitCode)
        assertEquals("", status.output)
        assertEquals("", status.error)
    }

    @Test
    fun `startK6Script uses bearer token when non-blank adds env flag`() {
        val analysis = LoadTestAnalysis(id = 30L, status = "COMPLETED", k6Script = "x")
        whenever(analysisRepo.findLoadTestAnalysisById(30L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        val captor = argumentCaptor<List<String>>()
        controller().startK6Script(30L, "MY_TOKEN", null, null)
        verify(processRunPort).launch(captor.capture())
        val args = captor.firstValue
        assertTrue(args.contains("--env"))
        assertTrue(args.any { it.startsWith("JWT_TOKEN=MY_TOKEN") })
    }

    @Test
    fun `startK6Script does not add env when bearerToken is blank`() {
        val analysis = LoadTestAnalysis(id = 31L, status = "COMPLETED", k6Script = "x")
        whenever(analysisRepo.findLoadTestAnalysisById(31L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        val captor = argumentCaptor<List<String>>()
        controller().startK6Script(31L, "  ", null, null)
        verify(processRunPort).launch(captor.capture())
        val args = captor.firstValue
        assertFalse(args.contains("--env"))
    }

    @Test
    fun `startK6Script falls back to analysis_k6Script when customScript is null`() {
        val analysis = LoadTestAnalysis(id = 32L, status = "COMPLETED", k6Script = "// analysis script")
        whenever(analysisRepo.findLoadTestAnalysisById(32L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        val captor = argumentCaptor<List<String>>()
        controller().startK6Script(32L, null, null, null)
        verify(processRunPort).launch(captor.capture())
        assertTrue(captor.firstValue.last().endsWith(".js"))
    }

    @Test
    fun `startK6Script falls back to analysis_k6Script when customScript is blank`() {
        val analysis = LoadTestAnalysis(id = 33L, status = "COMPLETED", k6Script = "// analysis")
        whenever(analysisRepo.findLoadTestAnalysisById(33L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(33L, null, null, customScript = "   ")
        verify(processRunPort).launch(any())
    }

    @Test
    fun `startK6Script uses environment when found and patches k6BaseUrl`() {
        val analysis =
            LoadTestAnalysis(
                id = 34L,
                status = "COMPLETED",
                k6Script = "// x",
                environmentsJson = """[{"name":"prod","host":"prod.com","port":"443","scheme":"https"}]""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(34L)).thenReturn(analysis)
        whenever(aiService.parseEnvironments(analysis.environmentsJson)).thenReturn(
            listOf(
                com.stacklens.application.port.out.loadtest.LoadTestEnvironment(
                    name = "prod",
                    host = "prod.com",
                    port = "443",
                    scheme = "https",
                ),
            ),
        )
        whenever(aiService.patchK6BaseUrl(any(), any())).thenAnswer { it.arguments[0] as String }
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(34L, null, "prod", null)
        verify(aiService).patchK6BaseUrl(any(), any())
    }

    @Test
    fun `startK6Script skips env block when environmentName is blank`() {
        val analysis =
            LoadTestAnalysis(
                id = 35L,
                status = "COMPLETED",
                k6Script = "// x",
                environmentsJson = """[{"name":"prod","host":"prod.com","port":"443","scheme":"https"}]""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(35L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(35L, null, "", null)
        verify(aiService, org.mockito.kotlin.never()).parseEnvironments(any())
    }

    @Test
    fun `startK6Script patches k6BaseUrl when env name matches`() {
        val analysis =
            LoadTestAnalysis(
                id = 36L,
                status = "COMPLETED",
                k6Script = "// x",
                environmentsJson = """[{"name":"prod","host":"prod.com","port":"443","scheme":"https"}]""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(36L)).thenReturn(analysis)
        whenever(aiService.parseEnvironments(analysis.environmentsJson)).thenReturn(
            listOf(
                com.stacklens.application.port.out.loadtest.LoadTestEnvironment(
                    name = "prod",
                    host = "prod.com",
                    port = "443",
                    scheme = "https",
                ),
            ),
        )
        whenever(aiService.patchK6BaseUrl(any(), any())).thenReturn("// patched")
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(36L, null, "prod", null)
        verify(aiService).patchK6BaseUrl(any(), any())
    }

    @Test
    fun `getK6Status returns running=true when process is alive`() {
        val analysis = LoadTestAnalysis(id = 40L, status = "COMPLETED", k6Script = "x")
        whenever(analysisRepo.findLoadTestAnalysisById(40L)).thenReturn(analysis)
        val liveProcess =
            object : Process() {
                override fun getOutputStream() = java.io.OutputStream.nullOutputStream()

                override fun getInputStream(): InputStream = ByteArrayInputStream("running...".toByteArray())

                override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

                override fun waitFor() = 0

                override fun exitValue() = 0

                override fun destroy() {}

                override fun isAlive() = true
            }
        whenever(processRunPort.launch(any())).thenReturn(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(liveProcess),
        )
        val ctrl = controller()
        ctrl.startK6Script(40L, null, null, null)
        val status = ctrl.getK6Status(40L)
        assertTrue(status.running)
        assertEquals(null, status.exitCode)
    }

    @Test
    fun `getK6Status processes summary file when process is dead`() {
        val summaryFile = java.io.File.createTempFile("k6-summary-", ".json")
        summaryFile.writeText("""{"metrics":{}}""")
        val analysis = LoadTestAnalysis(id = 41L, status = "COMPLETED", k6Script = "x")
        whenever(analysisRepo.findLoadTestAnalysisById(41L)).thenReturn(analysis)

        val deadProcess =
            object : Process() {
                override fun getOutputStream() = java.io.OutputStream.nullOutputStream()

                override fun getInputStream(): InputStream = ByteArrayInputStream("out".toByteArray())

                override fun getErrorStream(): InputStream = ByteArrayInputStream("err".toByteArray())

                override fun waitFor(): Int = 0

                override fun exitValue(): Int = 0

                override fun destroy() {}

                override fun isAlive() = false
            }

        val mockProcess = org.mockito.kotlin.mock<Process>()
        org.mockito.Mockito
            .`when`(mockProcess.isAlive)
            .thenReturn(false)
        org.mockito.Mockito
            .`when`(mockProcess.exitValue())
            .thenReturn(0)
        org.mockito.Mockito.`when`(mockProcess.inputStream).thenReturn(
            java.io.ByteArrayInputStream("out".toByteArray()),
        )
        org.mockito.Mockito.`when`(mockProcess.errorStream).thenReturn(
            java.io.ByteArrayInputStream("err".toByteArray()),
        )
        whenever(processRunPort.launch(any())).thenReturn(
            com.stacklens.adapter.out.shared
                .JvmManagedProcess(mockProcess),
        )
        val ctrl = controller()
        ctrl.startK6Script(41L, null, null, null)
        val status = ctrl.getK6Status(41L)
        assertFalse(status.running)
        assertEquals(0, status.exitCode)
    }

    @Test
    fun `startK6Script patches template default 8085 BASE_URL to local instance URL when no customScript and no env`() {
        val script =
            "const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';\nexport default function(){}"
        val analysis = LoadTestAnalysis(id = 50L, status = "COMPLETED", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(50L)).thenReturn(analysis)
        val (proc, latch) = blockingFakeProcess()
        whenever(processRunPort.launch(any())).thenReturn(proc)

        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()

        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(50L, null, null, null)
        try {
            val captor = argumentCaptor<List<String>>()
            verify(processRunPort).launch(captor.capture())
            val scriptFile = captor.firstValue.last()
            val written = java.io.File(scriptFile).readText()
            assertTrue(
                written.contains("'http://localhost:8090'"),
                "Expected BASE_URL patched to local instance, got:\n$written",
            )
            assertFalse(written.contains("localhost:8085"), "Template default 8085 must not survive the patch")
        } finally {
            latch.countDown()
        }
    }

    @Test
    fun `startK6Script patches placeholder http invalid 0 BASE_URL to local instance URL`() {
        val script =
            "const BASE_URL = __ENV.BASE_URL || 'http://invalid:0';\nexport default function(){}"
        val analysis = LoadTestAnalysis(id = 51L, status = "COMPLETED", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(51L)).thenReturn(analysis)
        val (proc, latch) = blockingFakeProcess()
        whenever(processRunPort.launch(any())).thenReturn(proc)

        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:9090")
            .`when`(localBaseUrl)
            .currentBaseUrl()

        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(51L, null, null, null)
        try {
            val captor = argumentCaptor<List<String>>()
            verify(processRunPort).launch(captor.capture())
            val written = java.io.File(captor.firstValue.last()).readText()
            assertTrue(written.contains("'http://localhost:9090'"), "Expected patched to localhost:9090, got:\n$written")
            assertFalse(written.contains("invalid:0"), "Placeholder 'invalid:0' must not survive the patch")
        } finally {
            latch.countDown()
        }
    }

    @Test
    fun `startK6Script always rewrites BASE_URL to the local instance URL`() {
        val script =
            "const BASE_URL = 'https://staging.example.com:9000';\nexport default function(){}"
        val analysis = LoadTestAnalysis(id = 52L, status = "COMPLETED", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(52L)).thenReturn(analysis)
        val (proc, latch) = blockingFakeProcess()
        whenever(processRunPort.launch(any())).thenReturn(proc)

        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()

        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(52L, null, null, null)
        org.mockito.Mockito
            .verify(localBaseUrl, org.mockito.kotlin.atLeastOnce())
            .currentBaseUrl()
        try {
            val captor = argumentCaptor<List<String>>()
            verify(processRunPort).launch(captor.capture())
            val written = java.io.File(captor.firstValue.last()).readText()
            assertTrue(
                written.contains("http://localhost:8090"),
                "Patcher must rewrite to local instance URL, got:\n$written",
            )
            assertFalse(
                written.contains("staging.example.com"),
                "Patcher must NOT leave the AI-generated remote URL in the script, got:\n$written",
            )
        } finally {
            latch.countDown()
        }
    }

    @Test
    fun `startK6Script does NOT patch the script when user supplied a customScript`() {
        val custom =
            "const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';\nexport default function(){}"
        val (proc, latch) = blockingFakeProcess()
        whenever(processRunPort.launch(any())).thenReturn(proc)

        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()

        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(60L, null, null, customScript = custom)
        try {
            val captor = argumentCaptor<List<String>>()
            verify(processRunPort).launch(captor.capture())
            val written = java.io.File(captor.firstValue.last()).readText()
            assertTrue(
                written.contains("localhost:8085"),
                "customScript must be passed through verbatim, got:\n$written",
            )
        } finally {
            latch.countDown()
        }
    }

    @Test
    fun `startK6Script lets the env-selector win over the local default patch`() {
        val script =
            "const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';\nexport default function(){}"
        val analysis =
            LoadTestAnalysis(
                id = 70L,
                status = "COMPLETED",
                k6Script = script,
                environmentsJson = """[{"name":"staging","host":"stg.example.com","port":"8443","scheme":"https"}]""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(70L)).thenReturn(analysis)
        whenever(aiService.parseEnvironments(analysis.environmentsJson)).thenReturn(
            listOf(
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("staging", "stg.example.com", "8443", "https"),
            ),
        )
        whenever(aiService.patchK6BaseUrl(any(), any())).thenAnswer { invocation ->
            (invocation.arguments[0] as String).replace(
                Regex("""(?m)(const|let|var)\s+BASE_URL\s*=\s*(?:__ENV\.BASE_URL\s*\|\|\s*)?['"][^'"]*['"]"""),
                "const BASE_URL = 'https://stg.example.com:8443'",
            )
        }
        val (proc, latch) = blockingFakeProcess()
        whenever(processRunPort.launch(any())).thenReturn(proc)

        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()

        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(70L, null, "staging", null)
        try {
            val captor = argumentCaptor<List<String>>()
            verify(processRunPort).launch(captor.capture())
            val written = java.io.File(captor.firstValue.last()).readText()
            assertTrue(
                written.contains("https://stg.example.com:8443"),
                "Env-selector URL must win, got:\n$written",
            )
            assertFalse(
                written.contains("http://localhost:8090"),
                "Local URL must not overwrite the env-selector choice, got:\n$written",
            )
        } finally {
            latch.countDown()
        }
    }

    @Test
    fun `patchScriptForLocalDefault rewrites 8085 default`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val patched =
            ctrl.patchScriptForLocalDefault(
                "const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';\nfoo",
            )
        assertTrue(patched.contains("'http://localhost:8090'"), "patched=\n$patched")
        assertFalse(patched.contains("localhost:8085"))
    }

    @Test
    fun `patchScriptForLocalDefault always rewrites to local instance URL even for remote-looking input`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val remoteLooking = "const BASE_URL = 'https://staging.acme.io:9001';"
        val patched = ctrl.patchScriptForLocalDefault(remoteLooking)
        assertTrue(
            patched.contains("'http://localhost:8090'"),
            "expected forced patch to localhost:8090 but got: $patched",
        )
        assertFalse(patched.contains("staging.acme.io"))
    }

    @Test
    fun `patchScriptForLocalDefault leaves script unchanged when no BASE_URL is present`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val body = "export default function(){ /* no BASE_URL here */ }"
        assertEquals(body, ctrl.patchScriptForLocalDefault(body))
    }

    @Test
    fun `isSuspiciousTemplateDefault rejects remote hosts and real local ports`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        assertTrue(ctrl.isSuspiciousTemplateDefault("http://localhost:8085"))
        assertTrue(ctrl.isSuspiciousTemplateDefault("http://localhost:80"))
        assertTrue(ctrl.isSuspiciousTemplateDefault("http://invalid:0"))
        assertTrue(ctrl.isSuspiciousTemplateDefault("https://localhost"))
        assertFalse(ctrl.isSuspiciousTemplateDefault("http://localhost:8090"))
        assertFalse(ctrl.isSuspiciousTemplateDefault("https://staging.acme.io:9001"))
        assertFalse(ctrl.isSuspiciousTemplateDefault("http://10.0.0.5:8080"))
    }

    @Test
    fun `isSuspiciousTemplateDefault flags localhost URL whose port differs from running server`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        assertTrue(ctrl.isSuspiciousTemplateDefault("http://localhost:8080"))
        assertTrue(ctrl.isSuspiciousTemplateDefault("http://localhost:9090"))
        assertTrue(ctrl.isSuspiciousTemplateDefault("http://127.0.0.1:8080"))
        assertFalse(ctrl.isSuspiciousTemplateDefault("http://localhost:8090"))
    }

    private fun scenarioSection(script: String): String = script.substringAfter("scenarios:", "")

    @Test
    fun `sanitizeScriptForK6V2 strips per-scenario gracefulRampDown and promotes it to top level`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val script =
            """
            export const options = {
              scenarios: {
                main_traffic: {
                  executor: 'ramping-vus',
                  stages: [
                    { duration: '30s', target: 20 },
                    { duration: '60s', target: 20 },
                  ],
                  gracefulRampDown: '10s',
                  exec: 'mainTraffic',
                },
                destructive_bench: {
                  executor: 'constant-vus',
                  vus: 5,
                  duration: '30s',
                  gracefulRampDown: '5s',
                  exec: 'destructiveBench',
                },
              },
            };
            """.trimIndent()
        val sanitised = ctrl.sanitizeScriptForK6V2(script)
        val occurrences = Regex("""gracefulRampDown\s*:""").findAll(sanitised).count()
        assertEquals(
            1,
            occurrences,
            "Exactly one `gracefulRampDown:` line must remain (top-level), got:\n$sanitised",
        )
        assertTrue(
            sanitised.contains("gracefulRampDown: '10s'"),
            "Largest scenario gracefulRampDown must be promoted to top-level, got:\n$sanitised",
        )
        assertFalse(
            scenarioSection(sanitised).contains("gracefulRampDown"),
            "No per-scenario gracefulRampDown may remain, got:\n$sanitised",
        )
    }

    @Test
    fun `sanitizeScriptForK6V2 leaves gracefulRampDown untouched when no per-scenario key`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val script =
            """
            export const options = {
              scenarios: {
                main: { executor: 'ramping-vus', stages: [] },
              },
            };
            """.trimIndent()
        val sanitised = ctrl.sanitizeScriptForK6V2(script)
        assertFalse(
            sanitised.contains("gracefulRampDown"),
            "No gracefulRampDown key was present, none should be added:\n$sanitised",
        )
        assertTrue(
            sanitised.contains("gracefulStop: '5s'"),
            "gracefulStop should be injected with the 5s default:\n$sanitised",
        )
    }

    @Test
    fun `sanitizeScriptForK6V2 keeps an existing top-level gracefulRampDown untouched`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val script =
            """
            export const options = {
              gracefulRampDown: '60s',
              scenarios: {
                main: {
                  executor: 'ramping-vus',
                  stages: [],
                  gracefulRampDown: '5s',
                },
              },
            };
            """.trimIndent()
        val sanitised = ctrl.sanitizeScriptForK6V2(script)
        assertTrue(sanitised.contains("gracefulRampDown: '60s'"), "Top-level value must be preserved:\n$sanitised")
        assertFalse(
            scenarioSection(sanitised).contains("gracefulRampDown"),
            "Per-scenario 5s must be stripped, got:\n$sanitised",
        )
    }

    @Test
    fun `sanitizeScriptForK6V2 injects a top-level gracefulStop when the script omits it`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val script =
            """
            const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
            export const options = {
              stages: [ { duration: '30s', target: 20 } ],
            };
            export default function() {}
            """.trimIndent()
        val sanitised = ctrl.sanitizeScriptForK6V2(script)
        assertTrue(
            sanitised.contains("gracefulStop: '5s'"),
            "Expected injected gracefulStop:'5s', got:\n$sanitised",
        )
    }

    @Test
    fun `sanitizeScriptForK6V2 keeps an existing top-level gracefulStop untouched`() {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        val script =
            """
            export const options = {
              gracefulStop: '15s',
              stages: [ { duration: '30s', target: 20 } ],
            };
            export default function() {}
            """.trimIndent()
        val sanitised = ctrl.sanitizeScriptForK6V2(script)
        assertTrue(
            sanitised.contains("gracefulStop: '15s'"),
            "User-set gracefulStop must be preserved:\n$sanitised",
        )
        assertFalse(
            sanitised.contains("gracefulStop: '5s'"),
            "Must not overwrite a user-set gracefulStop with the 5s default:\n$sanitised",
        )
    }

    @Test
    fun `startK6Script end-to-end AI script with per-scenario gracefulRampDown no longer exits 104`() {
        val script =
            """
            const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
            export const options = {
              scenarios: {
                main: {
                  executor: 'ramping-vus',
                  stages: [ { duration: '1s', target: 1 } ],
                  gracefulRampDown: '10s',
                },
              },
            };
            export default function() {}
            """.trimIndent()
        val analysis = LoadTestAnalysis(id = 80L, status = "COMPLETED", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(80L)).thenReturn(analysis)
        val (proc, latch) = blockingFakeProcess()
        whenever(processRunPort.launch(any())).thenReturn(proc)

        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
        org.mockito.Mockito
            .doReturn("http://localhost:8090")
            .`when`(localBaseUrl)
            .currentBaseUrl()

        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(80L, null, null, null)
        try {
            val captor = argumentCaptor<List<String>>()
            verify(processRunPort).launch(captor.capture())
            val written = java.io.File(captor.firstValue.last()).readText()
            assertFalse(
                scenarioSection(written).contains("gracefulRampDown"),
                "Per-scenario gracefulRampDown must be stripped from the script the k6 process actually runs:\n$written",
            )
            val occurrences = Regex("""gracefulRampDown\s*:""").findAll(written).count()
            assertEquals(
                1,
                occurrences,
                "Exactly one `gracefulRampDown:` line must remain (top-level), got:\n$written",
            )
            assertTrue(
                written.contains("gracefulRampDown: '10s'"),
                "Value must be promoted to top-level options:\n$written",
            )
        } finally {
            latch.countDown()
        }
    }
}
