package com.stacklens.application.service

import com.stacklens.adapter.out.docker.DockerTestEnvironmentAdapter
import com.stacklens.application.port.inbound.dockertest.DockerTestEventSink
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.async.DockerTestDispatchPort
import com.stacklens.application.port.out.docker.DemoEnvironmentOutcome
import com.stacklens.application.port.out.docker.DemoEnvironmentPort
import com.stacklens.application.port.out.dockertest.DockerOsOperationsPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.application.service.dockertest.DockerRestartService
import com.stacklens.application.service.dockertest.DockerTestJob
import com.stacklens.application.service.dockertest.DockerTestJobStore
import com.stacklens.application.service.dockertest.DockerTestService
import com.stacklens.application.service.dockertest.DockerTestStreamRegistryService
import com.stacklens.application.service.dockertest.DockerTestStreamRunner
import com.stacklens.testsupport.DockerPortsDouble
import com.stacklens.testsupport.FakeDockerPorts
import com.stacklens.testsupport.FakePlatformPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DockerEnvironmentStartupBranchTest {
    private class RecordingDemoEnvironment(
        var outcome: DemoEnvironmentOutcome? = DemoEnvironmentOutcome(0, "network created"),
        var failure: RuntimeException? = null,
    ) : DemoEnvironmentPort {
        val timeouts = mutableListOf<Long>()

        override fun bringUp(timeoutSeconds: Long): DemoEnvironmentOutcome? {
            timeouts += timeoutSeconds
            failure?.let { throw it }
            return outcome
        }

        override fun bringDown() = Unit

        override fun ensureMysqlSchema() = Unit
    }

    private val demoEnvironment = RecordingDemoEnvironment()

    private val configPort: StackLensConfigPort =
        mock {
            on { dockerComposeUpTimeoutSeconds } doReturn 120L
        }

    private fun service(prefs: Map<String, String>) =
        DockerEnvironmentStartupService(
            uiPreferences =
                object : UiPreferencesPort {
                    override fun getAll(): Map<String, String> = prefs

                    override fun replaceAll(preferences: Map<String, String>) = Unit
                },
            demoEnvironment = demoEnvironment,
            configPort = configPort,
            startupTasks = DockerEnvironmentStartupImmediateExecutor,
        )

    @Test
    fun `a failed compose run with blank output logs nothing extra`() {
        demoEnvironment.outcome = DemoEnvironmentOutcome(1, "   ")

        service(mapOf("dev_docker_auto_start" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    @Test
    fun `a successful compose run keeps the compact output path`() {
        demoEnvironment.outcome = DemoEnvironmentOutcome(0, "network created\n\ncontainer started")

        service(mapOf("dev_docker_test_env" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    @Test
    fun `a missing compose file is tolerated`() {
        demoEnvironment.outcome = null

        service(mapOf("dev_docker_auto_start" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    @Test
    fun `an incomplete environment reports the compose output`() {
        demoEnvironment.outcome = DemoEnvironmentOutcome(1, "\nfirst problem\n\nsecond problem\n")

        service(mapOf("dev_docker_auto_start" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    @Test
    fun `a failing compose run is caught`() {
        demoEnvironment.failure = RuntimeException("boom")

        service(mapOf("dev_docker_auto_start" to "true")).startDemoEnvironmentIfEnabledInBackground()

        assertEquals(listOf(120L), demoEnvironment.timeouts)
    }

    @Test
    fun `unsupported values for both flags leave docker untouched`() {
        service(mapOf("dev_docker_auto_start" to "yes", "dev_docker_test_env" to "0")).startDemoEnvironmentIfEnabledInBackground()

        assertTrue(demoEnvironment.timeouts.isEmpty())
    }
}

object DockerEnvironmentStartupImmediateExecutor : com.stacklens.application.port.out.async.StartupTaskExecutor {
    override fun submit(task: () -> Unit) {
        task()
    }
}

class DockerTestServiceI18nBranchTest {
    private class CollectingSink : DockerTestEventSink {
        val lines = mutableListOf<String>()

        var completed = 0

        override fun sendLine(line: String) {
            lines += line
        }

        override fun sendEvent(
            name: String,
            data: String,
        ) = Unit

        override fun complete() {
            completed++
        }

        override fun completeWithError(error: Throwable) = Unit
    }

    private val management = FakeDockerPorts(composeFile = "/tmp/docker-compose.yml")

    private val composePort: DockerPortsDouble = mock()

    private fun service(i18n: I18nPort) =
        DockerTestService(
            availability = management,
            composeFiles = management,
            composeContainers = management,
            dockerTestEnvironment = DockerTestEnvironmentAdapter(management, management),
            dockerRestart =
                DockerRestartService(
                    FakePlatformPort(OsType.LINUX),
                    mock<DockerOsOperationsPort>(),
                    management,
                    i18n,
                ),
            streamRunner = DockerTestStreamRunner(composePort),
            sleeper = SleeperPort { true },
            dispatcher =
                object : DockerTestDispatchPort {
                    override fun submitStream(task: () -> Unit) {
                        task()
                    }
                },
            jobStore = DockerTestJobStore(),
            streamRegistry = DockerTestStreamRegistryService(),
            configPort =
                mock<StackLensConfigPort> {
                    on { dockerComposeQueryTimeoutSeconds } doReturn 5L
                },
            i18n = i18n,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `the translated compose-missing notice is used when an i18n port is wired`() {
        management.composeFile = null
        val i18n: I18nPort = mock { on { t("docker_test.be_compose_not_found") } doReturn "keine compose datei" }
        val sink = CollectingSink()

        service(i18n).startCompose(sink)

        assertEquals(listOf("keine compose datei", DockerTestJob.ERROR_MARKER), sink.lines)
    }

    @Test
    fun `restart messages echo the key when the i18n port has no mapping`() {
        val view =
            service(
                com.stacklens.application.service
                    .FakeI18n(),
            ).restartDocker()

        assertEquals("docker_test.restart_success", view.message)
    }

    @Test
    fun `a job that is still running when the tail ends completes the stream without a marker`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        store.replace(job)
        var waits = 0
        val sleeper =
            SleeperPort {
                waits++
                when (waits) {
                    1 -> job.appendOutput("partial")
                    3 -> return@SleeperPort false
                }
                true
            }
        val sink = CollectingSink()

        serviceWith(store = store, sleeper = sleeper).attachOutput(0, sink)

        assertEquals(listOf("partial"), sink.lines)
        assertEquals(0, sink.completed)
    }

    private fun serviceWith(
        store: DockerTestJobStore,
        sleeper: SleeperPort,
    ): DockerTestService =
        DockerTestService(
            availability = management,
            composeFiles = management,
            composeContainers = management,
            dockerTestEnvironment = DockerTestEnvironmentAdapter(management, management),
            dockerRestart =
                DockerRestartService(
                    FakePlatformPort(OsType.LINUX),
                    mock<DockerOsOperationsPort>(),
                    management,
                    com.stacklens.application.service
                        .FakeI18n(),
                ),
            streamRunner = DockerTestStreamRunner(composePort),
            sleeper = sleeper,
            dispatcher =
                object : DockerTestDispatchPort {
                    override fun submitStream(task: () -> Unit) {
                        task()
                    }
                },
            jobStore = store,
            streamRegistry = DockerTestStreamRegistryService(),
            configPort =
                mock<StackLensConfigPort> {
                    on { dockerComposeQueryTimeoutSeconds } doReturn 5L
                },
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )
}
