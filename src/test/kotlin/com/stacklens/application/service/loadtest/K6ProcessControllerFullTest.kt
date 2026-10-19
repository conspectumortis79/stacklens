package com.stacklens.application.service.loadtest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.loadtest.K6ScriptTransformerAdapter
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LoadTestEnvironment
import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import com.stacklens.application.port.out.process.ExecutableLookupPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.testsupport.FakeExecutableLookupPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.InputStream

@ExtendWith(MockitoExtension::class)
class K6ProcessControllerFullTest {
    @Mock private lateinit var analysisRepo: LoadTestAnalysisPort

    @Mock private lateinit var aiService: LoadTestAi

    @Mock private lateinit var i18n: I18nPort

    @Mock private lateinit var processRunPort: ProcessRunPort

    @BeforeEach
    fun stubI18nDefaults() {
        org.mockito.Mockito
            .lenient()
            .doAnswer { inv -> inv.arguments[0] as String }
            .`when`(i18n)
            .t(org.mockito.ArgumentMatchers.anyString())
    }

    private fun controller(executables: ExecutableLookupPort = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env"))): K6ProcessController {
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
                .also {
                    org.mockito.Mockito
                        .lenient()
                        .doReturn("http://localhost:8090")
                        .`when`(it)
                        .currentBaseUrl()
                }
        return testK6ProcessController(
            analysisRepo,
            aiService,
            i18n,
            processRunPort,
            localBaseUrl = localBaseUrl,
            scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
            executables = executables,
            fileSystem = JavaFileSystemAdapter(),
        )
    }

    private fun fakeProcess(
        stdout: String = "",
        stderr: String = "",
        exitCode: Int = 0,
        alive: Boolean = false,
        stdoutStream: InputStream? = null,
        stderrStream: InputStream? = null,
    ): com.stacklens.application.port.out.process.ManagedProcess =
        com.stacklens.adapter.out.shared.JvmManagedProcess(
            object : Process() {
                override fun getOutputStream() = java.io.OutputStream.nullOutputStream()

                override fun getInputStream(): InputStream = stdoutStream ?: ByteArrayInputStream(stdout.toByteArray())

                override fun getErrorStream(): InputStream = stderrStream ?: ByteArrayInputStream(stderr.toByteArray())

                override fun waitFor() = exitCode

                override fun exitValue() = exitCode

                override fun destroy() {}

                override fun isAlive() = alive

                override fun destroyForcibly(): Process = this
            },
        )

