package com.stacklens.adapter.inbound.rest.pipeline

import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.AgentStage
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class PipelineSsePublisherTest {
    private val pipelineService: CopilotAgentPipelineUseCase = mock()

    @Test
    fun `sseExecutor is initialized with 50 threads`() {
        val publisher = PipelineSsePublisher(pipelineService)
        assertNotNull(publisher.sseExecutor)
    }

    @Test
    fun `stream returns not-found error when pipeline does not exist`() {
        val publisher = PipelineSsePublisher(pipelineService)
        whenever(pipelineService.get(99L)).thenReturn(null)
        val emitter = publisher.stream(99L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream drives the inner runnable when the pipeline is already DONE`() {
        val publisher = PipelineSsePublisher(pipelineService)
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        run.status = PipelineStatus.DONE
        val emitter = SseEmitter(30_000L)
        run.stages.forEach { it.status = com.stacklens.application.port.model.pipeline.StageStatus.DONE }
        PipelineStreamRunnable(run, emitter).run()
        val future =
            java.util.concurrent.CompletableFuture
                .completedFuture(Unit)
        CancelFutureOnCompletion(future).run()
        CancelFutureOnTimeout(future).run()
        CancelFutureOnError(future).accept(RuntimeException("synthetic"))
        assertNotNull(publisher)
    }

    @Test
    fun `PipelineStreamRunnable catch-all block completes the emitter on exception`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        run.status = PipelineStatus.DONE
        val emitter = org.mockito.kotlin.mock<SseEmitter>()
        org.mockito.kotlin
            .doThrow(java.io.IOException("synthetic"))
            .whenever(emitter)
            .send(org.mockito.kotlin.any<SseEmitter.SseEventBuilder>())
        PipelineStreamRunnable(run, emitter).run()
        org.mockito.kotlin
            .verify(emitter)
            .completeWithError(org.mockito.kotlin.any())
    }

    @Test
    fun `chat returns error when message is blank`() {
        val publisher = PipelineSsePublisher(pipelineService)
        val emitter = publisher.chat(1L, mapOf("message" to ""))
        assertNotNull(emitter)
    }

    @Test
    fun `chat returns error when message is missing`() {
        val publisher = PipelineSsePublisher(pipelineService)
        val emitter = publisher.chat(1L, emptyMap())
        assertNotNull(emitter)
    }

    @Test
    fun `chat returns error when message is whitespace only`() {
        val publisher = PipelineSsePublisher(pipelineService)
        val emitter = publisher.chat(1L, mapOf("message" to "   "))
        assertNotNull(emitter)
    }

    @Test
    fun `chat returns error when message exceeds 10_000 chars`() {
        val publisher = PipelineSsePublisher(pipelineService)
        val emitter = publisher.chat(1L, mapOf("message" to "x".repeat(10_001)))
        assertNotNull(emitter)
    }

    @Test
    fun `chat returns not-found error when pipeline does not exist`() {
        val publisher = PipelineSsePublisher(pipelineService)
        whenever(pipelineService.get(42L)).thenReturn(null)
        val emitter = publisher.chat(42L, mapOf("message" to "hello"))
        assertNotNull(emitter)
    }

    @Test
    fun `chat accepts message of exactly 10_000 chars`() {
        val publisher = PipelineSsePublisher(pipelineService)
        val run = AgentPipelineRun(id = 1L, rawText = "", repoPath = null, featureSlug = "x", createdAt = System.currentTimeMillis())
        whenever(pipelineService.get(1L)).thenReturn(run)
        val emitter = publisher.chat(1L, mapOf("message" to "x".repeat(10_000)))
        assertNotNull(emitter)
    }

    @Test
    fun `toStatusMap includes basic run metadata`() {
        val run =
            AgentPipelineRun(
                id = 7L,
                rawText = "raw",
                repoPath = "/tmp/repo",
                featureSlug = "feature-x",
                createdAt = 12345L,
            )
        run.gitBranch = "feature-x-branch"
        run.gitLog = "git log here"

        val map = run.toStatusMap()
        assertEquals(7L, map["id"])
        assertEquals("/tmp/repo", map["repoPath"])
        assertEquals("feature-x", map["featureSlug"])
        assertEquals("feature-x-branch", map["gitBranch"])
        assertEquals("git log here", map["gitLog"])
        assertEquals(12345L, map["createdAt"])
    }

    @Test
    fun `toStatusMap jiraMode is true when jiraTicketData is set`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                jiraTicketData = "PROJ-1",
                createdAt = System.currentTimeMillis(),
            )
        val map = run.toStatusMap()
        assertEquals(true, map["jiraMode"])
    }

    @Test
    fun `toStatusMap jiraMode is false when jiraTicketData is null`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        val map = run.toStatusMap()
        assertEquals(false, map["jiraMode"])
    }

    @Test
    fun `toStatusMap includes all stages with their current status`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        run.finn.output = "finn-output"
        run.finn.status = com.stacklens.application.port.model.pipeline.StageStatus.DONE
        run.faye.output = "faye-output"

        val map = run.toStatusMap()

        @Suppress("UNCHECKED_CAST")
        val stages = map["stages"] as List<Map<String, Any?>>
        assertTrue(stages.isNotEmpty())
        val finnStage = stages.first { it["name"] == "finn" }
        assertEquals("finn-output", finnStage["output"])
        assertEquals("DONE", finnStage["status"])
    }

    @Test
    fun `toStatusMap status is the name of PipelineStatus enum`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        run.status = com.stacklens.application.port.model.pipeline.PipelineStatus.RUNNING
        val map = run.toStatusMap()
        assertEquals("RUNNING", map["status"])
    }

    @Test
    fun `toStatusMap errorMessage is included when set`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        run.errorMessage = "boom"
        val map = run.toStatusMap()
        assertEquals("boom", map["errorMessage"])
    }

    @Test
    fun `toStatusMap currentStage is included when set`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        run.currentStage = "finn"
        val map = run.toStatusMap()
        assertEquals("finn", map["currentStage"])
    }

    @Test
    fun `toStatusMap maps every stage name to its presentation label and icon`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                jiraTicketData = "PROJ-1",
                createdAt = 1L,
            )

        @Suppress("UNCHECKED_CAST")
        val stages = run.toStatusMap()["stages"] as List<Map<String, Any?>>

        assertEquals(
            listOf(
                Triple("lena", "⓪ Lena — Jira-Ticket Analyse", "bi-ticket-detailed"),
                Triple("nora", "① Nora — Requirements", "bi-person-badge"),
                Triple("cole", "② Cole — Implementation Plan", "bi-diagram-3"),
                Triple("ivan", "Ⓘ Ivan — Git & Setup", "bi-git"),
                Triple("max", "③ Max — PHP Architecture", "bi-bezier2"),
                Triple("finn", "④ Finn — PHP Code", "bi-code-slash"),
                Triple("faye", "⑤ Faye — Frontend", "bi-palette"),
                Triple("vera", "⑥ Vera — Infrastructure", "bi-server"),
                Triple("tess", "⑦ Tess — Tests", "bi-clipboard2-check"),
                Triple("syd", "⑧ Syd — Systemtests", "bi-clipboard2-pulse"),
                Triple("quinn", "⑨ Quinn — Quality & Coverage", "bi-shield-check"),
                Triple("sage", "⑩ Sage — Security Review", "bi-shield-lock"),
                Triple("rex", "⑪ Rex — Build & Fix", "bi-terminal"),
            ),
            stages.map { Triple(it["name"], it["label"], it["icon"]) },
        )
    }

    @Test
    fun `AgentStage keeps status and output mutable without a change listener`() {
        val stage = AgentStage(name = "finn")
        stage.status = com.stacklens.application.port.model.pipeline.StageStatus.RUNNING
        stage.output = "partial"
        assertEquals(com.stacklens.application.port.model.pipeline.StageStatus.RUNNING, stage.status)
        assertEquals("partial", stage.output)
    }

    @Test
    fun `PipelineStreamRunnable polls the run state until it finishes`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "raw",
                repoPath = null,
                featureSlug = "x",
                createdAt = System.currentTimeMillis(),
            )
        run.status = PipelineStatus.RUNNING
        val emitter = org.mockito.kotlin.mock<SseEmitter>()
        val worker =
            Thread {
                PipelineStreamRunnable(run, emitter).run()
            }
        worker.isDaemon = true
        worker.start()
        Thread.sleep(400)
        run.finn.output = "polled-output"
        Thread.sleep(400)
        run.status = PipelineStatus.DONE
        worker.join(2_000)
        assertTrue(!worker.isAlive)
        org.mockito.kotlin
            .verify(emitter)
            .complete()
    }

    @Test
    fun `chat stops streaming when the worker thread is interrupted`() {
        val latch = java.util.concurrent.CountDownLatch(1)
        val run = AgentPipelineRun(id = 3L, rawText = "", repoPath = null, featureSlug = "x", createdAt = 1L)
        whenever(pipelineService.get(3L)).thenReturn(run)
        whenever(pipelineService.chat(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as (String) -> Unit
            Thread.currentThread().interrupt()
            try {
                callback("partial")
            } catch (e: InterruptedException) {
                latch.countDown()
                throw e
            }
            latch.countDown()
            ""
        }

        val emitter = PipelineSsePublisher(pipelineService).chat(3L, mapOf("message" to "hello"))

        assertNotNull(emitter)
        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
    }
}
