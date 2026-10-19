package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.chat.KiChatUsageUseCase
import com.stacklens.application.port.inbound.chat.KiChatUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class KiChatControllerRunChatThreadBranchTest {
    private val kiChat: KiChatUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val chatUsage: KiChatUsageUseCase = mock()
    private val controller = KiChatController(kiChat, i18n, chatUsage)

    @Test
    fun `safeCompleteWithError swallows exceptions from emitter`() {
        val emitter = mock<SseEmitter>()
        doThrow(RuntimeException("complete failed"))
            .whenever(emitter)
            .completeWithError(any<Throwable>())
        controller.safeCompleteWithError(emitter, RuntimeException("orig"))
    }

    @Test
    fun `safeCompleteWithError does not throw when emitter succeeds`() {
        val emitter = mock<SseEmitter>()
        controller.safeCompleteWithError(emitter, RuntimeException("orig"))
    }

    @Test
    fun `safeCompleteWithError swallows exceptions from emitter complete`() {
        val emitter = mock<SseEmitter>()
        doThrow(RuntimeException("complete failed"))
            .whenever(emitter)
            .completeWithError(any<Throwable>())
        controller.safeCompleteWithError(emitter, RuntimeException("orig"))
    }

    @Test
    fun `jsonEncodeObject returns the serialised form when objectMapper is present`() {
        val mapper =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        val controller =
            KiChatController(
                kiChat = mock(),
                i18n = mock(),
                chatUsage = mock(),
                objectMapper = mapper,
            )
        val m = controller.javaClass.getDeclaredMethod("jsonEncodeObject", Any::class.java)
        m.isAccessible = true
        val result = m.invoke(controller, listOf("a", "b")) as String
        assertTrue(result.contains("a"))
    }

    @Test
    fun `jsonEncodeObject returns the fallback when objectMapper is null`() {
        val controller =
            KiChatController(
                kiChat = mock(),
                i18n = mock(),
                chatUsage = mock(),
                objectMapper = null,
            )
        val m = controller.javaClass.getDeclaredMethod("jsonEncodeObject", Any::class.java)
        m.isAccessible = true
        val result = m.invoke(controller, listOf("a", "b")) as String
        assertEquals("[]", result)
    }
}
