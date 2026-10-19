package com.stacklens.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.KiChatEvent
import com.stacklens.application.port.inbound.chat.KiChatUsageUseCase
import com.stacklens.application.port.inbound.chat.KiChatUseCase
import com.stacklens.application.port.inbound.chat.KnowledgeSourceChip
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KiChatControllerEventShapeCoverageTest {
    private val kiChat = mock<KiChatUseCase>()
    private val controller = KiChatController(kiChat, mock<I18nUseCase>(), mock<KiChatUsageUseCase>(), ObjectMapper())
    private val request = ChatRequest(tabId = "tab-1", userMessage = "how do I deploy?")

    private val streamStarted = CountDownLatch(1)

    @Volatile
    private var worker: Thread? = null

    private val chip =
        KnowledgeSourceChip(
            citation = "KB:2#1",
            title = "Runbook",
            sourceType = "CONFLUENCE",
            sourceUri = "https://wiki.example/runbook",
            score = 0.8,
            documentId = 2L,
            chunkId = 1L,
            preview = "apply the manifest",
        )

    private fun streamEvents(
        events: List<KiChatEvent>,
        failure: Throwable? = null,
    ) {
        doAnswer { invocation ->
            worker = Thread.currentThread()
            streamStarted.countDown()
            val onEvent = invocation.getArgument<(KiChatEvent) -> Unit>(1)
            events.forEach { onEvent(it) }
            failure?.let { throw it }
            null
        }.whenever(kiChat)
            .chatEvents(any<ChatRequest>(), any<(KiChatEvent) -> Unit>())
    }

    private fun drain() {
        assertTrue(streamStarted.await(20, TimeUnit.SECONDS), "the chat stream never started")
        worker?.join(20_000)
    }

    @Test
    fun `chunk, sources and done events are all forwarded`() {
        val emitter = mock<SseEmitter>()
        streamEvents(listOf(KiChatEvent.Chunk("hallo"), KiChatEvent.Sources(listOf(chip)), KiChatEvent.Done))

        controller.runChatThread(emitter, request)
        drain()

        verify(emitter, atLeastOnce()).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
    }

    @Test
    fun `a worker that dies with an error still completes the stream`() {
        val emitter = mock<SseEmitter>()
        streamEvents(emptyList(), failure = AssertionError("the model exploded"))

        controller.runChatThread(emitter, request)
        drain()

        verify(emitter).complete()
        verify(emitter, never()).completeWithError(any())
    }

    @Test
    fun `a failure without a message falls back to the translated placeholder`() {
        val emitter = mock<SseEmitter>()
        streamEvents(emptyList(), failure = IllegalStateException())

        controller.runChatThread(emitter, request)
        drain()

        verify(emitter).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
    }

    @Test
    fun `a broken client connection cancels the run`() {
        val emitter = mock<SseEmitter>()
        doAnswer { throw IllegalStateException("client is gone") }
            .whenever(emitter)
            .send(any<SseEmitter.SseEventBuilder>())
        streamEvents(listOf(KiChatEvent.Chunk("first")))

        controller.runChatThread(emitter, request)
        drain()

        verify(kiChat).cancel("tab-1")
        verify(emitter).complete()
    }

    @Test
    fun `an ordinary failure is reported to the browser before the stream closes`() {
        val emitter = mock<SseEmitter>()
        streamEvents(emptyList(), failure = IllegalStateException("model is gone"))

        controller.runChatThread(emitter, request)
        drain()

        verify(emitter).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
    }
}
