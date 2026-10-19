package com.stacklens.adapter.inbound.rest.pipeline

import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.domain.util.quietly
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

@Component
class
PipelineSsePublisher(
    private val pipelineService: CopilotAgentPipelineUseCase,
) {
    private val log = LoggerFactory.getLogger(PipelineSsePublisher::class.java)

    val sseExecutor: ExecutorService =
        Executors.newFixedThreadPool(
            50,
            object : ThreadFactory {
                private val counter = AtomicInteger(0)

                override fun newThread(r: Runnable) = Thread(r, "ap-sse-${counter.incrementAndGet()}").also { it.isDaemon = true }
            },
        )

    fun stream(id: Long): SseEmitter {
        val run =
            pipelineService.get(id)
                ?: return SseEmitter(0L).also { it.completeWithError(IllegalArgumentException("Not found")) }

        val emitter = SseEmitter(2 * 60 * 60 * 1000L)

        val future =
            sseExecutor.submit(
                PipelineStreamRunnable(run, emitter),
            )

        emitter.onCompletion(CancelFutureOnCompletion(future))
        emitter.onTimeout(CancelFutureOnTimeout(future))
        emitter.onError(CancelFutureOnError(future))

        return emitter
    }

    fun chat(
        id: Long,
        body: Map<String, String>,
    ): SseEmitter {
        val message = body["message"]?.trim() ?: ""
        if (message.isBlank()) {
            return SseEmitter(0L).also { it.completeWithError(IllegalArgumentException("message required")) }
        }
        if (message.length > 10_000) {
            return SseEmitter(0L).also { it.completeWithError(IllegalArgumentException("message too long (max 10 000 chars)")) }
        }
        pipelineService.get(id) ?: return SseEmitter(0L).also {
            it.completeWithError(IllegalArgumentException("Pipeline #$id not found"))
        }

        val emitter = SseEmitter(300_000L)

        val future =
            sseExecutor.submit(
                object : Runnable {
                    override fun run() {
                        try {
                            var lastSent = ""
                            pipelineService.chat(id, message) { partial ->
                                if (Thread.interrupted()) throw InterruptedException("pipeline chat stream cancelled")
                                val delta = if (partial.length > lastSent.length) partial.substring(lastSent.length) else ""
                                if (delta.isNotEmpty()) {
                                    emitter.send(SseEmitter.event().name("chunk").data(delta))
                                    lastSent = partial
                                }
                            }
                            emitter.send(SseEmitter.event().name("done").data(""))
                            emitter.complete()
                        } catch (_: InterruptedException) {
                            quietly { emitter.complete() }
                        } catch (e: Exception) {
                            log.warn("[PipelineSsePublisher] chat stream {} failed: {}", id, e.message, e)
                            quietly { emitter.completeWithError(e) }
                        }
                    }
                },
            )

        emitter.onCompletion(CancelFutureOnCompletion(future))
        emitter.onTimeout(CancelFutureOnTimeout(future))
        emitter.onError(CancelFutureOnError(future))

        return emitter
    }
}

private object StagePresentation {
    val labels: Map<String, String> =
        mapOf(
            "lena" to "⓪ Lena — Jira-Ticket Analyse",
            "nora" to "① Nora — Requirements",
            "cole" to "② Cole — Implementation Plan",
            "ivan" to "Ⓘ Ivan — Git & Setup",
            "max" to "③ Max — PHP Architecture",
            "finn" to "④ Finn — PHP Code",
            "faye" to "⑤ Faye — Frontend",
            "vera" to "⑥ Vera — Infrastructure",
            "tess" to "⑦ Tess — Tests",
            "syd" to "⑧ Syd — Systemtests",
            "quinn" to "⑨ Quinn — Quality & Coverage",
            "sage" to "⑩ Sage — Security Review",
            "rex" to "⑪ Rex — Build & Fix",
        )

    val icons: Map<String, String> =
        mapOf(
            "lena" to "bi-ticket-detailed",
            "nora" to "bi-person-badge",
            "cole" to "bi-diagram-3",
            "ivan" to "bi-git",
            "max" to "bi-bezier2",
            "finn" to "bi-code-slash",
            "faye" to "bi-palette",
            "vera" to "bi-server",
            "tess" to "bi-clipboard2-check",
            "syd" to "bi-clipboard2-pulse",
            "quinn" to "bi-shield-check",
            "sage" to "bi-shield-lock",
            "rex" to "bi-terminal",
        )
}

internal fun stageLabelOf(name: String): String = StagePresentation.labels.getValue(name)

internal fun stageIconOf(name: String): String = StagePresentation.icons.getValue(name)

internal fun AgentPipelineRun.toStatusMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "status" to status.name,
        "jiraMode" to (jiraTicketData != null),
        "currentStage" to currentStage,
        "errorMessage" to errorMessage,
        "repoPath" to repoPath,
        "featureSlug" to featureSlug,
        "gitBranch" to gitBranch,
        "gitLog" to gitLog,
        "createdAt" to createdAt,
        "stages" to
            stages.map { s ->
                mapOf(
                    "name" to s.name,
                    "label" to stageLabelOf(s.name),
                    "icon" to stageIconOf(s.name),
                    "status" to s.status.name,
                    "output" to s.output,
                )
            },
    )

internal class PipelineStreamRunnable(
    private val run: AgentPipelineRun,
    private val emitter: org.springframework.web.servlet.mvc.method.annotation.SseEmitter,
) : Runnable {
    private val log = LoggerFactory.getLogger(PipelineStreamRunnable::class.java)

    override fun run() {
        var lastHash = Int.MIN_VALUE
        try {
            while (true) {
                val map = run.toStatusMap()
                val hash =
                    run.stages.fold(run.status.ordinal) { acc, s ->
                        acc * 31 + s.status.ordinal + s.output.length
                    }
                if (hash != lastHash) {
                    lastHash = hash
                    emitter.send(
                        org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                            .event()
                            .name("update")
                            .data(map),
                    )
                }
                if (run.status == PipelineStatus.DONE || run.status == PipelineStatus.ERROR) {
                    emitter.send(
                        org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                            .event()
                            .name("update")
                            .data(run.toStatusMap()),
                    )
                    emitter.complete()
                    break
                }
                Thread.sleep(POLL_INTERVAL_MILLIS)
            }
        } catch (_: InterruptedException) {
            quietly { emitter.complete() }
        } catch (e: Exception) {
            log.warn("[PipelineSsePublisher] pipeline stream failed: {}", e.message, e)
            quietly { emitter.completeWithError(e) }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 250L
    }
}

internal class CancelFutureOnCompletion(
    private val future: java.util.concurrent.Future<*>,
) : Runnable {
    override fun run() {
        future.cancel(true)
    }
}

internal class CancelFutureOnTimeout(
    private val future: java.util.concurrent.Future<*>,
) : Runnable {
    override fun run() {
        future.cancel(true)
    }
}

internal class CancelFutureOnError(
    private val future: java.util.concurrent.Future<*>,
) : java.util.function.Consumer<Throwable> {
    override fun accept(t: Throwable) {
        future.cancel(true)
    }
}
