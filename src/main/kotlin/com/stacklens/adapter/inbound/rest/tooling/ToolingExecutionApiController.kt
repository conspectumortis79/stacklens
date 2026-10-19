package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.tooling.ToolingExecutionUseCase
import com.stacklens.domain.util.quietly
import jakarta.annotation.PreDestroy
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

@RestController
@RequestMapping("/api/tooling")
class ToolingExecutionApiController(
    private val toolingService: ToolingExecutionUseCase,
    private val i18n: I18nUseCase,
) {
    private val sseExecutor = Executors.newCachedThreadPool(ToolingSseThreadFactory())

    @PreDestroy
    internal fun shutdownSseExecutor() {
        sseExecutor.shutdown()
    }

    @PostMapping("/{id}/run")
    fun run(
        @PathVariable id: Long,
    ): ResponseEntity<*> =
        try {
            val output = toolingService.runTool(id)
            ResponseEntity.ok(mapOf("output" to output))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: ToolingApiSupport.tr(i18n, "common.be_unknown_error"))))
        }

    @GetMapping("/{id}/run-stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun runStream(
        @PathVariable id: Long,
    ): SseEmitter {
        val emitter = SseEmitter(300_000L)
        emitter.onCompletion {}
        emitter.onTimeout { emitter.complete() }
        emitter.onError { emitter.complete() }
        sseExecutor.submit {
            try {
                emitter.send(
                    SseEmitter
                        .event()
                        .name("start")
                        .data("")
                        .build(),
                )
                toolingService.runToolStreaming(id) { line ->
                    quietly {
                        emitter.send(
                            SseEmitter
                                .event()
                                .name("line")
                                .data(line)
                                .build(),
                        )
                    }
                }
                emitter.send(
                    SseEmitter
                        .event()
                        .name("done")
                        .data("")
                        .build(),
                )
                emitter.complete()
            } catch (e: Exception) {
                quietly {
                    emitter.send(
                        SseEmitter
                            .event()
                            .name("error")
                            .data(e.message ?: ToolingApiSupport.tr(i18n, "common.be_unknown_error"))
                            .build(),
                    )
                    emitter.complete()
                }
            }
        }
        return emitter
    }

    internal class ToolingSseThreadFactory : ThreadFactory {
        override fun newThread(r: Runnable): Thread = Thread(r, "tooling-sse").also { it.isDaemon = true }
    }
}
