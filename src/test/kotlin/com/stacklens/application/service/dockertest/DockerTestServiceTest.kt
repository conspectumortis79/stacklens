package com.stacklens.application.service.dockertest

import com.stacklens.adapter.out.docker.TESTDOCKER_PROJECT_NAME
import com.stacklens.application.port.inbound.dockertest.DockerTestEventSink
import com.stacklens.application.port.inbound.dockertest.DockerTestStreamHandle
import com.stacklens.application.port.inbound.dockertest.DockerTestStreamRegistry
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.async.DockerTestDispatchPort
import com.stacklens.application.port.out.dockertest.DockerOsOperationsPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.domain.docker.ComposeContainerEntry
import com.stacklens.domain.docker.ComposeContainerState
import com.stacklens.domain.docker.DockerStatus
import com.stacklens.testsupport.DockerPortsDouble
import com.stacklens.testsupport.FakeDockerPorts
import com.stacklens.testsupport.FakePlatformPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class DockerTestServiceTest {
    private val composeFile = File("/tmp/stacklens-project/docker-compose.yml")

    private val management = FakeDockerPorts(composeFile = composeFile.absolutePath)

    private val composePort: DockerPortsDouble = mock()

    private val streamRunner = DockerTestStreamRunner(composePort)

    private val sleeper = ToggleSleeper()

    private val streams = StreamRegistryStub()

    private val configPort: StackLensConfigPort =
        mock {
            on { dockerComposeQueryTimeoutSeconds } doReturn 5L
        }

    private val runImmediately =
        object : DockerTestDispatchPort {
            override fun submitStream(task: () -> Unit) {
                task()
            }
        }

    private fun service(
        platform: PlatformPort = FakePlatformPort(OsType.LINUX),
        osOperations: DockerOsOperationsPort = mock(),
        i18n: I18nPort =
            com.stacklens.application.service
                .FakeI18n(),
        jobStore: DockerTestJobStore = DockerTestJobStore(),
        runner: DockerTestStreamRunner = streamRunner,
    ): DockerTestService =
        DockerTestService(
            availability = management,
            composeFiles = management,
            composeContainers = management,
            dockerTestEnvironment =
                com.stacklens.adapter.out.docker
                    .DockerTestEnvironmentAdapter(management, management),
            dockerRestart = DockerRestartService(platform, osOperations, management, i18n),
            streamRunner = runner,
            sleeper = sleeper,
            dispatcher = runImmediately,
            jobStore = jobStore,
            streamRegistry = streams,
            configPort = configPort,
            i18n = i18n,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun streamingExits(exitCode: Int = 0) {
        whenever(composePort.runComposeStreaming(any(), anyOrNull(), any())).thenAnswer { invocation ->
            val onLine = invocation.getArgument<Function1<String, Unit>>(2)
            onLine("output-line")
            exitCode
        }
    }

    @Test
    fun `info reports the resolved compose file and availability`() {
        val view = service().info()

        assertEquals(composeFile.absolutePath, view.composeFile)
        assertTrue(view.found)
        assertTrue(view.dockerAvailable)
        assertEquals("", view.dockerDiagnostic)
    }

    @Test
    fun `info keeps the availability diagnostic when docker cannot be reached`() {
        management.available = false
        management.status = DockerStatus(false, "no socket")

        val view = service().info()

        assertEquals("no socket", view.dockerDiagnostic)
    }

    @Test
    fun `info falls back to the exception type when no message is set`() {
        management.composeFileFailure = NullPointerException()

        val view = service().info()

        assertFalse(view.found)
        assertEquals("info failed: NullPointerException", view.dockerDiagnostic)
    }

    @Test
    fun `an active stream wins over the container state in status`() {
        management.containerState = ComposeContainerState(listOf(entry(RUNNING_STATE)))
        streams.open()
        streams.open()

        val view = service().status()

        assertEquals(DockerTestService.PHASE_RUNNING, view.phase)
        assertTrue(view.jobActive)
        assertEquals(1, view.containers)
    }

    @Test
    fun `a container that exited non zero reports the failed phase`() {
        management.containerState = ComposeContainerState(listOf(entry(EXITED_STATE, 1)))

        assertEquals(DockerTestService.PHASE_FAILED, service().status().phase)
    }

    @Test
    fun `running containers report the done phase`() {
        management.containerState = ComposeContainerState(listOf(entry(RUNNING_STATE)))

        assertEquals(DockerTestService.PHASE_DONE, service().status().phase)
    }

    @Test
    fun `an unavailable docker daemon skips the container query`() {
        management.available = false

        val view = service().status()

        assertEquals(DockerTestService.PHASE_IDLE, view.phase)
        assertEquals(0, view.containers)
    }

    @Test
    fun `clearing announces the removal and forgets the job`() {
        val store = DockerTestJobStore().apply { replace(DockerTestJob(id = "test-job", startedAt = 0L)) }

        val view = service(jobStore = store).clearCurrentJob()

        assertEquals(DockerTestService.CLEARED_MESSAGE, view.message)
        assertNull(store.current())
    }

    @Test
    fun `restart succeeds when the host reports a restart even without availability`() {
        val osOperations: DockerOsOperationsPort =
            mock {
                on { restartDockerOnLinux(any()) } doReturn true
            }

        val view = service(osOperations = osOperations).restartDocker()

        assertTrue(view.ok)
        assertTrue(view.details.contains("os=linux"))
    }

    @Test
    fun `restart appends the translated failure with the diagnostic`() {
        management.available = false
        management.status = DockerStatus(false, "socket down")
        val i18n: I18nPort =
            mock {
                on { t(DockerRestartService.KEY_RESTART_FAILED) } doReturn "restart failed"
            }

        val view = service(i18n = i18n).restartDocker()

        assertFalse(view.ok)
        assertEquals("restart failed: socket down", view.message)
    }

    @Test
    fun `a macos host is restarted through the macos strategy`() {
        val osOperations: DockerOsOperationsPort =
            mock {
                on { restartDockerOnMac(any()) } doReturn true
            }

        val view = service(platform = FakePlatformPort(OsType.MACOS), osOperations = osOperations).restartDocker()

        assertTrue(view.ok)
    }

    @Test
    fun `state exposes the running job with its output window`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L).apply { appendOutput("building") }
        store.replace(job)

        val view = service(jobStore = store).state()

        assertEquals(job.id, view.jobId)
        assertEquals(DockerTestJob.STATUS_RUNNING, view.status)
        assertEquals(listOf("building"), view.output)
        assertEquals(1, view.lineCount)
    }

    @Test
    fun `state falls back to an idle payload without a job`() {
        val view = service().state()

        assertNull(view.jobId)
        assertEquals(DockerTestService.PHASE_IDLE, view.status)
        assertTrue(view.output.isEmpty())
    }

    @Test
    fun `start runs pre-cleanup then build then up and closes the job`() {
        streamingExits()
        val store = DockerTestJobStore()
        val sink = RecordingSink()

        service(jobStore = store).startCompose(sink)

        val commands = capturedStreamingCommands(count = 3)
        assertEquals(listOf("docker", "container", "prune", "-f"), commands[0])
        assertEquals(
            listOf("docker", "compose", "-p", TESTDOCKER_PROJECT_NAME, "-f", composeFile.absolutePath, "build"),
            commands[1],
        )
        assertEquals(
            listOf(
                "docker",
                "compose",
                "-p",
                TESTDOCKER_PROJECT_NAME,
                "-f",
                composeFile.absolutePath,
                "up",
                "-d",
                "--force-recreate",
                "--remove-orphans",
            ),
            commands[2],
        )
        assertEquals(DockerTestJob.STATUS_DONE, store.current()?.status)
        assertTrue(sink.lines.contains("output-line"))
        assertEquals(DockerTestJob.DONE_MARKER, sink.lines.last())
        assertEquals(1, sink.completed)
    }

    @Test
    fun `start marks the job failed when a phase exits non zero`() {
        streamingExits(exitCode = 3)
        val store = DockerTestJobStore()
        val sink = RecordingSink()

        service(jobStore = store).startCompose(sink)

        assertEquals(DockerTestJob.STATUS_FAILED, store.current()?.status)
        assertEquals(DockerTestJob.ERROR_MARKER, sink.lines.last())
    }

    @Test
    fun `start announces the missing compose file instead of building`() {
        management.composeFile = null
        val sink = RecordingSink()

        service().startCompose(sink)

        assertEquals(1, sink.completed)
        assertEquals(DockerTestJob.ERROR_MARKER, sink.lines.last())
        verify(composePort, times(0)).runComposeStreaming(any(), anyOrNull(), any())
    }

    @Test
    fun `reset runs the down command`() {
        streamingExits()
        val sink = RecordingSink()

        service().resetDocker(sink)

        val command = capturedStreamingCommands(count = 1).single()
        assertEquals(
            listOf(
                "docker",
                "compose",
                "-p",
                TESTDOCKER_PROJECT_NAME,
                "-f",
                composeFile.absolutePath,
                "down",
                "--volumes",
                "--rmi",
                "all",
            ),
            command,
        )
    }

    @Test
    fun `attach reports an idle stream when no job is registered`() {
        val sink = RecordingSink()

        service().attachOutput(0, sink)

        assertTrue(sink.events.any { it == DockerTestService.EVENT_STATE to DockerTestService.PHASE_IDLE })
        assertEquals(1, sink.completed)
    }

    @Test
    fun `attach replays buffered output and closes a finished job with the done marker`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L).apply { appendOutput("line-one") }
        job.markDone()
        store.replace(job)
        val sink = RecordingSink()

        service(jobStore = store).attachOutput(0, sink)

        assertEquals(listOf("line-one", DockerTestJob.DONE_MARKER), sink.lines)
        assertEquals(1, sink.completed)
    }

    @Test
    fun `attach tails a running job until it finishes`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        store.replace(job)
        sleeper.onSecondWait = { job.markDone() }
        val sink = RecordingSink()

        service(jobStore = store).attachOutput(0, sink)

        assertTrue(sink.lines.contains(DockerTestJob.DONE_MARKER))
        assertEquals(1, sink.completed)
    }

    @Test
    fun `attach stops tailing when the sleeper reports an interruption`() {
        val store = DockerTestJobStore()
        store.replace(DockerTestJob(id = "test-job", startedAt = 0L))
        sleeper.alwaysWakes = false
        val sink = RecordingSink()

        service(jobStore = store).attachOutput(0, sink)

        assertEquals(0, sink.completed)
        assertTrue(sink.lines.isEmpty())
    }

    private fun capturedStreamingCommands(count: Int): List<List<String>> {
        val captured = argumentCaptor<List<String>>()

        verify(composePort, times(count)).runComposeStreaming(captured.capture(), anyOrNull(), any())
        return captured.allValues
    }

    private fun entry(
        state: String,
        exitCode: Int = 0,
    ): ComposeContainerEntry = ComposeContainerEntry(name = "demo", state = state, exitCode = exitCode)

    private companion object {
        private const val RUNNING_STATE = "running"
        private const val EXITED_STATE = "exited"
    }
}

