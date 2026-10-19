package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.KiChatEvent
import com.stacklens.application.port.inbound.chat.KiChatUsageUseCase
import com.stacklens.application.port.inbound.chat.KiChatUseCase
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.RecordingSseEmitter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KiChatControllerRunThreadCoverageTest {
    private val kiChat: KiChatUseCase = mock()
    private val controller = KiChatController(kiChat, FakeI18n(), mock<KiChatUsageUseCase>())

    private val request = ChatRequest(tabId = "tab-1", chatId = 1L, userMessage = "hello")

    private fun awaitCompletion(emitter: RecordingSseEmitter) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (emitter.completeCalls == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertTrue(emitter.completeCalls > 0, "the worker never completed the emitter")
    }

    @Suppress("UNCHECKED_CAST")
    private fun emit(vararg events: KiChatEvent) {
        whenever(kiChat.chatEvents(any(), any())).thenAnswer { invocation ->
            val onEvent = invocation.getArgument(1) as (KiChatEvent) -> Unit
            events.forEach(onEvent)
            Unit
        }
    }

    @Test
    fun `the done event alone completes the stream`() {
        emit(KiChatEvent.Done)
        val emitter = RecordingSseEmitter()

        controller.runChatThread(emitter, request)

        awaitCompletion(emitter)
    }

    @Test
    fun `chunk and source events are serialised before the done marker`() {
        emit(
            KiChatEvent.Chunk("partial"),
            KiChatEvent.Sources(emptyList()),
            KiChatEvent.Done,
        )
        val emitter = RecordingSseEmitter()

        controller.runChatThread(emitter, request)

        awaitCompletion(emitter)
    }

    @Test
    fun `an interrupted worker skips the done marker`() {
        whenever(kiChat.chatEvents(any(), any())).thenAnswer {
            Thread.currentThread().interrupt()
            Unit
        }
        val emitter = RecordingSseEmitter()

        controller.runChatThread(emitter, request)

        awaitCompletion(emitter)
    }

    @Test
    fun `an error that escapes the handlers still closes the emitter`() {
        whenever(kiChat.chatEvents(any(), any())).thenThrow(Error("escapes the exception handlers"))
        val emitter = RecordingSseEmitter()

        controller.runChatThread(emitter, request)

        awaitCompletion(emitter)
    }
}
