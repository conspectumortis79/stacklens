package com.stacklens.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.inbound.chat.ChatAttachment
import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.KiChatEvent
import com.stacklens.application.port.inbound.chat.KiChatUsageUseCase
import com.stacklens.application.port.inbound.chat.KiChatUseCase
import com.stacklens.application.port.inbound.chat.KnowledgeSourceChip
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KiChatControllerChatThreadCoverageTest {
    private val kiChat = mock<KiChatUseCase>()
    private val emitter = mock<SseEmitter>()
    private val streamStarted = CountDownLatch(1)

    @Volatile
    private var worker: Thread? = null
    private val request = ChatRequest(tabId = "tab-1", userMessage = "how do I deploy?")

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

    private fun controller(objectMapper: ObjectMapper? = null) = KiChatController(kiChat, mock<I18nUseCase>(), mock<KiChatUsageUseCase>(), objectMapper)

    private fun streamEvents(
        events: List<KiChatEvent>,
        failure: Throwable? = null,
    ) {
        doAnswer { invocation ->
            worker = Thread.currentThread()
            streamStarted.countDown()
            val onEvent = invocation.getArgument<(KiChatEvent) -> Unit>(1)
            events.forEach { onEvent(it) }
            if (failure != null) throw failure
            null
        }.whenever(kiChat)
            .chatEvents(any<ChatRequest>(), any<(KiChatEvent) -> Unit>())
    }

    private fun drain() {
        assertTrue(streamStarted.await(20, TimeUnit.SECONDS), "chat stream never started")
        worker!!.join(20_000)
        assertTrue(worker!!.isAlive.not(), "chat worker thread did not finish")
    }

    @Test
    fun `chunk sources and done events are sent as separate server sent events`() {
        streamEvents(
            listOf(
                KiChatEvent.Chunk("hallo"),
                KiChatEvent.Sources(listOf(chip)),
                KiChatEvent.Done,
            ),
        )

        controller(ObjectMapper()).runChatThread(emitter, request)
        drain()

        verify(emitter, atLeastOnce()).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
    }

    @Test
    fun `a source event without an object mapper sends an empty array`() {
        streamEvents(listOf(KiChatEvent.Sources(listOf(chip))))

        controller().runChatThread(emitter, request)
        drain()

        verify(emitter).complete()
    }

    @Test
    fun `a source serialisation failure is reported as an empty list`() {
        streamEvents(listOf(KiChatEvent.Sources(listOf(chip))))

        controller(FailingObjectMapper()).runChatThread(emitter, request)
        drain()

        verify(emitter).complete()
    }

    @Test
    fun `a client that disappeared interrupts the stream and completes the emitter`() {
        doThrow(RuntimeException("broke pipe")).whenever(emitter).send(any<SseEmitter.SseEventBuilder>())
        streamEvents(listOf(KiChatEvent.Chunk("partial")))

        controller().runChatThread(emitter, request)
        drain()

        verify(kiChat).cancel("tab-1")
        verify(emitter).complete()
        verify(emitter).complete()
    }

    @Test
    fun `a conversation error is pushed as an error event`() {
        streamEvents(emptyList(), failure = RuntimeException("model exploded"))

        controller().runChatThread(emitter, request)
        drain()

        verify(emitter).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
    }

    @Test
    fun `an unsupported attachment mime type is rejected before any streaming starts`() {
        val response =
            controller().chat(
                request.copy(attachments = listOf(ChatAttachment("notes.txt", "text/plain", "aGVsbG8="))),
            )

        verify(kiChat, never()).chatEvents(any<ChatRequest>(), any<(KiChatEvent) -> Unit>())
        assertEquals(0L, response.timeout)
    }

    @Test
    fun `a supported attachment starts the chat stream`() {
        streamEvents(listOf(KiChatEvent.Done))

        val response =
            controller().chat(
                request.copy(attachments = listOf(ChatAttachment("shot.png", "IMAGE/PNG", "aGVsbG8="))),
            )
        drain()

        assertEquals(300_000L, response.timeout)
    }
}

private class FailingObjectMapper : ObjectMapper() {
    override fun writeValueAsString(value: Any?): String = throw IllegalStateException("no serializer")
}