private class RecordingSink : DockerTestEventSink {
    val lines: MutableList<String> = mutableListOf()

    val events: MutableList<Pair<String, String>> = mutableListOf()

    var completed = 0

    var error: Throwable? = null

    override fun sendLine(line: String) {
        lines += line
    }

    override fun sendEvent(
        name: String,
        data: String,
    ) {
        events += name to data
    }

    override fun complete() {
        completed++
    }

    override fun completeWithError(error: Throwable) {
        this.error = error
    }
}

private class ToggleSleeper : SleeperPort {
    var alwaysWakes: Boolean = true

    var onSecondWait: (() -> Unit)? = null

    private var waits = 0

    override fun sleep(millis: Long): Boolean {
        if (!alwaysWakes) return false
        waits++
        if (waits == 2) onSecondWait?.invoke()
        return true
    }
}

private class StreamRegistryStub : DockerTestStreamRegistry {
    private var active = 0

    override fun open(): DockerTestStreamHandle {
        active++
        return object : DockerTestStreamHandle {
            override fun release() {
                active--
            }
        }
    }

    override fun activeStreamCount(): Int = active
}

class DockerTestServiceBranchTest {
    private val composeFile = File("/tmp/stacklens-branch/docker-compose.yml")

    private val management = FakeDockerPorts(composeFile = composeFile.absolutePath)

