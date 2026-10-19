package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.dockertest.SseDockerTestEventSink
import com.stacklens.adapter.inbound.rest.dockertest.SseDockerTestStreamTransport
import com.stacklens.application.port.inbound.dockertest.DockerTestUseCase
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/docker-test")
open class DockerTestApiController(
    private val dockerTest: DockerTestUseCase,
    private val streams: SseDockerTestStreamTransport,
) {
    @GetMapping("/info")
    fun info(): ResponseEntity<Map<String, String>> {
        val view = dockerTest.info()
        return ResponseEntity.ok(
            mapOf(
                "composeFile" to view.composeFile,
                "found" to view.found.toString(),
                "dockerAvailable" to view.dockerAvailable.toString(),
                "dockerDiagnostic" to view.dockerDiagnostic,
            ),
        )
    }

    @GetMapping("/status")
    fun status(): ResponseEntity<Map<String, String>> {
        val view = dockerTest.status()
        return ResponseEntity.ok(
            mapOf(
                "phase" to view.phase,
                "jobActive" to view.jobActive.toString(),
                "available" to view.available.toString(),
                "containers" to view.containers.toString(),
                "running" to view.running.toString(),
            ),
        )
    }

    @PostMapping("/clear")
    fun clearCurrentJob(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(
            mapOf(
                "status" to "ok",
                "message" to dockerTest.clearCurrentJob().message,
            ),
        )

    @PostMapping("/restart-docker")
    fun restartDocker(): ResponseEntity<Map<String, String>> {
        val view = dockerTest.restartDocker()
        return ResponseEntity.ok(
            mapOf(
                "ok" to view.ok.toString(),
                "dockerAvailable" to view.dockerAvailable.toString(),
                "dockerDiagnostic" to view.dockerDiagnostic,
                "message" to view.message,
                "details" to view.details,
            ),
        )
    }

    @GetMapping("/state")
    fun state(): ResponseEntity<Map<String, Any?>> {
        val view = dockerTest.state()
        return ResponseEntity.ok(
            mapOf(
                "jobId" to view.jobId,
                "status" to view.status,
                "output" to view.output,
                "lineCount" to view.lineCount,
            ),
        )
    }

    @GetMapping("/start", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun startDockerCompose(): SseEmitter = stream { sink -> dockerTest.startCompose(sink) }

    @GetMapping("/attach", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun attach(
        @RequestParam(defaultValue = "0") since: Int,
    ): SseEmitter = stream { sink -> dockerTest.attachOutput(since, sink) }

    @GetMapping("/reset", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun resetDocker(): SseEmitter = stream { sink -> dockerTest.resetDocker(sink) }

    private fun stream(start: (SseDockerTestEventSink) -> Unit): SseEmitter {
        val sink = streams.createStream()
        start(sink)
        return sink.emitter
    }
}
