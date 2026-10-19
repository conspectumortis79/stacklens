package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.code.CodeQaStreamingUseCase
import com.stacklens.domain.AnalysisStatus
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.util.DisconnectedClientHelper
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Component
class CodeQaSseStreamer(
    private val analysisService: CodeQaStreamingUseCase,
) {
    private val sseExecutor = Executors.newFixedThreadPool(20, CodeQaSseThreadFactory())

    @PreDestroy
    fun shutdown() {
        sseExecutor.shutdown()
    }

    fun openAnalysisStream(id: Long): SseEmitter {
        val analysis =
            analysisService.getAnalysis(id)
                ?: return SseEmitter(0L).also { it.completeWithError(IllegalArgumentException("Not found")) }

        if (analysis.status == AnalysisStatus.COMPLETED) {
            val emitter = SseEmitter(0L)
            runCatching { emitter.send(SseEmitter.event().name("chunk").data(analysis.resultMarkdown ?: "")) }
            runCatching { emitter.send(SseEmitter.event().name("done").data("")) }
            runCatching { emitter.complete() }
            return emitter
        }
        if (analysis.status == AnalysisStatus.FAILED) {
            val emitter = SseEmitter(0L)
            runCatching { emitter.send(SseEmitter.event().name("analysis-error").data(analysis.errorMessage ?: "")) }
            runCatching { emitter.complete() }
            return emitter
        }
        if (analysis.status == AnalysisStatus.CANCELLED) {
            val emitter = SseEmitter(0L)
            runCatching { emitter.send(SseEmitter.event().name("analysis-error").data("")) }
            runCatching { emitter.complete() }
            return emitter
        }

        val emitter = SseEmitter(15 * 60 * 1000L)
        return emitter.cancelling(sseExecutor.submit(SseStreamTask(id, emitter, analysisService)))
    }

    fun openIntegrationTestStream(id: Long): SseEmitter {
        val analysis =
            analysisService.getAnalysis(id)
                ?: return SseEmitter(0L).also { it.completeWithError(IllegalArgumentException("Not found")) }

        val itStatus = analysis.integrationTestStatus
        if (itStatus == AnalysisStatus.COMPLETED || itStatus == AnalysisStatus.FAILED || itStatus == AnalysisStatus.CANCELLED) {
            val emitter = SseEmitter(0L)
            val log = analysis.integrationTestLog.orEmpty()
            if (log.isNotBlank()) runCatching { emitter.send(SseEmitter.event().name("chunk").data(log)) }
            runCatching { emitter.send(SseEmitter.event().name("test-done").data(itStatus)) }
            runCatching { emitter.complete() }
            return emitter
        }

        val emitter = SseEmitter(30 * 60 * 1000L)
        return emitter.cancelling(sseExecutor.submit(IntegrationTestSseTask(id, emitter, analysisService)))
    }

    private fun SseEmitter.cancelling(future: Future<*>): SseEmitter =
        apply {
            val canceller = FutureCanceller(future)
            onCompletion(canceller)
            onTimeout(canceller)
            onError(canceller)
        }
}

internal class CodeQaSseThreadFactory : ThreadFactory {
    override fun newThread(r: Runnable): Thread = Thread(r, "mr-sse").also { it.isDaemon = true }
}