    private val composePort: DockerPortsDouble = mock()

    private val streamRunner = DockerTestStreamRunner(composePort)

    private val registry = RecordingRegistry()

    private val configPort: StackLensConfigPort =
        mock {
            on { dockerComposeQueryTimeoutSeconds } doReturn 5L
        }

    private val dispatcher =
        object : DockerTestDispatchPort {
            override fun submitStream(task: () -> Unit) {
                task()
            }
        }

    private fun service(
        sleeper: SleeperPort = SleeperPort { true },
        runner: DockerTestStreamRunner = streamRunner,
        osOperations: DockerOsOperationsPort = mock(),
        store: DockerTestJobStore = DockerTestJobStore(),
    ) = DockerTestService(
        availability = management,
        composeFiles = management,
        composeContainers = management,
        dockerTestEnvironment =
            com.stacklens.adapter.out.docker
                .DockerTestEnvironmentAdapter(management, management),
        dockerRestart =
            DockerRestartService(
                FakePlatformPort(OsType.LINUX),
                osOperations,
                management,
                com.stacklens.application.service
                    .FakeI18n(),
            ),
        streamRunner = runner,
        sleeper = sleeper,
        dispatcher = dispatcher,
        jobStore = store,
        streamRegistry = registry,
        configPort = configPort,
        i18n =
            com.stacklens.application.service
                .FakeI18n(),
        clock = com.stacklens.adapter.out.time.SystemClockPort,
    )

