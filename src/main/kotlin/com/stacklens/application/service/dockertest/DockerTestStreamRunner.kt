package com.stacklens.application.service.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestEventSink
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class DockerTestStreamRunner(
    private val dockerCommands: DockerCommandPort,
) {
    private val log = LoggerFactory.getLogger(DockerTestStreamRunner::class.java)

    fun runTwoPhase(
        phase1: List<String>,
        phase2: List<String>,
        workDir: String?,
        sink: DockerTestEventSink,
        preCleanup: List<String>?,
        job: DockerTestJob,
    ) {
        sink.sendEvent(EVENT_PING, DATA_CONNECTED)
        try {
            if (preCleanup != null) {
                val preExit = runPhase(0, "pre-cleanup", preCleanup, workDir, sink, job, record = false)
                if (preExit != 0) {
                    log.warn("Docker pre-cleanup exited with code {} — continuing with build/up", preExit)
                    emitLine("⚠ Pre-cleanup exited with code $preExit — continuing anyway", job, sink, record = true)
                }
            }
            val commands = listOf(phase1, phase2)
            for ((idx, command) in commands.withIndex()) {
                val phaseExit = runPhase(idx + 1, if (idx == 0) "build" else "up", command, workDir, sink, job, record = true)
                if (phaseExit != 0) {
                    log.warn("Docker phase {} exited with code {}", idx + 1, phaseExit)
                    emitLine(DockerTestJob.ERROR_MARKER, job, sink, record = true)
                    sink.complete()
                    job.markFailed()
                    return
                }
            }
            emitLine(DockerTestJob.DONE_MARKER, job, sink, record = true)
            job.markDone()
            sink.complete()
        } catch (e: Exception) {
            log.error("Docker command failed: {}", e.message, e)
            emitLine("Error: ${e.message}", job, sink, record = true)
            emitLine(DockerTestJob.ERROR_MARKER, job, sink, record = true)
            sink.completeWithError(e)
            job.markFailed()
        }
    }

    fun runSinglePhase(
        command: List<String>,
        workDir: String?,
        sink: DockerTestEventSink,
    ) {
        sink.sendEvent(EVENT_PING, DATA_CONNECTED)
        try {
            log.info("Docker command {} in {}", command, workDir)
            val exit = dockerCommands.runComposeStreaming(command, workDir) { line -> sink.sendLine(line) }
            if (exit == 0) {
                sink.sendLine(DockerTestJob.DONE_MARKER)
            } else {
                log.warn("Docker command exited with code {}", exit)
                sink.sendLine(DockerTestJob.ERROR_MARKER)
            }
            sink.complete()
        } catch (e: Exception) {
            log.error("Docker command failed: {}", e.message, e)
            sink.sendLine("Error: ${e.message}")
            sink.sendLine(DockerTestJob.ERROR_MARKER)
            sink.completeWithError(e)
        }
    }

    fun sendComposeNotFound(
        sink: DockerTestEventSink,
        message: String,
    ) {
        runCatching {
            sink.sendLine(message)
            sink.sendLine(DockerTestJob.ERROR_MARKER)
            sink.complete()
        }
    }

    private fun runPhase(
        phaseIndex: Int,
        label: String,
        command: List<String>,
        workDir: String?,
        sink: DockerTestEventSink,
        job: DockerTestJob,
        record: Boolean,
    ): Int {
        log.info("Docker phase {} ({}) command {} in {}", phaseIndex, label, command, workDir)
        return dockerCommands.runComposeStreaming(command, workDir) { line -> emitLine(line, job, sink, record) }
    }

    private fun emitLine(
        line: String,
        job: DockerTestJob,
        sink: DockerTestEventSink,
        record: Boolean,
    ) {
        if (record) {
            job.appendOutput(line)
        }
        sink.sendLine(line)
    }

    private companion object {
        const val EVENT_PING: String = "ping"
        const val DATA_CONNECTED: String = "connected"
    }
}