internal class SseStreamTask(
    private val id: Long,
    private val emitter: SseEmitter,
    private val analysisService: CodeQaStreamingUseCase,
    private val pollIntervalMs: Long = 500L,
) : Runnable {
    private val log = LoggerFactory.getLogger(SseStreamTask::class.java)

    override fun run() {
        val changeSignal = LinkedBlockingQueue<Unit>(1)
        val listener: () -> Unit = { changeSignal.offer(Unit) }
        val handle = analysisService.registerStreamingChangeListener(id, listener)
        try {
            var lastLen = 0
            while (true) {
                val partial = analysisService.getStreamingOutput(id)
                if (partial != null && partial.length > lastLen) {
                    emitter.send(SseEmitter.event().name("chunk").data(partial.substring(lastLen)))
                    lastLen = partial.length
                }
                val current = analysisService.getAnalysis(id)
                when (current?.status) {
                    AnalysisStatus.COMPLETED -> {
                        val full = current.resultMarkdown ?: ""
                        if (full.length > lastLen) {
                            emitter.send(SseEmitter.event().name("chunk").data(full.substring(lastLen)))
                        }
                        emitter.send(SseEmitter.event().name("done").data(""))
                        emitter.complete()
                        return
                    }
                    AnalysisStatus.FAILED, AnalysisStatus.CANCELLED -> {
                        emitter.send(SseEmitter.event().name("analysis-error").data(current.errorMessage ?: ""))
                        emitter.complete()
                        return
                    }
                    null -> {
                        emitter.send(SseEmitter.event().name("analysis-error").data("not found"))
                        emitter.complete()
                        return
                    }
                }
                changeSignal.poll(pollIntervalMs, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            runCatching { emitter.complete() }
        } catch (e: Exception) {
            if (e is IOException && DisconnectedClientHelper.isClientDisconnectedException(e)) {
                log.debug("[CodeQaSseStreamer] analysis stream {} closed by disconnected client", id)
                runCatching { emitter.complete() }
            } else {
                log.warn("[CodeQaSseStreamer] analysis stream {} failed: {}", id, e.message, e)
                runCatching { emitter.completeWithError(e) }
            }
        } finally {
            handle.invoke()
        }
    }
}

internal class FutureCanceller(
    private val future: Future<*>,
) : Runnable,
    Consumer<Throwable> {
    override fun run() {
        future.cancel(true)
    }

    override fun accept(t: Throwable) {
        future.cancel(true)
    }
}

internal class IntegrationTestSseTask(
    private val id: Long,
    private val emitter: SseEmitter,
    private val analysisService: CodeQaStreamingUseCase,
    private val pollIntervalMs: Long = 500L,
    private val waiter: SsePollWaiter = ThreadSsePollWaiter,
) : Runnable {
    private val log = LoggerFactory.getLogger(IntegrationTestSseTask::class.java)

    override fun run() {
        try {
            var lastLen = 0
            while (true) {
                val partial = analysisService.getIntegrationTestStreamingOutput(id)
                if (partial != null && partial.length > lastLen) {
                    emitter.send(SseEmitter.event().name("chunk").data(partial.substring(lastLen)))
                    lastLen = partial.length
                }
                val current = analysisService.getAnalysis(id)
                val itStatus = current?.integrationTestStatus
                when (itStatus) {
                    AnalysisStatus.COMPLETED, AnalysisStatus.FAILED, AnalysisStatus.CANCELLED -> {
                        val finalPartial = analysisService.getIntegrationTestStreamingOutput(id)
                        if (finalPartial != null && finalPartial.length > lastLen) {
                            runCatching {
                                emitter.send(SseEmitter.event().name("chunk").data(finalPartial.substring(lastLen)))
                            }
                        }
                        emitter.send(SseEmitter.event().name("test-done").data(itStatus))
                        emitter.complete()
                        return
                    }
                    null -> {
                        emitter.send(SseEmitter.event().name("test-error").data("not found"))
                        emitter.complete()
                        return
                    }
                }
                if (!waiter.await(pollIntervalMs)) {
                    Thread.interrupted()
                    runCatching { emitter.complete() }
                    return
                }
            }
        } catch (_: InterruptedException) {
            runCatching { emitter.complete() }
        } catch (e: Exception) {
            if (e is IOException && DisconnectedClientHelper.isClientDisconnectedException(e)) {
                log.debug("[CodeQaSseStreamer] integration test stream {} closed by disconnected client", id)
                runCatching { emitter.complete() }
            } else {
                log.warn("[CodeQaSseStreamer] integration test stream {} failed: {}", id, e.message, e)
                runCatching { emitter.completeWithError(e) }
            }
        }
    }
}

internal fun interface SsePollWaiter {
    fun await(millis: Long): Boolean
}

internal object ThreadSsePollWaiter : SsePollWaiter {
    override fun await(millis: Long): Boolean =
        try {
            Thread.sleep(millis)
            true
        } catch (_: InterruptedException) {
            Thread.interrupted()
            false
        }
}