    private fun streaming(exitCode: Int = 0) {
        whenever(composePort.runComposeStreaming(any(), anyOrNull(), any())).thenAnswer { invocation ->
            invocation.getArgument<Function1<String, Unit>>(2).invoke("out")
            exitCode
        }
    }

    @Test
    fun `info reports a missing compose file as not found`() {
        management.composeFile = null

        val view = service().info()

        assertEquals("", view.composeFile)
        assertFalse(view.found)
    }

    @Test
    fun `an info failure without a message falls back to the exception type`() {
        management.availabilityFailure = NullPointerException()

        assertEquals("info failed: NullPointerException", service().info().dockerDiagnostic)
    }

    @Test
    fun `an info failure keeps the exception message`() {
        management.availabilityFailure = RuntimeException("probe exploded")

        assertEquals("info failed: probe exploded", service().info().dockerDiagnostic)
    }

    @Test
    fun `a failing container query degrades to the empty state`() {
        management.containerFailure = RuntimeException("docker ps failed")

        val view = service().status()

        assertEquals(DockerTestService.PHASE_IDLE, view.phase)
        assertEquals(0, view.containers)
    }

    @Test
    fun `a missing compose file skips the container query even when docker is up`() {
        management.composeFile = null

        val view = service().status()

        assertEquals(0, view.containers)
        assertEquals(0, management.containersQueried.size)
    }

    @Test
    fun `the availability diagnostic is dropped when the probe itself fails`() {
        management.available = false
        management.statusFailure = RuntimeException("probe down")

        assertEquals("", service().info().dockerDiagnostic)
    }

    @Test
    fun `a successful restart wins even without the host reporting success`() {
        val osOperations: DockerOsOperationsPort =
            mock {
                on { restartDockerOnLinux(any()) } doReturn false
            }
        management.available = true

        val view = service(osOperations = osOperations).restartDocker()

        assertTrue(view.ok)
        assertEquals("docker_test.restart_success", view.message)
    }

    @Test
    fun `a failed restart without a diagnostic keeps the plain failure message`() {
        val osOperations: DockerOsOperationsPort =
            mock {
                on { restartDockerOnLinux(any()) } doReturn false
            }
        management.available = false
        management.status = DockerStatus(false, "")

        val view = service(osOperations = osOperations).restartDocker()

        assertFalse(view.ok)
        assertEquals("docker_test.restart_failed", view.message)
    }

