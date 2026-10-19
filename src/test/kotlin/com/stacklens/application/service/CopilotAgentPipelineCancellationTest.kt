package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CopilotAgentPipelineCancellationTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `cancel does nothing for unknown id`() {
        val svc = makeService()
        svc.cancel(99L)
    }

    @Test
    fun `cancel sets cancelled flag for running run`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 7L, rawText = "r", repoPath = null, featureSlug = "r", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.RUNNING
        svc.registry.put(7L, run)
        svc.cancel(7L)
        assertTrue(run.cancelled)
    }

    @Test
    fun `cancel sets cancelled flag for pending run`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 71L, rawText = "r", repoPath = null, featureSlug = "r", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.PENDING
        svc.registry.put(71L, run)
        svc.cancel(71L)
        assertTrue(run.cancelled)
    }

    @Test
    fun `cancel does not set cancelled for done run`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 8L, rawText = "d", repoPath = null, featureSlug = "d", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.DONE
        svc.registry.put(8L, run)
        svc.cancel(8L)
        assertFalse(run.cancelled)
    }

    @Test
    fun `cancel does not set cancelled for error run`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 81L, rawText = "d", repoPath = null, featureSlug = "d", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.ERROR
        svc.registry.put(81L, run)
        svc.cancel(81L)
        assertFalse(run.cancelled)
    }

    @Test
    fun `cancel after Nora stops pipeline before Ivan`() {
        val run = runCancelAtCall(2) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals("Pipeline abgebrochen.", run.errorMessage)
        assertEquals(StageStatus.DONE, run.nora.status)

        assertEquals(StageStatus.PENDING, run.ivan.status)
        assertEquals(StageStatus.PENDING, run.cole.status)
    }

    @Test
    fun `cancel after Cole stops pipeline before Max`() {
        val run = runCancelAtCall(3) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.nora.status)
        assertEquals(StageStatus.DONE, run.ivan.status)
        assertEquals(StageStatus.DONE, run.cole.status)
        assertEquals(StageStatus.PENDING, run.max.status)
    }

    @Test
    fun `cancel after Max stops pipeline before Finn`() {
        val run = runCancelAtCall(4) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.max.status)
        assertEquals(StageStatus.PENDING, run.finn.status)
    }

    @Test
    fun `cancel after Finn stops pipeline before Faye`() {
        val run = runCancelAtCall(5) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.finn.status)
        assertEquals(StageStatus.PENDING, run.faye.status)
    }

    @Test
    fun `cancel after Faye stops pipeline before Vera`() {
        val run = runCancelAtCall(6) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.faye.status)
        assertEquals(StageStatus.PENDING, run.vera.status)
    }

    @Test
    fun `cancel after Tess stops pipeline before Syd`() {
        val run = runCancelAtCall(8) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.tess.status)
        assertEquals(StageStatus.PENDING, run.syd.status)
    }

    @Test
    fun `cancel after Quinn stops pipeline before Sage`() {
        val run = runCancelAtCall(10) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.quinn.status)
        assertEquals(StageStatus.PENDING, run.sage.status)
    }

    @Test
    fun `cancel after Sage stops pipeline - Rex stays PENDING`() {
        val run = runCancelAtCall(11) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.sage.status)
        assertEquals(StageStatus.PENDING, run.rex.status)
    }

    @Test
    fun `cancel after Vera stops pipeline before Tess`() {
        val run = runCancelAtCall(7) { svc -> svc.start("Feature", null) }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.vera.status)

        assertEquals(StageStatus.PENDING, run.tess.status)
    }

    @Test
    fun `cancel in jira-mode after Lena stops pipeline`() {
        val run =
            runCancelAtCall(1) { svc ->
                svc.startFromJira(
                    ticketData = "PROJ-5\nSummary: Feature",
                    ticketKey = "PROJ-5",
                    localRepoConfigId = null,
                )
            }
        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals("Pipeline abgebrochen.", run.errorMessage)
        assertEquals(StageStatus.DONE, run.lena.status)
        assertEquals(StageStatus.PENDING, run.nora.status)
    }

    @Test
    fun `cancel inside Faye-to-Finn loopback after Finn fix-pass`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            val n = callCount.incrementAndGet()
            when (n) {
                6 -> "🔄 HANDOFF_TO_FINN\nhandoff content"
                7 -> {
                    reached.countDown()
                    release.await(3, TimeUnit.SECONDS)
                    "finn-fix-output"
                }
                else -> "ok"
            }
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        reached.await(3, TimeUnit.SECONDS)
        svc.get(id)!!.cancelled = true
        release.countDown()

        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals("Pipeline abgebrochen.", run.errorMessage)

        assertEquals(StageStatus.DONE, run.finn.status)
        assertTrue(run.finn.output.contains("finn-fix-output"))

        assertEquals(StageStatus.DONE, run.faye.status)

        assertEquals(StageStatus.PENDING, run.tess.status)
    }

    @Test
    fun `cancel inside Tess-to-Finn loopback after Finn fix-pass`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            val n = callCount.incrementAndGet()
            when (n) {
                8 -> "🔄 HANDOFF_TO_FINN\nhandoff"
                9 -> {
                    reached.countDown()
                    release.await(3, TimeUnit.SECONDS)
                    "finn-fix-output"
                }
                else -> "ok"
            }
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        reached.await(3, TimeUnit.SECONDS)
        svc.get(id)!!.cancelled = true
        release.countDown()

        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)

        assertEquals(StageStatus.DONE, run.finn.status)

        assertTrue(run.tess.output.contains("HANDOFF_TO_FINN"))

        assertEquals(StageStatus.PENDING, run.quinn.status)
    }

    @Test
    fun `cancel inside Quinn-Tess-Finn loopback after Finn fix-pass`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->
            val n = callCount.incrementAndGet()
            when (n) {
                10 -> "🔄 HANDOFF_TO_TESS\nquinn handoff"
                11 -> "🔄 HANDOFF_TO_FINN\ntess-quinn-redux handoff"
                12 -> {
                    reached.countDown()
                    release.await(3, TimeUnit.SECONDS)
                    "finn-fix-output"
                }
                else -> "ok"
            }
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.start("Feature", null)
        reached.await(3, TimeUnit.SECONDS)
        svc.get(id)!!.cancelled = true
        release.countDown()

        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)

        assertTrue(run.finn.output.contains("finn-fix-output"))

        assertTrue(run.tess.output.contains("HANDOFF_TO_FINN"))

        assertEquals(StageStatus.PENDING, run.sage.status)
    }

    @Test
    fun `cancel after Faye with mockup - mockup fields remain set in cancelled run`() {
        val b64 =
            java.util.Base64
                .getEncoder()
                .encodeToString("data".toByteArray())
        val run =
            runCancelAtCall(6) { svc ->
                svc.start("Feature", localRepoConfigId = null, mockupBase64 = b64, mockupMimeType = "image/png")
            }

        assertEquals(b64, run.mockupBase64)
        assertEquals("image/png", run.mockupMimeType)
        assertEquals(PipelineStatus.ERROR, run.status)
    }

    @Test
    fun `cancel after Nora in jira-mode - Lena done, Nora done, rest PENDING`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val callCount = AtomicInteger(0)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { _ ->

            if (callCount.incrementAndGet() == 2) {
                reached.countDown()
                release.await(3, TimeUnit.SECONDS)
            }
            "ok"
        }

        val svc = makeService(dataDir = "build/tests/data-test-${System.currentTimeMillis()}")
        val id = svc.startFromJira(ticketData = "JIRA-1\nSummary: test", ticketKey = "JIRA-1", localRepoConfigId = null)
        reached.await(3, TimeUnit.SECONDS)
        svc.get(id)!!.cancelled = true
        release.countDown()
        val run = awaitRunDone(svc, id)

        assertEquals(PipelineStatus.ERROR, run.status)
        assertEquals(StageStatus.DONE, run.lena.status)
        assertEquals(StageStatus.DONE, run.nora.status)
        assertEquals(StageStatus.PENDING, run.cole.status)
    }
}
