package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenAiCompatibleUrlTest {
    @Test
    fun `normalize keeps a complete url untouched`() {
        assertEquals(
            "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
            OpenAiCompatibleUrl.normalize("https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
        )
    }

    @Test
    fun `normalize strips trailing slashes and surrounding whitespace`() {
        assertEquals(
            "http://localhost:8000/v1",
            OpenAiCompatibleUrl.normalize("  http://localhost:8000/v1///  "),
        )
    }

    @Test
    fun `normalize assumes https when the scheme is missing`() {
        assertEquals(
            "https://api.groq.com/openai/v1",
            OpenAiCompatibleUrl.normalize("api.groq.com/openai/v1"),
        )
    }

    @Test
    fun `normalize keeps an explicit http scheme for local servers`() {
        assertEquals("http://127.0.0.1:1234/v1", OpenAiCompatibleUrl.normalize("http://127.0.0.1:1234/v1"))
    }

    @Test
    fun `normalize returns an empty string for null and blank input`() {
        assertEquals("", OpenAiCompatibleUrl.normalize(null))
        assertEquals("", OpenAiCompatibleUrl.normalize("   "))
    }

    @Test
    fun `models and chatCompletions append the api paths`() {
        assertEquals("https://x.test/v1/models", OpenAiCompatibleUrl.models("https://x.test/v1"))
        assertEquals(
            "https://x.test/v1/chat/completions",
            OpenAiCompatibleUrl.chatCompletions("https://x.test/v1"),
        )
    }
}
