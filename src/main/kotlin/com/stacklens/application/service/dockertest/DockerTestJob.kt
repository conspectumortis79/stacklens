package com.stacklens.application.service.dockertest

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class DockerTestJob(
    val id: String,
    val startedAt: Long,
) {
    private val log = LoggerFactory.getLogger(DockerTestJob::class.java)

    private val _status = AtomicReference(STATUS_RUNNING)
    val status: String get() = _status.get()

    private val lines = CopyOnWriteArrayList<String>()
    val isRunning: Boolean get() = _status.get() == STATUS_RUNNING

    fun appendOutput(line: String) {
        if (line == DONE_MARKER || line == ERROR_MARKER) {
            return
        }
        lines.add(line)
    }

    fun lineCount(): Int = lines.size

    fun allOutput(): List<String> = lines.toList()

    fun outputSince(sinceIndex: Int): OutputBatch {
        if (sinceIndex < 0) {
            return OutputBatch(emptyList(), 0)
        }
        if (sinceIndex >= lines.size) {
            return OutputBatch(emptyList(), lines.size)
        }
        return OutputBatch(lines.subList(sinceIndex, lines.size).toList(), lines.size)
    }

    fun markDone() {
        if (_status.compareAndSet(STATUS_RUNNING, STATUS_DONE)) {
            log.info("DockerTestJob {} marked DONE", id)
        }
    }

    fun markFailed() {
        if (_status.compareAndSet(STATUS_RUNNING, STATUS_FAILED)) {
            log.info("DockerTestJob {} marked FAILED", id)
        }
    }

    data class OutputBatch(
        val lines: List<String>,
        val nextIndex: Int,
    )

    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        const val DONE_MARKER = "__DONE__"
        const val ERROR_MARKER = "__ERROR__"
    }
}
