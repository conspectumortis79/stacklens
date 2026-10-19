package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.MakeRunnerUseCase
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/make-runner")
open class MakeRunnerController(
    private val makeRunnerUseCase: MakeRunnerUseCase,
) {
    private val executor =
        Executors.newCachedThreadPool { r ->
            Thread(r, "make-runner-sse").apply { isDaemon = true }
        }

    data class RunRequest(
        val repoPath: String = "",
        val target: String = "test",
    )

    internal open fun waitForProcess(proc: Process): Boolean = true

    internal open fun createEmitter(): SseEmitter = SseEmitter(15 * 60 * 1000L)

    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun run(
        @RequestBody req: RunRequest,
    ): SseEmitter {
        val emitter = createEmitter()
        executor.submit {
            try {
                val target = req.target.trim().ifBlank { "test" }
                emitter.send(SseEmitter.event().name("line").data("▶ make $target  (cwd=${req.repoPath.trim()})"))
                emitter.send(SseEmitter.event().name("line").data(""))
                val result =
                    makeRunnerUseCase.run(req.repoPath, target) { line ->
                        emitter.send(SseEmitter.event().name("line").data(line))
                    }
                when {
                    result.errorMessage != null -> {
                        emitter.send(SseEmitter.event().name("line").data("✗ ${result.errorMessage}"))
                        emitter.send(SseEmitter.event().name("done").data("error"))
                    }
                    result.timedOut -> {
                        emitter.send(SseEmitter.event().name("line").data(""))
                        emitter.send(SseEmitter.event().name("line").data("✗ Timeout after 10 minutes — process aborted"))
                        emitter.send(SseEmitter.event().name("done").data("timeout"))
                    }
                    else -> {
                        val rc = result.exitCode
                        emitter.send(SseEmitter.event().name("line").data(""))
                        emitter.send(
                            SseEmitter.event().name("line").data(
                                if (rc == 0) "✓ make $target successful (exit 0)" else "✗ make $target failed (exit $rc)",
                            ),
                        )
                        emitter.send(SseEmitter.event().name("done").data(if (rc == 0) "ok" else "fail"))
                    }
                }
                emitter.complete()
            } catch (e: Exception) {
                try {
                    emitter.send(SseEmitter.event().name("line").data("✗ Error: ${e.message}"))
                    emitter.send(SseEmitter.event().name("done").data("error"))
                } catch (_: Exception) {
                }
                emitter.complete()
            }
        }
        return emitter
    }
}