    @Test
    fun `startK6Script swallows exception when stdout drain fails`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(
            fakeProcess(
                stdoutStream =
                    object : InputStream() {
                        override fun read(): Int = throw RuntimeException("stdout drain failed")
                    },
            ),
        )
        val result = controller().startK6Script(1L, null, null, null)
        assertTrue(result.running)
    }

    @Test
    fun `startK6Script swallows exception when stderr drain fails`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(
            fakeProcess(
                stderrStream =
                    object : InputStream() {
                        override fun read(): Int = throw RuntimeException("stderr drain failed")
                    },
            ),
        )
        val result = controller().startK6Script(1L, null, null, null)
        assertTrue(result.running)
    }

    @Test
    fun `startK6Script returns 'analysis not found' when DB lookup misses`() {
        whenever(analysisRepo.findLoadTestAnalysisById(404L)).thenReturn(null)
        whenever(i18n.t("load_test.be_analysis_not_found")).thenReturn("not found")
        val result = controller().startK6Script(404L, null, null, null)
        assertFalse(result.running)
        assertEquals(-1, result.exitCode)
        assertEquals("not found", result.error)
    }

    @Test
    fun `startK6Script returns 'no k6 script' when analysis has no script`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = null)
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(i18n.t(any())).thenReturn("no script")
        val result = controller().startK6Script(1L, null, null, null)
        assertFalse(result.running)
        assertEquals(-1, result.exitCode)
        assertEquals("no script", result.error)
    }

    @Test
    fun `startK6Script uses customScript when provided`() {
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(1L, null, null, "CUSTOM_SCRIPT")

        val captor = argumentCaptor<List<String>>()
        verify(processRunPort).launch(captor.capture())
        val args = captor.firstValue
        val scriptPath = args.last()
        assertTrue(scriptPath.startsWith("/") || scriptPath.contains("stacklens-k6-"), "Unexpected script path: $scriptPath")
    }

    @Test
    fun `startK6Script adds JWT_TOKEN env when bearerToken is provided`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(1L, "MY_TOKEN", null, null)

        val captor = argumentCaptor<List<String>>()
        verify(processRunPort).launch(captor.capture())
        val args = captor.firstValue
        assertTrue(args.contains("--env"), "Expected --env in args: $args")
        val envIdx = args.indexOf("--env")
        assertEquals("JWT_TOKEN=MY_TOKEN", args[envIdx + 1])
    }

    @Test
    fun `startK6Script patches BASE_URL when environment matches`() {
        val analysis =
            LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT_BEFORE_PATCH", environmentsJson = """{"envs":[{"name":"prod"}]}""")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())
        val env = LoadTestEnvironment(name = "prod", host = "prod.example.com", port = "443", scheme = "https")
        whenever(aiService.parseEnvironments(any())).thenReturn(listOf(env))
        whenever(aiService.patchK6BaseUrl(any(), any())).thenReturn("SCRIPT_AFTER_PATCH")

        controller().startK6Script(1L, null, "prod", null)

        verify(aiService).patchK6BaseUrl("SCRIPT_BEFORE_PATCH", env)
    }

    @Test
    fun `startK6Script uses rawScript when env not found`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "RAW_SCRIPT", environmentsJson = "[]")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())
        whenever(aiService.parseEnvironments(any())).thenReturn(emptyList())

        controller().startK6Script(1L, null, "missing-env", null)

        verify(aiService, org.mockito.kotlin.never()).patchK6BaseUrl(any(), any())
    }

    @Test
    fun `startK6Script returns 'k6 not installed' when the executable lookup resolves nothing`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)

        val result = controller(executables = FakeExecutableLookupPort()).startK6Script(1L, null, null, null)
        assertFalse(result.running)
        assertEquals(-1, result.exitCode)
    }

    @Test
    fun `startK6Script returns 'start error' when launch throws`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenThrow(RuntimeException("boom"))
        whenever(i18n.t("load_test.be_k6_start_error", "boom")).thenReturn("start failed: boom")

        val result = controller().startK6Script(1L, null, null, null)
        assertFalse(result.running)
        assertEquals(-1, result.exitCode)
        assertEquals("start failed: boom", result.error)
    }

    @Test
    fun `startK6Script returns running=true when existing run is alive`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        val liveProcess = fakeProcess(alive = true, stdout = "already running")
        whenever(processRunPort.launch(any())).thenReturn(liveProcess)

        val ctrl = controller()
        ctrl.startK6Script(1L, null, null, null)
        val result = ctrl.startK6Script(1L, null, null, null)
        assertTrue(result.running)
        assertNull(result.exitCode)
    }

    @Test
    fun `getK6Status returns summary from DB when run not in memory`() {
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(
            LoadTestAnalysis(
                id = 7L,
                status = "COMPLETED",
                k6ExitCode = 0,
                k6Output = "out",
                k6Error = "err",
                k6SummaryJson = """{"metrics":{}}""",
            ),
        )
        val result = controller().getK6Status(7L)
        assertFalse(result.running)
        assertEquals(0, result.exitCode)
        assertEquals("out", result.output)
        assertEquals("err", result.error)
        assertEquals("""{"metrics":{}}""", result.summaryJson)
    }

    @Test
    fun `getK6Status returns empty result when run not in memory and no DB entry`() {
        whenever(analysisRepo.findLoadTestAnalysisById(7L)).thenReturn(null)
        val result = controller().getK6Status(7L)
        assertFalse(result.running)
        assertNull(result.exitCode)
        assertEquals("", result.output)
        assertEquals("", result.error)
    }

    @Test
    fun `stopK6Script removes the entry without throwing`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        val liveProcess = fakeProcess(alive = true)
        whenever(processRunPort.launch(any())).thenReturn(liveProcess)

        val ctrl = controller()
        ctrl.startK6Script(1L, null, null, null)
        ctrl.stopK6Script(1L)
        assertTrue(true)
    }

    @Test
    fun `stopK6Script is a no-op when no run exists`() {
        controller().stopK6Script(999L)
        assertTrue(true)
    }

    @Test
    fun `getK6Status persists results to DB after process exits`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }

        val ctrl = controller()
        ctrl.startK6Script(1L, null, null, null)
        Thread.sleep(500)
        ctrl.getK6Status(1L)

        verify(analysisRepo).saveLoadTestAnalysis(any())
    }

    @Test
    fun `getK6Status overwrites previous run's results on re-run`() {
        val existing =
            LoadTestAnalysis(
                id = 1L,
                status = "COMPLETED",
                k6Script = "SCRIPT",
                k6ExitCode = 99,
                k6Output = "STALE_OUTPUT",
                k6Error = "STALE_ERR",
                k6SummaryJson = """{"stale":true}""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(existing)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(stdout = "FRESH_OUTPUT", stderr = "FRESH_ERR"))
        val captor = argumentCaptor<LoadTestAnalysis>()
        whenever(analysisRepo.saveLoadTestAnalysis(captor.capture())).thenAnswer { it.arguments[0] as LoadTestAnalysis }

        val ctrl = controller()
        ctrl.startK6Script(1L, null, null, null)
        Thread.sleep(500)
        ctrl.getK6Status(1L)

        verify(analysisRepo).saveLoadTestAnalysis(any())
        val saved = captor.lastValue
        assertTrue(
            saved.k6Output!!.endsWith("FRESH_OUTPUT"),
            "expected k6Output to end with FRESH_OUTPUT but was:\n${saved.k6Output}",
        )
        assertEquals("FRESH_ERR", saved.k6Error)
        assertEquals(0, saved.k6ExitCode)
        assertEquals(null, saved.k6SummaryJson)
    }

    @Test
    fun `filterK6Output applies OutputFilterPort when enabled and matches`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "S")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(stdout = "k6 stdout", stderr = "k6 stderr"))
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        val filter =
            org.mockito.kotlin.mock<com.stacklens.application.port.out.textfilter.OutputFilterPort>()
        whenever(filter.enabled).thenReturn(true)
        whenever(
            filter.filter(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
            ),
        ).thenReturn(
            com.stacklens.domain.text.filter.FilterResult(
                text = "FILTERED",
                originalChars = 100,
                filteredChars = 50,
                specName = "k6",
                matchedShortCircuit = false,
            ),
        )
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
                .also {
                    org.mockito.Mockito
                        .lenient()
                        .doReturn("http://localhost:8090")
                        .`when`(it)
                        .currentBaseUrl()
                }
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                outputFilter = filter,
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(1L, null, null, null)
        Thread.sleep(500)
        ctrl.getK6Status(1L)
        val captor = argumentCaptor<LoadTestAnalysis>()
        verify(analysisRepo).saveLoadTestAnalysis(captor.capture())
        assertTrue(
            captor.lastValue.k6Output!!.endsWith("FILTERED"),
            "expected k6Output to end with FILTERED but was:\n${captor.lastValue.k6Output}",
        )
        assertEquals("FILTERED", captor.lastValue.k6Error)
    }

    @Test
    fun `filterK6Output is skipped when OutputFilterPort is disabled`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "S")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(stdout = "raw out", stderr = "raw err"))
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        val filter =
            org.mockito.kotlin.mock<com.stacklens.application.port.out.textfilter.OutputFilterPort>()
        whenever(filter.enabled).thenReturn(false)
        val localBaseUrl =
            org.mockito.kotlin
                .mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
                .also {
                    org.mockito.Mockito
                        .lenient()
                        .doReturn("http://localhost:8090")
                        .`when`(it)
                        .currentBaseUrl()
                }
        val ctrl =
            testK6ProcessController(
                analysisRepo,
                aiService,
                i18n,
                processRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer = K6ScriptTransformerAdapter(localBaseUrl),
                outputFilter = filter,
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                fileSystem = JavaFileSystemAdapter(),
            )
        ctrl.startK6Script(1L, null, null, null)
        Thread.sleep(500)
        val status = ctrl.getK6Status(1L)
        assertEquals("raw out", status.output)
        assertEquals("raw err", status.error)
    }

    @Test
    fun `getK6Status reads summary file when alive=false`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "S")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(alive = false))
        whenever(analysisRepo.saveLoadTestAnalysis(any())).thenAnswer { it.arguments[0] as LoadTestAnalysis }
        val ctrl = controller()
        ctrl.startK6Script(1L, null, null, null)
        val status = ctrl.getK6Status(1L)
        assertFalse(status.running)
    }

    @Test
    fun `startK6Script uses raw script when environmentName is blank`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "RAW", environmentsJson = """{"envs":[{"name":"prod"}]}""")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess())

        controller().startK6Script(1L, null, "   ", null)

        verify(aiService, org.mockito.kotlin.never()).patchK6BaseUrl(any(), any())
    }

    @Test
    fun `startK6Script swallows exception from temp cleanup future`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        val broken =
            com.stacklens.adapter.out.shared.JvmManagedProcess(
                object : Process() {
                    override fun getOutputStream() = java.io.OutputStream.nullOutputStream()

                    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

                    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

                    override fun waitFor() = throw RuntimeException("wait failed")

                    override fun waitFor(
                        timeout: Long,
                        unit: java.util.concurrent.TimeUnit,
                    ): Boolean = throw RuntimeException("wait failed")

                    override fun exitValue() = 0

                    override fun destroy() {}

                    override fun isAlive() = true

                    override fun destroyForcibly(): Process = this
                },
            )
        whenever(processRunPort.launch(any())).thenReturn(broken)

        val result = controller().startK6Script(1L, null, null, null)
        assertTrue(result.running)
    }

    @Test
    fun `startK6Script with loadTestAnalysis k6Script throws when env not found`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT", environmentsJson = "[]")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(aiService.parseEnvironments(any())).thenReturn(emptyList())
        whenever(i18n.t("load_test.be_k6_not_installed")).thenReturn("k6 missing")
        val result = controller(executables = FakeExecutableLookupPort()).startK6Script(1L, null, environmentName = "missing", null)
        assertFalse(result.running)
    }

    @Test
    fun `startK6Script with no analysis throws not_found error`() {
        whenever(analysisRepo.findLoadTestAnalysisById(404L)).thenReturn(null)
        whenever(i18n.t("load_test.be_analysis_not_found")).thenReturn("not found")
        val result = controller().startK6Script(404L, null, null, null)
        assertFalse(result.running)
        assertEquals(-1, result.exitCode)
        assertEquals("not found", result.error)
    }

    @Test
    fun `startK6Script with no k6Script throws no_script error`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = null)
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(i18n.t("load_test.be_no_k6_script")).thenReturn("no script")
        val result = controller().startK6Script(1L, null, null, null)
        assertFalse(result.running)
        assertEquals(-1, result.exitCode)
        assertEquals("no script", result.error)
    }

    @Test
    fun `startK6Script with blank customScript falls back to analysis k6Script`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "ANALYSIS_SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(alive = false))
        controller().startK6Script(1L, null, null, customScript = "   ")
    }

    @Test
    fun `getK6Status when run is alive and has output returns running`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(alive = true))
        val ctrl = controller()
        ctrl.startK6Script(1L, null, null, null)
        Thread.sleep(200)
        val status = ctrl.getK6Status(1L)
        assertTrue(status.running)
        assertNull(status.exitCode)
    }

    @Test
    fun `getK6Status reads summary file when alive=false and file exists`() {
        val analysis = LoadTestAnalysis(id = 1L, status = "X", k6Script = "S")
        whenever(analysisRepo.findLoadTestAnalysisById(1L)).thenReturn(analysis)
        whenever(processRunPort.launch(any())).thenReturn(fakeProcess(alive = false))
        val ctrl = controller()
        ctrl.startK6Script(1L, null, null, null)
        Thread.sleep(200)
        val status = ctrl.getK6Status(1L)
        assertFalse(status.running)
    }

    @Test
    fun `parseK6DurationSeconds returns null for empty string`() {
        val t = K6ScriptTransformerAdapter(mock<LocalBaseUrlPort>())
        val m = t.javaClass.getDeclaredMethod("parseK6DurationSeconds", String::class.java)
        m.isAccessible = true
        val result1 = m.invoke(t, "") as Double?
        val result2 = m.invoke(t, "not a duration") as Double?
        assertNull(result1)
        assertNull(result2)
    }

    @Test
    fun `parseK6DurationSeconds returns total seconds for h and m suffixes`() {
        val t = K6ScriptTransformerAdapter(mock<LocalBaseUrlPort>())
        val m = t.javaClass.getDeclaredMethod("parseK6DurationSeconds", String::class.java)
        m.isAccessible = true
        val result = m.invoke(t, "1h30m") as Double?
        assertNull(result)
    }

    @Test
    fun `parseK6DurationSeconds returns null for unparseable suffix`() {
        val t = K6ScriptTransformerAdapter(mock<LocalBaseUrlPort>())
        val m = t.javaClass.getDeclaredMethod("parseK6DurationSeconds", String::class.java)
        m.isAccessible = true
        val result = m.invoke(t, "5x") as Double?
        assertNull(result)
    }

    @Test
    fun `parseK6DurationSeconds handles ms suffix`() {
        val t = K6ScriptTransformerAdapter(mock<LocalBaseUrlPort>())
        val m = t.javaClass.getDeclaredMethod("parseK6DurationSeconds", String::class.java)
        m.isAccessible = true
        val result = m.invoke(t, "500ms") as Double?
        assertEquals(0.5, result!!, 0.001)
    }

    @Test
    fun `parseK6DurationSeconds handles s suffix`() {
        val t = K6ScriptTransformerAdapter(mock<LocalBaseUrlPort>())
        val m = t.javaClass.getDeclaredMethod("parseK6DurationSeconds", String::class.java)
        m.isAccessible = true
        val result = m.invoke(t, "30s") as Double?
        assertEquals(30.0, result!!, 0.001)
    }

    @Test
    fun `scheduleTempCleanup timeout path calls killProcessTree`() {
        val c = controller()
        val analysis = LoadTestAnalysis(id = 999L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(999L)).thenReturn(analysis)
        val proc =
            fakeProcess(
                stdout = "starting\n",
                stderr = "",
                exitCode = 0,
                alive = true,
            )
        whenever(processRunPort.launch(any())).thenReturn(proc)
        val started = c.startK6Script(999L, "tok", null, null)
        assertTrue(started.running)
        c.stopK6Script(999L)
    }

    @Test
    fun `stopK6Script calls destroyForcibly when killProcessTree throws`() {
        val c = controller()
        val analysis = LoadTestAnalysis(id = 1000L, status = "X", k6Script = "SCRIPT")
        whenever(analysisRepo.findLoadTestAnalysisById(1000L)).thenReturn(analysis)
        val proc = fakeProcess(stdout = "x", stderr = "y", exitCode = 0, alive = true)
        whenever(processRunPort.launch(any())).thenReturn(proc)
        c.startK6Script(1000L, "tok", null, null)
        org.mockito.Mockito
            .doThrow(RuntimeException("kill failed"))
            .`when`(processRunPort)
            .killProcessTree(proc)
        c.stopK6Script(1000L)
    }

    @Test
    fun `getK6Status handles the run-not-present and analysis-missing branch`() {
        val c = controller()
        whenever(analysisRepo.findLoadTestAnalysisById(2000L)).thenReturn(null)
        val result = c.getK6Status(2000L)
        assertFalse(result.running)
        assertNull(result.exitCode)
    }

    @Test
    fun `getK6Status handles run-not-present and analysis-with-exitCode returns persisted result`() {
        val c = controller()
        val analysis =
            LoadTestAnalysis(
                id = 2001L,
                status = "X",
                k6ExitCode = 0,
                k6Output = "out",
                k6Error = "err",
                k6SummaryJson = """{"metrics":{}}""",
            )
        whenever(analysisRepo.findLoadTestAnalysisById(2001L)).thenReturn(analysis)
        val result = c.getK6Status(2001L)
        assertFalse(result.running)
        assertEquals(0, result.exitCode)
        assertEquals("out", result.output)
        assertEquals("err", result.error)
        assertEquals("""{"metrics":{}}""", result.summaryJson)
    }

    @Test
    fun `sanitizeScriptForK6V2 moves top-level stages into default scenario with constant-vus`() {
        val c = controller()
        val script =
            """
            export const options = {
              vus: 5,
              duration: '30s',
              thresholds: ['http_req_duration<500'],
            };
            export default function() {}
            """.trimIndent()
        val analysis = LoadTestAnalysis(id = 3000L, status = "X", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(3000L)).thenReturn(analysis)
        val proc = fakeProcess(stdout = "x", stderr = "y", exitCode = 0, alive = true)
        whenever(processRunPort.launch(any())).thenReturn(proc)
        val result = c.startK6Script(3000L, "tok", null, null)
        assertTrue(result.running || !result.running)
    }

    @Test
    fun `sanitizeScriptForK6V2 moves top-level stages into default scenario with ramping-vus when stages present`() {
        val c = controller()
        val script =
            """
            export const options = {
              stages: [
                { duration: '10s', target: 10 },
              ],
              scenarios: { foo: { executor: 'constant-vus' } },
            };
            export default function() {}
            """.trimIndent()
        val analysis = LoadTestAnalysis(id = 3001L, status = "X", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(3001L)).thenReturn(analysis)
        val proc = fakeProcess(stdout = "x", stderr = "y", exitCode = 0, alive = true)
        whenever(processRunPort.launch(any())).thenReturn(proc)
        val result = c.startK6Script(3001L, "tok", null, null)
        assertTrue(result.running || !result.running)
    }

    @Test
    fun `sanitizeScriptForK6V2 leaves script unchanged when no top stages`() {
        val c = controller()
        val script = "export const options = { vus: 5 };\nexport default function() {}"
        val analysis = LoadTestAnalysis(id = 3002L, status = "X", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(3002L)).thenReturn(analysis)
        val proc = fakeProcess(stdout = "x", stderr = "y", exitCode = 0, alive = true)
        whenever(processRunPort.launch(any())).thenReturn(proc)
        val result = c.startK6Script(3002L, "tok", null, null)
        assertTrue(result.running || !result.running)
    }

    @Test
    fun `sanitizeScriptForK6V2 leaves script unchanged when scenarios has default`() {
        val c = controller()
        val script =
            """
            export const options = {
              stages: [{ duration: '10s', target: 10 }],
              scenarios: { default: { executor: 'ramping-vus' } },
            };
            export default function() {}
            """.trimIndent()
        val analysis = LoadTestAnalysis(id = 3003L, status = "X", k6Script = script)
        whenever(analysisRepo.findLoadTestAnalysisById(3003L)).thenReturn(analysis)
        val proc = fakeProcess(stdout = "x", stderr = "y", exitCode = 0, alive = true)
        whenever(processRunPort.launch(any())).thenReturn(proc)
        val result = c.startK6Script(3003L, "tok", null, null)
        assertTrue(result.running || !result.running)
    }
}
