package com.stacklens.application.service.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestEventSink
import com.stacklens.application.port.inbound.dockertest.DockerTestInfoView
import com.stacklens.application.port.inbound.dockertest.DockerTestJobClearedView
import com.stacklens.application.port.inbound.dockertest.DockerTestRestartView
import com.stacklens.application.port.inbound.dockertest.DockerTestStateView
import com.stacklens.application.port.inbound.dockertest.DockerTestStatusView
import com.stacklens.application.port.inbound.dockertest.DockerTestStreamRegistry
import com.stacklens.application.port.inbound.dockertest.DockerTestUseCase
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.async.DockerTestDispatchPort
import com.stacklens.application.port.out.docker.ComposeContainerStatePort
import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.docker.DockerTestEnvironmentPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.docker.ComposeContainerState
import org.slf4j.LoggerFactory
import java.util.UUID

@ApplicationService
class DockerTestService(
    private val availability: DockerAvailabilityPort,
    private val composeFiles: ComposeFileLocationPort,
    private val composeContainers: ComposeContainerStatePort,
    private val dockerTestEnvironment: DockerTestEnvironmentPort,
    private val dockerRestart: DockerRestartService,
    private val streamRunner: DockerTestStreamRunner,
    private val sleeper: SleeperPort,
    private val dispatcher: DockerTestDispatchPort,
    private val jobStore: DockerTestJobStore,
    private val streamRegistry: DockerTestStreamRegistry,
    private val configPort: StackLensConfigPort,
    private val i18n: I18nPort,
    private val clock: ClockPort,
) : DockerTestUseCase {
    private val log = LoggerFactory.getLogger(DockerTestService::class.java)

    override fun info(): DockerTestInfoView =
        try {
            val composeFile = composeFiles.resolveComposeFile()
            val available = availability.isDockerAvailable()
            DockerTestInfoView(
                composeFile = composeFile ?: "",
                found = composeFile != null,
                dockerAvailable = available,
                dockerDiagnostic = if (available) "" else dockerRestart.availabilityDiagnostic(),
            )
        } catch (e: Exception) {
            log.error("docker-test info failed: {}", e.message, e)
            DockerTestInfoView(
                composeFile = "",
                found = false,
                dockerAvailable = false,
                dockerDiagnostic = "info failed: ${e.message ?: e.javaClass.simpleName}",
            )
        }

    override fun status(): DockerTestStatusView {
        val composeFile = composeFiles.resolveComposeFile()
        val available = availability.isDockerAvailable()
        val jobActive = streamRegistry.activeStreamCount() > 0
        val containers =
            if (composeFile != null && available) {
                runCatching { composeContainers.queryComposeContainers(composeFile, configPort.dockerComposeQueryTimeoutSeconds) }
                    .getOrDefault(ComposeContainerState.EMPTY)
            } else {
                ComposeContainerState.EMPTY
            }
        val phase =
            when {
                jobActive -> PHASE_RUNNING
                containers.anyExitedNonZero -> PHASE_FAILED
                containers.any -> PHASE_DONE
                else -> PHASE_IDLE
            }
        return DockerTestStatusView(
            phase = phase,
            jobActive = jobActive,
            available = available,
            containers = containers.total,
            running = containers.running,
        )
    }

    override fun clearCurrentJob(): DockerTestJobClearedView {
        jobStore.clear()
        log.info("docker-test: currentJob cleared via /clear")
        return DockerTestJobClearedView(message = CLEARED_MESSAGE)
    }

    override fun restartDocker(): DockerTestRestartView = dockerRestart.restartDocker()

    override fun state(): DockerTestStateView {
        val job = jobStore.current() ?: return DockerTestStateView(jobId = null, status = PHASE_IDLE, output = emptyList(), lineCount = 0)
        return DockerTestStateView(
            jobId = job.id,
            status = job.status,
            output = job.allOutput(),
            lineCount = job.lineCount(),
        )
    }

    override fun startCompose(sink: DockerTestEventSink) {
        val job = DockerTestJob(id = UUID.randomUUID().toString(), startedAt = clock.epochMillis())
        jobStore.replace(job)
        val plan = dockerTestEnvironment.composePlan()
        dispatcher.submitStream {
            if (plan == null) {
                streamRunner.sendComposeNotFound(sink, i18n.t(KEY_COMPOSE_NOT_FOUND))
                job.markFailed()
                return@submitStream
            }
            streamRunner.runTwoPhase(
                phase1 = plan.build,
                phase2 = plan.up,
                workDir = plan.workDir,
                sink = sink,
                preCleanup = plan.prune,
                job = job,
            )
        }
    }

    override fun attachOutput(
        since: Int,
        sink: DockerTestEventSink,
    ) {
        val job = jobStore.current()
        dispatcher.submitStream { tailJobOutput(job, since, sink) }
    }

    override fun resetDocker(sink: DockerTestEventSink) {
        val job = DockerTestJob(id = UUID.randomUUID().toString(), startedAt = clock.epochMillis())
        jobStore.replace(job)
        val plan = dockerTestEnvironment.composePlan()
        dispatcher.submitStream {
            if (plan == null) {
                streamRunner.sendComposeNotFound(sink, i18n.t(KEY_COMPOSE_NOT_FOUND))
                job.markFailed()
                return@submitStream
            }
            streamRunner.runSinglePhase(
                command = plan.down,
                workDir = plan.workDir,
                sink = sink,
            )
            job.markDone()
        }
    }

    private fun tailJobOutput(
        job: DockerTestJob?,
        since: Int,
        sink: DockerTestEventSink,
    ) {
        if (job == null) {
            sink.sendEvent(EVENT_STATE, PHASE_IDLE)
            sink.complete()
            return
        }
        val batch = job.outputSince(since)
        batch.lines.forEach { sink.sendLine(it) }
        if (terminate(sink, job.status)) return
        try {
            var lastIndex = batch.nextIndex
            while (job.isRunning) {
                if (!sleeper.sleep(TAIL_POLL_INTERVAL_MS)) {
                    Thread.interrupted()
                    return
                }
                val nextBatch = job.outputSince(lastIndex)
                nextBatch.lines.forEach { sink.sendLine(it) }
                lastIndex = nextBatch.nextIndex
            }
            terminate(sink, job.status, completeAfter = true)
        } catch (e: Exception) {
            log.warn("attach tailing failed: {}", e.message)
        }
    }

    private fun terminate(
        sink: DockerTestEventSink,
        status: String,
        completeAfter: Boolean = false,
    ): Boolean {
        val marker = terminalMarker(status)
        if (marker != null) {
            sink.sendLine(marker)
        }
        if (completeAfter || marker != null) {
            sink.complete()
        }
        return marker != null
    }

    private fun terminalMarker(status: String): String? =
        when (status) {
            DockerTestJob.STATUS_DONE -> DockerTestJob.DONE_MARKER
            DockerTestJob.STATUS_FAILED -> DockerTestJob.ERROR_MARKER
            else -> null
        }

    companion object {
        const val CLEARED_MESSAGE: String = "current job cleared"
        const val PHASE_RUNNING: String = "running"
        const val PHASE_FAILED: String = "failed"
        const val PHASE_DONE: String = "done"
        const val PHASE_IDLE: String = "idle"
        const val EVENT_STATE: String = "state"
        const val KEY_COMPOSE_NOT_FOUND: String = "docker_test.be_compose_not_found"
        private const val TAIL_POLL_INTERVAL_MS: Long = 500L
    }
}
