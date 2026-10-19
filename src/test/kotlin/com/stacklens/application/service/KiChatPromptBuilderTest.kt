package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.out.platform.AppKnowledgePort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.domain.model.knowledge.RetrievedKnowledge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KiChatPromptBuilderTest {
    private val promptLoader = CapturingPromptLoader()
    private val appKnowledge = FakeAppKnowledge()
    private val sut = KiChatPromptBuilder(promptLoader, appKnowledge)

    @Test
    fun `build returns the user message last`() {
        val prompt = sut.build(request(userMessage = "hello world"), "de", "", emptyList())
        assertThat(prompt).contains("hello world")
        assertThat(prompt.indexOf("hello world")).isGreaterThan(prompt.indexOf("## User Message"))
    }

    @Test
    fun `build produces a non-empty prompt for the german language`() {
        val prompt = sut.build(request(), "de", "", emptyList())
        assertThat(prompt).isNotBlank()
        assertThat(promptLoader.callCount).isGreaterThan(0)
    }

    @Test
    fun `build produces a non-empty prompt for the english language`() {
        val prompt = sut.build(request(), "en", "", emptyList())
        assertThat(prompt).isNotBlank()
        assertThat(promptLoader.callCount).isGreaterThan(0)
    }

    @Test
    fun `build does not touch appKnowledge readme when no app mention is in the user message`() {
        sut.build(request(userMessage = "regular question"), "de", "", emptyList())
        assertThat(appKnowledge.readmeCalled).isFalse()
    }

    @Test
    fun `build renders retrieved-knowledge chips and citation rows`() {
        val retrieved =
            listOf(
                RetrievedKnowledge(
                    chunkId = 1L,
                    documentId = 10L,
                    knowledgeBaseId = 100L,
                    title = "Demo",
                    sourceType = KnowledgeSourceType.MANUAL,
                    sourceUri = "file://demo",
                    content = "Long content body",
                    score = 0.87,
                    citation = "KB:document-10#chunk-1",
                ),
            )

        val prompt = sut.build(request(), "de", "", retrieved)

        assertThat(prompt).contains("Retrieved StackLens knowledge (RAG)")
        assertThat(prompt).contains("KB:document-10#chunk-1")
        assertThat(prompt).contains("Title: Demo")
    }

    private fun request(userMessage: String = "hi"): ChatRequest = ChatRequest(tabId = "t", userMessage = userMessage)
}

private class CapturingPromptLoader : PromptReaderPort {
    var callCount: Int = 0

    override fun get(
        lang: String,
        key: String,
        vars: Map<String, String>,
    ): String {
        callCount += 1
        return ""
    }

    override fun context(lang: String) = error("not used")
}

private class FakeAppKnowledge : AppKnowledgePort {
    var readmeCalled: Boolean = false

    override fun index(): String = ""

    override fun relevant(
        query: String,
        maxFiles: Int,
        perFileChars: Int,
    ): String = ""

    override fun readme(): String? {
        readmeCalled = true
        return null
    }
}
