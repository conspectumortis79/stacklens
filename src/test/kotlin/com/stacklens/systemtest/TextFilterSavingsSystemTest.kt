package com.stacklens.systemtest

import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.service.loadtest.K6ProcessController
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.testsupport.FakeExecutableLookupPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:filter-savings-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    ],
)
@AutoConfigureMockMvc
class TextFilterSavingsSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var analysisRepo: LoadTestAnalysisPort

    @Autowired
    lateinit var k6Controller: K6ProcessController

    @Autowired
    lateinit var configPort: TextFilterConfigPort

    @Autowired
    lateinit var localRepoConfigRepo: com.stacklens.application.port.out.LocalRepoConfigPort

    @TestConfiguration
    class FakeK6ProcessConfig {
        @Bean
        @Primary
        fun k6ProcessController(
            analysisRepo: LoadTestAnalysisPort,
            aiService: com.stacklens.application.service.loadtest.LoadTestAi,
            i18n: com.stacklens.application.port.out.i18n.I18nPort,
            fakeProcessRunPort: ProcessRunPort,
            outputFilter: com.stacklens.application.port.out.textfilter.OutputFilterPort,
            localBaseUrl: com.stacklens.application.port.out.loadtest.LocalBaseUrlPort,
            runRegistry: com.stacklens.application.service.loadtest.K6RunRegistry,
            launcher: com.stacklens.application.service.loadtest.K6ProcessLauncher,
            statusReporter: com.stacklens.application.service.loadtest.K6StatusReporter,
        ): K6ProcessController =
            K6ProcessController(
                analysisRepo = analysisRepo,
                aiService = aiService,
                i18n = i18n,
                processRunPort = fakeProcessRunPort,
                localBaseUrl = localBaseUrl,
                scriptTransformer =
                    com.stacklens.adapter.out.loadtest
                        .K6ScriptTransformerAdapter(localBaseUrl),
                outputFilter = outputFilter,
                executables = FakeExecutableLookupPort(resolved = mapOf("k6" to "/usr/bin/env")),
                runRegistry = runRegistry,
                launcher = launcher,
                statusReporter = statusReporter,
            )

        @Bean
        @Primary
        fun localBaseUrlPort(): com.stacklens.application.port.out.loadtest.LocalBaseUrlPort {
            val mock =
                org.mockito.kotlin.mock<com.stacklens.application.port.out.loadtest.LocalBaseUrlPort>()
            org.mockito.Mockito
                .doReturn("http://localhost:8090")
                .`when`(mock)
                .currentBaseUrl()
            return mock
        }

        @Bean
        @Primary
        fun fakeProcessRunPort(): ProcessRunPort =
            object : ProcessRunPort {
                override fun killProcessTree(process: com.stacklens.application.port.out.process.ManagedProcess) {
                    runCatching { process.killTree() }
                }

                override fun run(
                    cmd: List<String>,
                    timeoutSec: Long,
                    workDir: File?,
                ): ProcessResult {
                    val stdout = buildK6Stdout()
                    return ProcessResult(exitCode = 0, stdout = stdout, stderr = "")
                }

                override fun launch(cmd: List<String>): com.stacklens.application.port.out.process.ManagedProcess =
                    com.stacklens.adapter.out.shared
                        .JvmManagedProcess(FakeK6Process(buildK6Stdout(), ""))

                override fun runStreaming(
                    cmd: List<String>,
                    workDir: File?,
                    timeoutSec: Long,
                    onLine: (String) -> Unit,
                ): Int {
                    cmd.forEach(onLine)
                    return 0
                }
            }

        private fun buildK6Stdout(): String {
            val sb = StringBuilder()
            sb.appendLine("          /\\      |‾‾| /‾‾/   /‾‾/ ")
            sb.appendLine("     /\\  /  \\     |  |/  /   /  / ")
            sb.appendLine("    /  \\/    \\    |     (   /   ‾‾\\")
            sb.appendLine("   /          \\   |  |\\  \\ |  (‾)  |")
            sb.appendLine("  / __________ \\  |__| \\__\\ \\_____/ .io")
            sb.appendLine()
            sb.appendLine("  execution: local")
            sb.appendLine("     script: /tmp/stacklens-k6-fake.js")
            sb.appendLine("     output: -")
            sb.appendLine()
            sb.appendLine("  scenarios: (100.00%) 1 scenario, 10 max VUs, 1m30s max duration (incl. graceful stop):")
            sb.appendLine("           * default: 10 looping VUs for 30s (gracefulStop: 30s)")
            sb.appendLine()
            sb.appendLine()
            sb.appendLine("     ✓ http_status_200")
            sb.appendLine("     ✓ response_time<500")
            sb.appendLine("     ✗ response_time<200")
            sb.appendLine("      ↳  0% — ✓ 0 / ✗ 5421")
            sb.appendLine()
            sb.appendLine("     checks..................: 99.43% ✓ 7821 ✗ 45")
            sb.appendLine("     data_received..........: 1.2 MB 12 kB/s")
            sb.appendLine("     data_sent..............: 142 kB 1.4 kB/s")
            sb.appendLine("     http_req_blocked.......: avg=1.2ms  min=1µs  med=2µs  max=212ms p(90)=3.2ms p(95)=5.1ms")
            sb.appendLine("     http_req_connecting....: avg=12ms   min=0s   med=10µs max=2.1s  p(90)=32ms  p(95)=45ms")
            sb.appendLine("     http_req_duration......: avg=42ms   min=12ms med=35ms max=4.2s  p(90)=120ms  p(95)=95ms")
            sb.appendLine("     http_req_failed.........: 0.00%  ✓ 0    ✗ 5421")
            sb.appendLine("     http_req_receiving.....: avg=212µs  min=12µs med=180µs max=12ms p(90)=420µs p(95)=780µs")
            sb.appendLine("     http_req_sending.......: avg=42µs   min=8µs  med=32µs  max=4ms   p(90)=92µs  p(95)=180µs")
            sb.appendLine("     http_req_tls_handshaking: avg=0s     min=0s   med=0s   max=0s   p(90)=0s    p(95)=0s")
            sb.appendLine("     http_req_waiting.......: avg=41ms   min=11ms med=34ms max=4.2s  p(90)=119ms  p(95)=94ms")
            sb.appendLine("     http_reqs..............: 5421    180.6/s")
            sb.appendLine("     iteration_duration.....: avg=1.5s   min=1s   med=1.5s max=12s   p(90)=1.2s   p(95)=2.1s")
            sb.appendLine("     iterations.............: 5421    180.6/s")
            sb.appendLine("     vus....................: 10     min=10  max=10")
            sb.appendLine("     vus_max................: 10     min=10  max=10")
            sb.appendLine("     dropped_iterations.....: 0      0/s")
            sb.appendLine("     network_data_received..: 1.2 MB 12 kB/s")
            sb.appendLine("     network_data_sent......: 142 kB 1.4 kB/s")
            sb.appendLine()
            sb.appendLine("running (0m30.0s), 00/10 VUs, 5421 and 45/45 VUs once, 0 unexpected responses")
            sb.appendLine("     ✗ response_time<200")
            sb.appendLine("      ↳  0% — ✓ 0 / ✗ 5421")
            return sb.toString()
        }
    }

    private class FakeK6Process(
        private val stdoutText: String,
        private val stderrText: String,
    ) : Process() {
        private val stdoutDrained = AtomicBoolean(false)
        private val stderrDrained = AtomicBoolean(false)

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

        override fun getInputStream(): InputStream =
            TrackedInputStream(
                ByteArrayInputStream(stdoutText.toByteArray()),
            ) { stdoutDrained.set(true) }

        override fun getErrorStream(): InputStream =
            TrackedInputStream(
                ByteArrayInputStream(stderrText.toByteArray()),
            ) { stderrDrained.set(true) }

        override fun waitFor(): Int = 0

        override fun waitFor(
            timeout: Long,
            unit: java.util.concurrent.TimeUnit,
        ): Boolean = true

        override fun exitValue(): Int = 0

        override fun isAlive(): Boolean = !(stdoutDrained.get() && stderrDrained.get())

        override fun destroy() {}

        override fun destroyForcibly(): Process = this
    }

    private class TrackedInputStream(
        private val delegate: InputStream,
        private val onClose: () -> Unit,
    ) : InputStream() {
        override fun read(): Int = delegate.read()

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = delegate.read(b, off, len)

        override fun read(b: ByteArray): Int = delegate.read(b)

        override fun available(): Int = delegate.available()

        override fun close() {
            delegate.close()
            onClose()
        }
    }

    @Test
    fun `k6 output persisted to DB is significantly smaller when filter is enabled`() {
        val analysis = createAnalysisWithLargeK6Script()
        val analysisId = analysis.id

        configPort.setEnabled(true)
        runFakeK6AndPersist(analysisId)
        val enabledPersisted = persistedK6OutputOrThrow(analysisId)
        val enabledSize = enabledPersisted.length

        configPort.setEnabled(false)
        val secondAnalysis = createAnalysisWithLargeK6Script()
        runFakeK6AndPersist(secondAnalysis.id)
        val disabledPersisted = persistedK6OutputOrThrow(secondAnalysis.id)
        val disabledSize = disabledPersisted.length

        assertTrue(
            enabledSize < disabledSize,
            "filter on must produce smaller output (on=$enabledSize, off=$disabledSize)",
        )

        val savedBytes = disabledSize - enabledSize
        val savedPct = (savedBytes * 100) / disabledSize
        println("=== k6 filter savings ===")
        println("  raw k6 output    : $disabledSize bytes")
        println("  filtered output  : $enabledSize bytes")
        println("  bytes saved      : $savedBytes ($savedPct %)")
        println("=========================")

        assertTrue(savedPct >= 20, "expected at least 20 % savings, got $savedPct %")
    }

    @Test
    fun `toggling the filter via REST endpoint takes effect immediately`() {
        configPort.setEnabled(false)

        mockMvc
            .put("/api/text-filter/config") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"enabled": true}"""
            }.andExpect { status { isOk() } }

        assertEquals(true, configPort.enabled)

        mockMvc
            .put("/api/text-filter/config") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"enabled": false}"""
            }.andExpect { status { isOk() } }

        assertEquals(false, configPort.enabled)
    }

    private fun createAnalysisWithLargeK6Script(): LoadTestAnalysis {
        val repo =
            LocalRepoConfig(
                name = "filter-savings-repo-${System.nanoTime()}",
                path = Files.createTempDirectory("filter-savings-repo").toString(),
            )
        localRepoConfigRepo.saveLocalRepoConfig(repo)
        return analysisRepo.saveLoadTestAnalysis(
            LoadTestAnalysis(
                id = 0L,
                repoConfigId = repo.id,
                status = com.stacklens.domain.AnalysisStatus.PENDING,
                k6Script = "export const options = {vus: 1, duration: '1s'};\nexport default function() {}",
            ),
        )
    }

    private fun runFakeK6AndPersist(analysisId: Long) {
        k6Controller.startK6Script(
            analysisId = analysisId,
            bearerToken = null,
            environmentName = null,
            customScript = null,
        )
        val deadline = System.currentTimeMillis() + 5_000L
        var status = k6Controller.getK6Status(analysisId)
        while (status.running && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            status = k6Controller.getK6Status(analysisId)
        }
        assertEquals(false, status.running, "k6 should be reported as finished within 5s")
        assertEquals(0, status.exitCode)
    }

    private fun persistedK6OutputOrThrow(analysisId: Long): String {
        val raw = analysisRepo.findLoadTestAnalysisById(analysisId)?.k6Output
        check(!raw.isNullOrEmpty()) {
            "no k6 output persisted for analysisId=$analysisId"
        }
        return raw
    }
}
