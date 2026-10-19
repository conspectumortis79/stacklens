package com.stacklens.adapter.inbound.rest.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.TimeUnit

class PipelineStreamRunnableChangeSignalCoverageTest {
    @Test
    fun `a stage change is picked up by the poll loop`() {
        val run =
            AgentPipelineRun(
                id = 9L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "feat",
                createdAt = 1L,
            )
        val emitter = mock<SseEmitter>()
        val thread = Thread(PipelineStreamRunnable(run, emitter))
        thread.isDaemon = true
        thread.start()
        Thread.sleep(400)

        run.lena.status = StageStatus.RUNNING
        run.lena.output = "zwischenausgabe"
        Thread.sleep(400)
        run.status = PipelineStatus.DONE

        thread.join(TimeUnit.SECONDS.toMillis(20))
        assertTrue(!thread.isAlive, "the stream thread did not finish")
        verify(emitter, atLeastOnce()).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
    }
}