    @Test
    fun `the macos host restart selects the darwin strategy`() {
        val osOperations: DockerOsOperationsPort =
            mock {
                on { restartDockerOnMac(any()) } doReturn true
            }

        val view =
            DockerTestService(
                availability = management,
                composeFiles = management,
                composeContainers = management,
                dockerTestEnvironment =
                    com.stacklens.adapter.out.docker
                        .DockerTestEnvironmentAdapter(management, management),
                dockerRestart =
                    DockerRestartService(
                        FakePlatformPort(OsType.MACOS),
                        osOperations,
                        management,
                        com.stacklens.application.service
                            .FakeI18n(),
                    ),
                streamRunner = streamRunner,
                sleeper = SleeperPort { true },
                dispatcher = dispatcher,
                jobStore = DockerTestJobStore(),
                streamRegistry = registry,
                configPort = configPort,
                i18n =
                    com.stacklens.application.service
                        .FakeI18n(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            ).restartDocker()

        assertTrue(view.ok)
        verify(osOperations).restartDockerOnMac(any())
    }

    @Test
    fun `reset announces a missing compose file and fails the job`() {
        management.composeFile = null
        val store = DockerTestJobStore()
        val sink = RecordingSink()

        service(store = store).resetDocker(sink)

        assertEquals(DockerTestJob.STATUS_FAILED, store.current()?.status)
        assertEquals(
            listOf(DockerTestService.KEY_COMPOSE_NOT_FOUND, DockerTestJob.ERROR_MARKER),
            sink.lines,
        )
    }

    @Test
    fun `a service answers the translated compose-missing text`() {
        management.composeFile = null
        val sink = RecordingSink()

        service().startCompose(sink)

        assertTrue(sink.lines.contains(DockerTestService.KEY_COMPOSE_NOT_FOUND))
    }

    @Test
    fun `attach keeps tailing until the job finishes and reports the done marker once`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        store.replace(job)
        var waits = 0
        val sleeper =
            SleeperPort {
                waits++
                when (waits) {
                    1 -> job.appendOutput("line-a")
                    2 -> job.appendOutput("line-b")
                    else -> job.markDone()
                }
                true
            }
        val sink = RecordingSink()

        service(sleeper = sleeper, store = store).attachOutput(0, sink)

        assertEquals(listOf("line-a", "line-b", DockerTestJob.DONE_MARKER), sink.lines)
    }

    @Test
    fun `attach ends a failed job with the error marker and completes the stream`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        job.appendOutput("boom")
        job.markFailed()
        store.replace(job)
        val sink = RecordingSink()

        service(store = store).attachOutput(0, sink)

        assertEquals(listOf("boom", DockerTestJob.ERROR_MARKER), sink.lines)
        assertEquals(1, sink.completed)
    }

    @Test
    fun `attach stops when the tailing loop is interrupted`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        store.replace(job)
        val sleeper =
            SleeperPort {
                Thread.currentThread().interrupt()
                false
            }

        service(sleeper = sleeper, store = store).attachOutput(0, RecordingSink())

        Thread.interrupted()
    }

    @Test
    fun `a sink that throws while tailing is reported and ends the tailing`() {
        val store = DockerTestJobStore()
        val job = DockerTestJob(id = "test-job", startedAt = 0L)
        store.replace(job)
        val sleeper =
            SleeperPort {
                job.appendOutput("more")
                true
            }
        val throwing =
            object : DockerTestEventSink {
                private var sends = 0

                override fun sendLine(line: String) {
                    sends++
                    if (sends > 2) throw IllegalStateException("pipe closed")
                }

                override fun sendEvent(
                    name: String,
                    data: String,
                ) = Unit

                override fun complete() = Unit

                override fun completeWithError(error: Throwable) = Unit
            }

        service(sleeper = sleeper, store = store).attachOutput(0, throwing)
    }

    private class RecordingRegistry : DockerTestStreamRegistry {
        var opened = 0

        override fun open(): DockerTestStreamHandle {
            opened++
            return object : DockerTestStreamHandle {
                override fun release() = Unit
            }
        }

        override fun activeStreamCount(): Int = opened
    }
}
