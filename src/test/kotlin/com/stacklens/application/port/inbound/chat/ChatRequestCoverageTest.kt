package com.stacklens.application.port.inbound.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatRequestCoverageTest {
    @Test
    fun `ChatTurn stores its arguments verbatim`() {
        val t = ChatTurn(role = "user", content = "hello")
        assertEquals("user", t.role)
        assertEquals("hello", t.content)
    }

    @Test
    fun `ChatRequest defaults are sensible`() {
        val req = ChatRequest()
        assertEquals("general", req.tabId)
        assertEquals("", req.tabLabel)
        assertEquals(0L, req.chatId)
        assertEquals("", req.userMessage)
        assertEquals("", req.tabContext)
        assertTrue(req.history.isEmpty())
        assertEquals("", req.systemHint)
    }

    @Test
    fun `ChatRequest stores every provided field verbatim`() {
        val history = listOf(ChatTurn("user", "a"), ChatTurn("assistant", "b"))
        val req =
            ChatRequest(
                tabId = "t-1",
                tabLabel = "My Tab",
                chatId = 42L,
                userMessage = "what is the answer?",
                tabContext = "some context",
                history = history,
                systemHint = "be brief",
            )
        assertEquals("t-1", req.tabId)
        assertEquals("My Tab", req.tabLabel)
        assertEquals(42L, req.chatId)
        assertEquals("what is the answer?", req.userMessage)
        assertEquals("some context", req.tabContext)
        assertEquals(history, req.history)
        assertEquals("be brief", req.systemHint)
    }
}
